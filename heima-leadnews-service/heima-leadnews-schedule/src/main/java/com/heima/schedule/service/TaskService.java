package com.heima.schedule.service;

import com.heima.model.schedule.dtos.Task;

public interface TaskService {

    //添加延时任务
    public long addTask(Task task);

    //取消任务
    public boolean cancelTask(long taskId);

    //按照类型和优先级拉取任务
    public Task poll(int type, int priority);
}
