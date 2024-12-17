package com.heima.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;

@Mapper
public interface ApArticleMapper extends BaseMapper<ApArticle> {

    //加载文章列表    type：1-加载更多 2-加载最新
    public List<ApArticle> loadArticleList(@Param("dto") ArticleHomeDto articleHomeDto, @Param("type") Short type, @Param("Ids") Set<String> readArticleIds);
}
