package com.heima.article.controller.v1;


import com.heima.article.service.ApCommentService;
import com.heima.model.article.dtos.ArticleCommentDto;
import com.heima.model.article.dtos.CommentDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comment")
public class CommentController {

    @Autowired
    private ApCommentService apCommentService;

    //加载文章下面的评论
    @PostMapping("/load")
    public ResponseResult load(@RequestBody ArticleCommentDto articleCommentDto){
        return apCommentService.loadComment(articleCommentDto);
    }

    //新增评论
    @PostMapping("/save")
    public ResponseResult save(@RequestBody CommentDto commentDto){
        return apCommentService.saveComment(commentDto);
    }

    //删除评论
    @PostMapping("/delete")
    public ResponseResult delete(@RequestBody CommentDto commentDto){
        return apCommentService.deleteComment(commentDto);
    }

}
