package com.heima.article.listener;

import com.heima.article.mapper.ApArticleMapper;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedisInitListener implements ApplicationListener<ApplicationEvent> {

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private CacheService cacheService;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof ContextRefreshedEvent){
            List<ApArticle> apArticleList = apArticleMapper.selectList(null);
            Set<String> allArticleIds = apArticleList.stream().map(ApArticle::getId).collect(Collectors.toSet());
            String key = ArticleConstants.ARTICLE_ALL_ID_TOPIC;
            cacheService.leadToRedisSet(key, allArticleIds);
        }
    }
}
