package com.heima.model.article.dtos;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentDto {
    //评论id
    private String id;
    //作者id
    private Long authorId;
    //文章id
    private String articleId;
    //父级评论id
    private String parentId;
    //评论内容
    private String content;
    //回复的是哪一条评论
    private String pointTo;

}
