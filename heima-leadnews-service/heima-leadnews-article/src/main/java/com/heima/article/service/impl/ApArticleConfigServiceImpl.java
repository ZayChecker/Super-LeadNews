package com.heima.article.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.service.ApArticleConfigService;
import com.heima.model.article.pojos.ApArticleConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
@Transactional
public class ApArticleConfigServiceImpl extends ServiceImpl<ApArticleConfigMapper, ApArticleConfig> implements ApArticleConfigService {

    //根据kafka传递过来的消息来修改文章配置
    @Override
    public void updateByMap(Map map) {
        Object enable = map.get("enable");
        //逻辑是反的
        boolean isDown = true;
        if(enable.equals(1)){
            isDown = false;
        }
        lambdaUpdate()
            .set(ApArticleConfig::getIsDown, isDown)
            .eq(ApArticleConfig::getArticleId, map.get("articleId"))
            .update();
    }
}
