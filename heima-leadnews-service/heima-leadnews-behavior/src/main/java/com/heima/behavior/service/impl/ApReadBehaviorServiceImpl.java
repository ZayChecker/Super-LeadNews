package com.heima.behavior.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.behavior.service.ApReadBehaviorService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.constants.HotArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.behaviour.dtos.ReadBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.UpdateArticleMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@Slf4j
public class ApReadBehaviorServiceImpl implements ApReadBehaviorService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    //保存阅读行为
    @Override
    public ResponseResult readBehavior(ReadBehaviorDto readBehaviorDto) {
        //1.检查参数
        if(readBehaviorDto == null || readBehaviorDto.getArticleId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if(user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //更新阅读次数
        String readBehaviorJson = (String) cacheService.hGet(BehaviorConstants.READ_BEHAVIOR + readBehaviorDto.getArticleId().toString(), user.getId().toString());
        if(StringUtils.isNotBlank(readBehaviorJson)){
            ReadBehaviorDto passReadBehaviorDto = JSON.parseObject(readBehaviorJson, ReadBehaviorDto.class);
            readBehaviorDto.setCount((short) (readBehaviorDto.getCount() + passReadBehaviorDto.getCount()));
        }
        //保存当前key
        cacheService.hPut(BehaviorConstants.READ_BEHAVIOR + readBehaviorDto.getArticleId().toString(), user.getId().toString(), JSON.toJSONString(readBehaviorDto));

        //发送消息, 数据聚合
        UpdateArticleMess updateArticleMess = new UpdateArticleMess();
        updateArticleMess.setArticleId(readBehaviorDto.getArticleId());
        updateArticleMess.setType(UpdateArticleMess.UpdateArticleType.VIEWS);
        updateArticleMess.setAdd(1);
        kafkaTemplate.send(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC, JSON.toJSONString(updateArticleMess));

        //用户看过的文章
        cacheService.userReadArticle(user.getId(), readBehaviorDto.getArticleId());

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
