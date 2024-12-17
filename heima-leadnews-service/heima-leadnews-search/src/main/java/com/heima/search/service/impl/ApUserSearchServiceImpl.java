package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.HistorySearchDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.pojos.ApUserSearch;
import com.heima.search.service.ApUserSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ApUserSearchServiceImpl implements ApUserSearchService {

    @Autowired
    private MongoTemplate mongoTemplate;

    //保存用户搜索历史记录
    @Override
    @Async    //由于是新开了个线程, 无法使用ThreadLocal
    public void insert(String keyword, Integer userId) {
        //1.查询当前用户的搜索关键词
        Query query = Query.query(Criteria.where("userId").is(userId).and("keyword").is(keyword));
        ApUserSearch apUserSearch = mongoTemplate.findOne(query, ApUserSearch.class);
        //2.存在则更新创建时间
        if(apUserSearch != null){
            apUserSearch.setCreatedTime(new Date());
            mongoTemplate.save(apUserSearch);   //无id(默认主键)则保存, 有id则新建
            return;
        }
        //3.不存在则判断当前历史记录总数量是否超过10
        apUserSearch = new ApUserSearch();
        apUserSearch.setUserId(userId);
        apUserSearch.setKeyword(keyword);
        apUserSearch.setCreatedTime(new Date());

        Query newquery = Query.query(Criteria.where("userId").is(userId));
        newquery.with(Sort.by(Sort.Direction.DESC, "createdTime"));
        List<ApUserSearch> apUserSearchList = mongoTemplate.find(newquery, ApUserSearch.class);
        if(apUserSearchList == null || apUserSearchList.size() < 10){
            mongoTemplate.save(apUserSearch);
        }
        else{
            //替换最久的那一条
            ApUserSearch lastUserSearch = apUserSearchList.get(apUserSearchList.size() - 1);
            mongoTemplate.findAndReplace(Query.query(Criteria.where("id").is(lastUserSearch.getId())), apUserSearch);
        }
    }

    //查询搜索历史
    @Override
    public ResponseResult findUserSearch() {
        //获取当前用户
        ApUser user = AppThreadLocalUtil.getUser();
        if(user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //根据用户查询数据, 按照时间倒序
        List<ApUserSearch> apUserSearches = mongoTemplate.find(Query.query(Criteria.where("userId").is(user.getId()))
                .with(Sort.by(Sort.Direction.DESC, "createdTime")), ApUserSearch.class);
        return ResponseResult.okResult(apUserSearches);
    }

    //删除历史记录
    @Override
    public ResponseResult delUserSearch(HistorySearchDto historySearchDto) {
        //1.检查参数
        if(historySearchDto.getId() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //2.判断是否登录
        ApUser user = AppThreadLocalUtil.getUser();
        if(user == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        //3.删除
        mongoTemplate.remove(Query.query(Criteria.where("userId").is(user.getId()).and("id").is(historySearchDto.getId())), ApUserSearch.class);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
