package com.heima.model.article.pojos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("ap_comment")
public class ApComment implements Serializable {

    @TableId(value = "id",type = IdType.ID_WORKER)
    private String id;

    /**
     * 作者id
     */
    private Long authorId;

    /**
     * 评论所属文章id
     */
    private String articleId;

    /**
     * 父级评论id
     */
    private String parentId;

    /**
     * 回复的是哪条评论
     */
    private String pointTo;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 创建时间
     */
    private Date createdTime;

    /**
     * 发布时间
     */
    private Date publishTime;

    /**
     * 是否已删除
     * true: 删除   1
     * false: 没有删除  0
     */
    private Integer isDelete;
}
