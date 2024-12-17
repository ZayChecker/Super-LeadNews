package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleFreemarkerService;
import com.heima.article.service.ApArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {

    private static final short MAX_PAGE_SIZE = 50;

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private ApArticleFreemarkerService apArticleFreemarkerService;

    @Autowired
    private CacheService cacheService;

    //根据参数加载文章列表   1-加载更多 2-加载最新
    @Override
    public ResponseResult load(ArticleHomeDto articleHomeDto, Short type) {
        //参数校验
        //分页条数的校验
        Integer size = articleHomeDto.getSize();
//        if(size == null || size == 0){
//            size = 10;
//        }
        size = 5;
        size = Math.min(size, MAX_PAGE_SIZE);       //分页的值不超过50
        articleHomeDto.setSize(size);

        //类型参数校验
        if(!type.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !type.equals(ArticleConstants.LOADTYPE_LOAD_NEW)){
            type = ArticleConstants.LOADTYPE_LOAD_MORE;
        }

        //文章频道校验
        if(StringUtils.isBlank(articleHomeDto.getTag())){
            articleHomeDto.setTag(ArticleConstants.DEFAULT_TAG);
        }

        //时间校验
        if(articleHomeDto.getMinBehotTime() == null) articleHomeDto.setMinBehotTime(new Date());
        if(articleHomeDto.getMaxBehotTime() == null) articleHomeDto.setMaxBehotTime(new Date());

        //取出用户看过的
        ApUser user = AppThreadLocalUtil.getUser();
        Set<String> readArticleIds = new TreeSet<>();
        if(cacheService.exists(ArticleConstants.ARTICLE_RESTRICT_TOPIC + ":" + user.getId().toString() + ":" + "0")){
            readArticleIds = cacheService.setMembers(ArticleConstants.ARTICLE_RESTRICT_TOPIC + ":" + user.getId().toString() + ":" + "0");
        }
        if(cacheService.exists(ArticleConstants.ARTICLE_RESTRICT_TOPIC + ":" + user.getId().toString() + ":" + "1")){
            readArticleIds.addAll(cacheService.setMembers(ArticleConstants.ARTICLE_RESTRICT_TOPIC + ":" + user.getId().toString() + ":" + "1"));
        }
        if(cacheService.exists(ArticleConstants.ARTICLE_RESTRICT_TOPIC + ":" + user.getId().toString() + ":" + "2")){
            readArticleIds.addAll(cacheService.setMembers(ArticleConstants.ARTICLE_RESTRICT_TOPIC + ":" + user.getId().toString() + ":" + "2"));
        }
        Set<String> allArticleIds = cacheService.setMembers(ArticleConstants.ARTICLE_ALL_ID_TOPIC);
        allArticleIds.removeAll(readArticleIds);

        List<ApArticle> apArticleList = apArticleMapper.loadArticleList(articleHomeDto, type, allArticleIds);

        //如果已经查完了，就把已经看过的输出
        if(apArticleList == null || apArticleList.size() == 0){
            articleHomeDto.setSize(0);
            type = 3;
            apArticleList = apArticleMapper.loadArticleList(articleHomeDto, type, readArticleIds);
        }

        return ResponseResult.okResult(apArticleList);
    }

    //从缓存中加载文章列表
    //type: 1-加载更多 2-加载最新
    @Override
    public ResponseResult load2(ArticleHomeDto articleHomeDto, Short type, boolean firstPage) {
        if(firstPage){
            String jsonStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + articleHomeDto.getTag());
            if(StringUtils.isNotBlank(jsonStr)){
                List<HotArticleVo> hotArticleVoList = JSON.parseArray(jsonStr, HotArticleVo.class);
                return ResponseResult.okResult(hotArticleVoList);
            }
        }
        return load(articleHomeDto, type);
    }

    //保存app端相关文章
    @Override
    public ResponseResult saveArticle(ArticleDto articleDto) {
        //1.检查参数
        if(articleDto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(articleDto, apArticle);

        //2.判断是否存在id
        if(articleDto.getId() == null){
            //保存文章
            save(apArticle);
            //保存配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);
            //保存文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(articleDto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        }
        else{
            //修改文章
            updateById(apArticle);
            //修改文章内容
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.lambdaQuery(ApArticleContent.class)
                    .eq(ApArticleContent::getArticleId, apArticle.getId()));
            apArticleContent.setContent(articleDto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }

        //异步调用, 生成静态文件上传到minio中
        apArticleFreemarkerService.buildArticleToMinIO(apArticle, articleDto.getContent());

        //3.结果返回, 文章的id
        return ResponseResult.okResult(apArticle.getId());
    }

    //更新文章的分值 同时更新缓存中的热点文章数据
    @Override
    public void updateScore(ArticleVisitStreamMess mess) {
        //1.更新文章的阅读、点赞、收藏、评论的数量
        ApArticle apArticle = updateArticle(mess);
        //2.计算文章的分值
        Integer score = computeScore(apArticle);
        score = score * 3;
        //3.替换当前文章对应频道的热点数据
        String articleListStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + apArticle.getChannelId());
        if(StringUtils.isNotBlank(articleListStr)){
            List<HotArticleVo> hotArticleVoList = JSON.parseArray(articleListStr, HotArticleVo.class);
            boolean flag = true;
            //如果缓存中只存在该文章, 只更新分值
            for (HotArticleVo hotArticleVo : hotArticleVoList) {
                if(hotArticleVo.getId().equals(apArticle.getId())){
                    hotArticleVo.setScore(score);
                    flag = false;
                    break;
                }
            }
            //如果缓存不存在, 查询缓存中分值最小的一条数据, 进行分值的比较, 如果当前文章的分值大于缓存中的数据, 就替换
            if(flag){
                if(hotArticleVoList.size() >= 3){
                    hotArticleVoList = hotArticleVoList.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                    HotArticleVo lastHot = hotArticleVoList.get(hotArticleVoList.size() - 1);
                    if(lastHot.getScore() < score){
                        hotArticleVoList.remove(lastHot);
                        HotArticleVo hot = new HotArticleVo();
                        BeanUtils.copyProperties(apArticle, hot);
                        hot.setScore(score);
                        hotArticleVoList.add(hot);
                    }
                }
                else{
                    HotArticleVo hot = new HotArticleVo();
                    BeanUtils.copyProperties(apArticle, hot);
                    hot.setScore(score);
                    hotArticleVoList.add(hot);
                }
            }
            //缓存到redis
            hotArticleVoList = hotArticleVoList.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
            cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + apArticle.getChannelId(), JSON.toJSONString(hotArticleVoList));
        }
        //4.替换推荐对应的热点数据
        String articleListAllStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG);
        if(StringUtils.isNotBlank(articleListAllStr)){
            List<HotArticleVo> hotArticleVoAllList = JSON.parseArray(articleListAllStr, HotArticleVo.class);
            boolean flag = true;
            //如果缓存中只存在该文章, 只更新分值
            for (HotArticleVo hotArticleVo : hotArticleVoAllList) {
                if(hotArticleVo.getId().equals(apArticle.getId())){
                    hotArticleVo.setScore(score);
                    flag = false;
                    break;
                }
            }
            //如果缓存不存在, 查询缓存中分值最小的一条数据, 进行分值的比较, 如果当前文章的分值大于缓存中的数据, 就替换
            if(flag){
                if(hotArticleVoAllList.size() >= 3){
                    hotArticleVoAllList = hotArticleVoAllList.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                    HotArticleVo lastHot = hotArticleVoAllList.get(hotArticleVoAllList.size() - 1);
                    if(lastHot.getScore() < score){
                        hotArticleVoAllList.remove(lastHot);
                        HotArticleVo hot = new HotArticleVo();
                        BeanUtils.copyProperties(apArticle, hot);
                        hot.setScore(score);
                        hotArticleVoAllList.add(hot);
                    }
                }
                else{
                    HotArticleVo hot = new HotArticleVo();
                    BeanUtils.copyProperties(apArticle, hot);
                    hot.setScore(score);
                    hotArticleVoAllList.add(hot);
                }
            }
            //缓存到redis
            hotArticleVoAllList = hotArticleVoAllList.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
            cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG, JSON.toJSONString(hotArticleVoAllList));
        }
    }

    private ApArticle updateArticle(ArticleVisitStreamMess mess){
        ApArticle apArticle = getById(mess.getArticleId());
        apArticle.setCollection(apArticle.getCollection() == null ? 0 : apArticle.getCollection() + mess.getCollect());
        apArticle.setComment(apArticle.getComment() == null ? 0 : apArticle.getComment() + mess.getComment());
        apArticle.setLikes(apArticle.getLikes() == null ? 0 : apArticle.getLikes() + mess.getLike());
        apArticle.setViews(apArticle.getViews() == null ? 0 : apArticle.getViews() + mess.getView());
        updateById(apArticle);
        return apArticle;
    }

    //计算文章的具体分值
    private Integer computeScore(ApArticle apArticle){
        Integer score = 0;
        if(apArticle.getLikes() != null){
            score += apArticle.getLikes() * ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT;
        }
        if(apArticle.getViews() != null){
            score += apArticle.getViews();
        }
        if(apArticle.getComment() != null){
            score += apArticle.getComment() * ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT;
        }
        if(apArticle.getCollection() != null){
            score += apArticle.getCollection() * ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT;
        }
        return score;
    }
}
