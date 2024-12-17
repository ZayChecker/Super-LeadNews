package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.apis.article.IArticleClient;
import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.common.SensitiveWordUtil;
import com.heima.wemedia.algorithm.ACAutomaton;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {

    @Autowired
    private WmNewsMapper wmNewsMapper;

    @Autowired
    private GreenTextScan greenTextScan;

    @Autowired
    private GreenImageScan greenImageScan;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private IArticleClient articleClient;

    @Autowired
    private WmChannelMapper wmChannelMapper;

    @Autowired
    private WmUserMapper wmUserMapper;

    @Autowired
    private WmSensitiveMapper wmSensitiveMapper;

    //自媒体文章审核
    @Override
    @Async     //表明当前方法是一个异步方法
    public void autoScanWmNews(Integer id) {
        //1.查询自媒体文章
        WmNews wmNews = wmNewsMapper.selectById(id);
        if(wmNews == null){
            throw new RuntimeException("WmNewsAutoScanServiceImpl-文章不存在");
        }
        if(wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())){
            //从内容中提取文本内容和图片(内容和封面的)
            Map<String, Object> textAndImages = handleTextAndImages(wmNews);

            //自管理的敏感词过滤
            boolean isSensitive = handleSensitiveScan((String) textAndImages.get("content"), wmNews);
            if(!isSensitive) return;

            //2.审核文本内容 阿里云接口
//            boolean isTextScan = handleTextScan((String) textAndImages.get("content"), wmNews);
//            if(!isTextScan) return;
            //3.审核图片 阿里云接口
//            boolean isImageScan = handleImageScan((List<String>) textAndImages.get("images"), wmNews);
//            if(!isImageScan) return;

            //4.审核成功，保存app端的相关的文章数据
            ResponseResult responseResult = saveAppArticle(wmNews);
            if(!responseResult.getCode().equals(200)){
                throw new RuntimeException("WmNewsAutoScanServiceImpl-文章审核, 保存app端相关文章数据失败");
            }
            //回填article_id
            wmNews.setArticleId((String) responseResult.getData());
            wmNews.setStatus((short)9);
            wmNews.setReason("审核成功");
            wmNewsMapper.updateById(wmNews);
        }
    }

    private boolean handleSensitiveScan(String content, WmNews wmNews){
//        //获取所有的敏感词
//        List<WmSensitive> wmSensitives = wmSensitiveMapper.selectList(Wrappers.lambdaQuery(WmSensitive.class));
//        List<String> sensitiveList = wmSensitives.stream().map(WmSensitive::getSensitives).collect(Collectors.toList());
//        //初始化敏感词库
//        SensitiveWordUtil.initMap(sensitiveList);
//        //查看文章中是否包含敏感词
//        Map<String, Integer> map = SensitiveWordUtil.matchWords(content);
//        if(map.size() > 0){
//            wmNews.setStatus((short)2);
//            wmNews.setReason("当前文章中存在违规内容" + map);
//            wmNewsMapper.updateById(wmNews);
//            return false;
//        }
//        return true;

        ACAutomaton.ACNode root = ACAutomaton.getRoot();
        Boolean result = ACAutomaton.query(root, content);
        if(result){
            wmNews.setStatus((short)2);
            wmNews.setReason("当前文章中存在违规内容");
            wmNewsMapper.updateById(wmNews);
            return false;
        }
        return true;
    }

    private Map<String, Object> handleTextAndImages(WmNews wmNews) {
        //存储纯文本内容
        StringBuilder stringBuilder = new StringBuilder();
        //存储图片
        List<String> images = new ArrayList<>();

        if(StringUtils.isNotBlank(wmNews.getContent())){
            List<Map> maps = JSONArray.parseArray(wmNews.getContent(), Map.class);
            for (Map map : maps) {
                if(map.get("type").equals("text")){
                    stringBuilder.append(map.get("value"));
                }
                if(map.get("type").equals("image")){
                    images.add((String) map.get("value"));
                }
            }
        }

        //封面图片是以逗号分开的字符串
        if(StringUtils.isNotBlank(wmNews.getImages())){
            String[] split = wmNews.getImages().split(",");
            images.addAll(Arrays.asList(split));
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("content", stringBuilder.toString());
        resultMap.put("images", images);
        return resultMap;
    }

    //保存app端相关的文章数据
    private ResponseResult saveAppArticle(WmNews wmNews){
        ArticleDto articleDto = new ArticleDto();
        BeanUtils.copyProperties(wmNews, articleDto);
        //文章的布局
        articleDto.setLayout(wmNews.getType());
        //频道名字
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        if(wmChannel != null){
            articleDto.setChannelName(wmChannel.getName());
        }
        //作者
        articleDto.setAuthorId(wmNews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        if(wmUser != null){
            articleDto.setAuthorName(wmUser.getName());
        }
        //设置文章id
        if(wmNews.getArticleId() != null){
            articleDto.setId(wmNews.getArticleId());
        }
        articleDto.setCreatedTime(new Date());
        ResponseResult responseResult = articleClient.saveArticle(articleDto);
        return responseResult;
    }

    //审核纯文本内容
    private boolean handleTextScan(String content, WmNews wmNews){
        boolean flag = true;

        if((wmNews.getTitle() + "-" + content).length() == 1){   //标题也要审核
            return true;
        }

        try {
            Map map = greenTextScan.greeTextScan(wmNews.getTitle() + "-" + content);
            if(map != null){
                //审核失败
                if(map.get("suggestion").equals("block")){
                    flag = false;
                    wmNews.setStatus((short)2);
                    wmNews.setReason("当前文章中存在违规内容");
                    wmNewsMapper.updateById(wmNews);
                }
                //不确定信息, 需要人工审核
                if(map.get("suggestion").equals("review")){
                    flag = false;
                    wmNews.setStatus((short)3);
                    wmNews.setReason("当前文章中存在不确定内容");
                    wmNewsMapper.updateById(wmNews);
                }
            }
        } catch (Exception e) {
            flag = false;
            throw new RuntimeException(e);
        }
        return flag;
    }

    //审核图片
    private boolean handleImageScan(List<String> images, WmNews wmNews){
        boolean flag = true;

        if(images == null || images.size() == 0){
            return true;
        }

        //下载图片 minIO
        //图片去重
        images = images.stream().distinct().collect(Collectors.toList());
        List<byte[]> imageList = new ArrayList<>();
        for (String image : images) {
            byte[] bytes = fileStorageService.downLoadFile(image);
            imageList.add(bytes);
        }

        try {
            Map map = greenImageScan.imageScan(imageList);
            if(map != null){
                //审核失败
                if(map.get("suggestion").equals("block")){
                    flag = false;
                    wmNews.setStatus((short)2);
                    wmNews.setReason("当前图片中存在违规内容");
                    wmNewsMapper.updateById(wmNews);
                }
                //不确定信息, 需要人工审核
                if(map.get("suggestion").equals("review")){
                    flag = false;
                    wmNews.setStatus((short)3);
                    wmNews.setReason("当前图片中存在不确定内容");
                    wmNewsMapper.updateById(wmNews);
                }
            }
        } catch (Exception e) {
            flag = false;
            throw new RuntimeException(e);
        }
        return flag;
    }
}
