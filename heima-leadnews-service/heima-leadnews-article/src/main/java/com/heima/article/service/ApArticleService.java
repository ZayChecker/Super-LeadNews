package com.heima.article.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.mess.ArticleVisitStreamMess;
import org.springframework.web.bind.annotation.RequestBody;

public interface ApArticleService extends IService<ApArticle> {

    //根据参数加载文章列表   1-加载更多 2-加载最新
    ResponseResult load(ArticleHomeDto articleHomeDto, Short type);

    //保存app端相关文章
    ResponseResult saveArticle(ArticleDto articleDto);

    //从缓存中加载文章列表
    //type: 1-加载更多 2-加载最新
    public ResponseResult load2(ArticleHomeDto articleHomeDto, Short type, boolean firstPage);

    //更新文章的分值 同时更新缓存中的热点文章数据
    public void updateScore(ArticleVisitStreamMess mess);
}
