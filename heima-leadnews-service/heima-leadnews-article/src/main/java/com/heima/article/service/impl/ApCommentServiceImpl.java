package com.heima.article.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.mapper.ApCommentMapper;
import com.heima.article.service.ApCommentService;
import com.heima.article.websocket.WebSocketProcess;
import com.heima.model.article.dtos.ArticleCommentDto;
import com.heima.model.article.dtos.CommentDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApComment;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApCommentServiceImpl extends ServiceImpl<ApCommentMapper, ApComment> implements ApCommentService {

    @Autowired
    private ApCommentMapper apCommentMapper;

    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private WebSocketProcess webSocketProcess;

    @Override
    public ResponseResult loadComment(ArticleCommentDto articleCommentDto) {
        Map<ApComment, Object> map = new HashMap<>();
        //拿到文章的作者
        ApArticle apArticle = apArticleMapper.selectById(articleCommentDto.getArticleId());
        //先获取一级评论
        List<ApComment> oneLevelCommentList = apCommentMapper.loadComment("0", articleCommentDto.getType(), apArticle.getAuthorId());
        //获取二级评论
        for(ApComment apComment : oneLevelCommentList){
            List<ApComment> twoLevelCommentList = apCommentMapper.loadComment(apComment.getId(), articleCommentDto.getType(), apArticle.getAuthorId());
            map.put(apComment, twoLevelCommentList);
        }
        return ResponseResult.okResult(map);
    }

    @Override
    public ResponseResult saveComment(CommentDto commentDto) {
        ApComment comment = new ApComment();
        BeanUtils.copyProperties(commentDto, comment);

        apCommentMapper.insert(comment);

        //通知作者有新的评论
        //拿到文章的作者
        ApArticle apArticle = apArticleMapper.selectById(commentDto.getArticleId());
        webSocketProcess.sendMsg(apArticle.getAuthorId(), "作者：" + commentDto.getAuthorId() + "回复了您的评论，评论内容是" + comment.getContent());

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Override
    public ResponseResult deleteComment(CommentDto commentDto) {
        lambdaUpdate().set(ApComment::getIsDelete, 1)
                .eq(ApComment::getId, commentDto.getId())
                .update();
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
