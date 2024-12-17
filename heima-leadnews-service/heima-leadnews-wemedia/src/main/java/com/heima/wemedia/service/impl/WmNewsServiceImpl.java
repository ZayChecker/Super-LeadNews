package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;

    @Autowired
    private WmNewsTaskService wmNewsTaskService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    //条件查询文章列表
    @Override
    public ResponseResult findAll(WmNewsPageReqDto wmNewsPageReqDto) {
        //1.分页检查
        wmNewsPageReqDto.checkParam();
        //2.分页条件查询
        IPage page = new Page(wmNewsPageReqDto.getPage(), wmNewsPageReqDto.getSize());
        LambdaQueryWrapper<WmNews> queryWrapper = Wrappers.lambdaQuery(WmNews.class);
        //状态精准查询
        if(wmNewsPageReqDto.getStatus() != null){
            queryWrapper.eq(WmNews::getStatus, wmNewsPageReqDto.getStatus());
        }
        //频道精准查询
        if(wmNewsPageReqDto.getChannelId() != null){
            queryWrapper.eq(WmNews::getChannelId, wmNewsPageReqDto.getChannelId());
        }
        //时间范围查询
        if(wmNewsPageReqDto.getBeginPubDate() != null && wmNewsPageReqDto.getEndPubDate() != null){
            queryWrapper.between(WmNews::getPublishTime, wmNewsPageReqDto.getBeginPubDate(), wmNewsPageReqDto.getEndPubDate());
        }
        //关键字模糊查询
        if(StringUtils.isNoneBlank(wmNewsPageReqDto.getKeyword())){
            queryWrapper.like(WmNews::getTitle, wmNewsPageReqDto.getKeyword());
        }
        //查询当前登陆人的文章
        queryWrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());
        //发布时间倒序查询
        queryWrapper.orderByDesc(WmNews::getCreatedTime);

        page = page(page, queryWrapper);
        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(wmNewsPageReqDto.getPage(), wmNewsPageReqDto.getSize(), (int)page.getTotal());
        responseResult.setData(page.getRecords());
        return responseResult;
    }

    //发布/修改文章或保存为草稿
    @Override
    @Transactional
    public ResponseResult submitNews(WmNewsDto wmNewsDto) {
        //0.条件判断
        if(wmNewsDto == null || wmNewsDto.getContent() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //1.保存或修改文章
        WmNews wmNews = new WmNews();
        //属性拷贝，属性名称和类型相同时才能拷贝
        BeanUtils.copyProperties(wmNewsDto, wmNews);
        //有部分属性无法拷贝
        //封面图片 list->string
        if(wmNewsDto.getImages() != null && wmNewsDto.getImages().size() > 0){
            String imageStr = StringUtils.join(wmNewsDto.getImages(), ",");
            wmNews.setImages(imageStr);
        }
        if(wmNewsDto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }
        saveOrUpdateWmNews(wmNews);

        //2.判断是否为草稿, 如果为草稿则结束当前方法
        if(wmNewsDto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }

        //3.不是草稿，保存文章内容图片与素材的关系
        List<String> materials = ectractUrlInfo(wmNewsDto.getContent());
        saveRelativeInfoContent(materials, wmNews.getId());

        //4.不是草稿，保存文章封面图片与素材的关系
        saveRelativeInfoForCover(wmNewsDto, wmNews, materials);

        //审核文章
        //wmNewsAutoScanService.autoScanWmNews(wmNews.getId());   //审核就算报错也不会影响文章的发布, 因为是异步
        wmNewsTaskService.addNewsToTask(wmNews.getId(), wmNews.getPublishTime());


        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);  //有可能先返回结果了, 审核还没结束
    }


    //保存或修改文章
    private void saveOrUpdateWmNews(WmNews wmNews){
        //补全属性
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short)1);

        if(wmNews.getId() == null){
            //id为空则保存
            save(wmNews);
        }
        else{
            //否则就是修改
            //删除文章图片与素材的关系
            wmNewsMaterialMapper.delete(Wrappers.lambdaQuery(WmNewsMaterial.class)
                    .eq(WmNewsMaterial::getNewsId, wmNews.getId()));
            updateById(wmNews);
        }
    }

    //提取文章内容中的图片信息
    private List<String> ectractUrlInfo(String content) {
        List<String> materials = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if(map.get("type").equals("image")){
                String imgUrl = (String)map.get("value");
                materials.add(imgUrl);
            }
        }
        return materials;
    }

    //处理文章内容图片与素材的关系
    private void saveRelativeInfoContent(List<String> materials, Integer newsId) {
        saveRelationInfo(materials, newsId, WemediaConstants.WM_CONTENT_REFERENCE);
    }

    //保存文章图片与素材的关系到数据库中
    private void saveRelationInfo(List<String> materials, Integer newsId, Short type) {
        if(materials != null && !materials.isEmpty()){
            //通过图片的url查询素材的id
            List<WmMaterial> dbMaterials = wmMaterialMapper.selectList(Wrappers.lambdaQuery(WmMaterial.class).in(WmMaterial::getUrl, materials));
            //判断素材是否有效
            if(dbMaterials == null || dbMaterials.size() == 0 || dbMaterials.size() != materials.size()){
                throw new CustomException(AppHttpCodeEnum.MATERIAL_REFERENCE_FAIL);
            }
            List<Integer> idList = dbMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());
            //批量保存
            wmNewsMaterialMapper.saveRelations(idList, newsId, type);
        }
    }

    /**
     * 第一个功能：如果封面类型为自动，则设置封面类型的数据
     * 匹配规则：
     * 1.如果内容图片大于等于1，小于3   单图，type：1
     * 2.如果内容图片大于等于3         多图，type：3
     * 3.如果内容没有图片             无图，type：0
     * 第二个功能：保存封面图片与素材的关系
     * @param wmNewsDto
     * @param wmNews
     * @param materials
     */
    private void saveRelativeInfoForCover(WmNewsDto wmNewsDto, WmNews wmNews, List<String> materials) {
        List<String> images = wmNewsDto.getImages();
        //如果封面类型为自动，则设置封面类型的数据
        if(wmNewsDto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            if(materials.size() >= 3){    //多图
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            }
            else if(materials.size() >= 1 && materials.size() < 3){   //单图
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materials.stream().limit(1).collect(Collectors.toList());
            }
            else{   //无图
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }
            //修改实体对应的images
            if(images != null && images.size() > 0){
                wmNews.setImages(StringUtils.join(images, ","));
            }
            updateById(wmNews);
        }
        if(images != null && images.size() > 0){
            saveRelationInfo(images, wmNews.getId(), WemediaConstants.WM_COVER_REFERENCE);
        }
    }

    //文章的上下架
    @Override
    public ResponseResult downOrUp(WmNewsDto wmNewsDto) {
        //1.检查参数
        if(wmNewsDto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.查询文章
        WmNews wmNews = getById(wmNewsDto.getId());
        if(wmNews == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "文章不存在");
        }
        //3.判断文章是否已发布
        if(!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "当前文章不是发布状态, 不能上下架");
        }
        //4.修改文章enable
        if(wmNewsDto.getEnable() != null && wmNewsDto.getEnable() > -1 && wmNewsDto.getEnable() < 2){
            lambdaUpdate()
                .set(WmNews::getEnable, wmNewsDto.getEnable())
                .eq(WmNews::getId, wmNews.getId())
                .update();

            if(wmNews.getArticleId() != null){
                //发送消息, 通知article修改文章的配置
                Map<String, Object> map = new HashMap<>();
                map.put("articleId", wmNews.getArticleId());
                map.put("enable", wmNewsDto.getEnable());
                kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));
            }
        }
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
