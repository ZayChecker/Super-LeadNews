package com.heima.article.service;

import com.heima.model.article.dtos.ArticleCommentDto;
import com.heima.model.article.dtos.CommentDto;
import com.heima.model.common.dtos.ResponseResult;

public interface ApCommentService {
    //加载文章下面的评论
    ResponseResult loadComment(ArticleCommentDto articleCommentDto);

    //新增评论
    ResponseResult saveComment(CommentDto commentDto);

    //删除评论
    ResponseResult deleteComment(CommentDto commentDto);
}
