package com.heima.wemedia.service;

import java.util.Date;

public interface WmNewsTaskService {

    //添加任务到延迟队列中
    //文章id  发布的时间(可以作为任务的执行时间)
    public void addNewsToTask(Integer id, Date publishTime);

    //消费任务, 审核文章
    public void scanNewsByTask();
}
