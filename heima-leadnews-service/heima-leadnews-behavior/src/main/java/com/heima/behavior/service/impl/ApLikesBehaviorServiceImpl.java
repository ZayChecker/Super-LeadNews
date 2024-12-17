package com.heima.behavior.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.behavior.service.ApLikesBehaviorService;
import com.heima.common.constants.BehaviorConstants;
import com.heima.common.constants.HotArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.behaviour.dtos.LikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.UpdateArticleMess;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional
@Slf4j
public class ApLikesBehaviorServiceImpl implements ApLikesBehaviorService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    //存储喜欢数据
    @Override
    public ResponseResult like(LikesBehaviorDto likesBehaviorDto) {
        //1.检查参数
        if(likesBehaviorDto == null || likesBehaviorDto.getArticleId() == null || checkParam(likesBehaviorDto)){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if(user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        UpdateArticleMess updateArticleMess = new UpdateArticleMess();
        updateArticleMess.setArticleId(likesBehaviorDto.getArticleId());
        updateArticleMess.setType(UpdateArticleMess.UpdateArticleType.LIKES);

        //3.点赞 保存数据
        if(likesBehaviorDto.getOperation() == 0){
            Object hUserLike = cacheService.hGet(BehaviorConstants.LIKE_BEHAVIOR + likesBehaviorDto.getArticleId().toString(), user.getId().toString());
            if(hUserLike != null){
                return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID, "已点赞");
            }
            //保存当前key
            cacheService.hPut(BehaviorConstants.LIKE_BEHAVIOR + likesBehaviorDto.getArticleId().toString(), user.getId().toString(), JSON.toJSONString(likesBehaviorDto));
            updateArticleMess.setAdd(1);
        }
        else{
            //删除当前key
            cacheService.hDelete(BehaviorConstants.LIKE_BEHAVIOR + likesBehaviorDto.getArticleId().toString(), user.getId().toString());
            updateArticleMess.setAdd(-1);
        }

        //发送消息, 数据聚合
        kafkaTemplate.send(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC, JSON.toJSONString(updateArticleMess));

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 检查参数
     *
     * @return
     */
    private boolean checkParam(LikesBehaviorDto dto) {
        if (dto.getType() > 2 || dto.getType() < 0 || dto.getOperation() > 1 || dto.getOperation() < 0) {
            return true;
        }
        return false;
    }
}
