package com.heima.article.service;

import com.heima.model.article.pojos.ApArticle;

public interface ApArticleFreemarkerService {

    //生成静态文件, 上传到minIO中
    public void buildArticleToMinIO(ApArticle apArticle, String content);
}
