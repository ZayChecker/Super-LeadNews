package com.heima.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heima.model.article.dtos.ArticleCommentDto;
import com.heima.model.article.pojos.ApComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApCommentMapper extends BaseMapper<ApComment> {
    //加载二级评论
    public List<ApComment> loadComment(@Param("commentId") String commentId, @Param("type") Integer type, @Param("authorId") Long authorId);
}
