package com.heima.model.article.dtos;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArticleCommentDto {

    private String articleId;

    /**
     * 0-默认
     * 1-最新
     * 2-只看作者
     */
    private Integer type;
}
