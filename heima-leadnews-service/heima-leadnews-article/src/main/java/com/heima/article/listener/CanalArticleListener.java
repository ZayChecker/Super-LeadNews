package com.heima.article.listener;

import com.alibaba.fastjson.JSON;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.event.CanalBinlogEvent;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.vos.HotArticleVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class CanalArticleListener {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ApArticleMapper apArticleMapper;

    @KafkaListener(topics = "leadnews_articles_change")
    public void onMessage(String message){
        CanalBinlogEvent event = JSON.parseObject(message, CanalBinlogEvent.class);
        if(event.getType().equals("UPDATE") && event.getTable().equals("ap_article_config")){
            List<Map<String, Object>> data = event.getData();
            Map<String, Object> map = data.get(0);
            String isDown = (String)map.get("is_down");
            if(isDown.equals("1")){
                String articleId = (String)map.get("article_id");
                ApArticle apArticle = apArticleMapper.selectById(articleId);

                String articleListStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + apArticle.getChannelId());
                List<HotArticleVo> hotArticleVoList = JSON.parseArray(articleListStr, HotArticleVo.class);
                for (int i = 0; i < hotArticleVoList.size(); i++) {
                    HotArticleVo articleVo = hotArticleVoList.get(i);
                    if(articleVo.getId().equals(articleId)){
                        hotArticleVoList.remove(articleVo);
                        break;
                    }
                }
                cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + apArticle.getChannelId(), JSON.toJSONString(hotArticleVoList));

                articleListStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG);
                hotArticleVoList = JSON.parseArray(articleListStr, HotArticleVo.class);
                for (int i = 0; i < hotArticleVoList.size(); i++) {
                    HotArticleVo articleVo = hotArticleVoList.get(i);
                    if(articleVo.getId().equals(articleId)){
                        hotArticleVoList.remove(articleVo);
                        break;
                    }
                }
                cacheService.set(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + ArticleConstants.DEFAULT_TAG, JSON.toJSONString(hotArticleVoList));
            }
        }
    }
}
