package com.heima.schedule.test;

import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.service.TaskService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.Set;

@SpringBootTest(classes = ScheduleApplication.class)
@RunWith(SpringRunner.class)
public class TaskServiceImplTest {

    @Autowired
    private TaskService taskService;

    @Test
    public void addTask(){
        Task task = new Task();
        task.setTaskType(100);
        task.setPriority(50);
        task.setParameters("task test".getBytes());
        task.setExecuteTime(new Date().getTime());

        long taskId = taskService.addTask(task);
        System.out.println(taskId);
    }

    @Test
    public void poll(){
        Task task = taskService.poll(100, 50);
        System.out.println(task);
    }

    @Test
    public void addTasks(){
        for (int i = 0; i < 5; i++) {
            Task task = new Task();
            task.setTaskType(100+i);
            task.setPriority(50);
            task.setParameters("task test".getBytes());
            task.setExecuteTime(new Date().getTime() + 500 * i);
            long taskId = taskService.addTask(task);
        }
    }
}
