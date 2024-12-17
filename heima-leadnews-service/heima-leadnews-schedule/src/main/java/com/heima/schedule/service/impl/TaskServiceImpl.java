package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@Transactional
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskinfoMapper taskinfoMapper;

    @Autowired
    private TaskinfoLogsMapper taskinfoLogsMapper;

    @Autowired
    private CacheService cacheService;

    //添加延时任务
    @Override
    public long addTask(Task task) {
        //1.添加任务到数据库中
        boolean success = addTaskToDb(task);

        if(success){
            //2.添加任务到redis中
            addTaskToCache(task);
        }

        return task.getTaskId();
    }

    //取消任务
    @Override
    public boolean cancelTask(long taskId) {
        //删除任务, 更新任务日志
        Task task = updateDb(taskId, ScheduleConstants.CANCELLED);
        //删除redis的数据
        if(task != null){
            removeTaskFromCache(task);
            return true;
        }
        else return false;
    }

    //按照类型和优先级拉取任务
    @Override
    public Task poll(int type, int priority) {
        Task task = null;
        try{
            //从redis中拉取数据 pop
            String key = type + "_" + priority;
            String task_json = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            if(StringUtils.isNotBlank(task_json)){
                task = JSON.parseObject(task_json, Task.class);
                //修改数据库信息
                updateDb(task.getTaskId(), ScheduleConstants.EXECUTED);    //已完成
            }
            System.out.println(task);
        }catch (Exception e){
            e.printStackTrace();
            log.error("poll task exception");
        }
        return task;
    }

    private boolean addTaskToDb(Task task){
        try {
            //保存到任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            //设置taskId
            task.setTaskId(taskinfo.getTaskId());

            //保存到任务日志表
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void addTaskToCache(Task task){
        String key = task.getTaskType() + "_" + task.getPriority();

        //获取5分钟之后的时间 毫秒值
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long nextScheduleTime = calendar.getTimeInMillis();

        //如果任务的执行时间小于等于当前时间, 存入list
        if(task.getExecuteTime() <= System.currentTimeMillis()){
            cacheService.lLeftPush(ScheduleConstants.TOPIC + key, JSON.toJSONString(task));
        }
        else if(task.getExecuteTime() <= nextScheduleTime){
            //如果任务的执行时间大于当前时间&&小于等于预设时间(未来5分钟), 存入zset中
            cacheService.zAdd(ScheduleConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }
    }

    private Task updateDb(long taskId, int status){
        Task task = null;
        try{
            //删除任务
            taskinfoMapper.deleteById(taskId);

            //更新任务日志
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs, task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        }catch (Exception e){
            log.error("task cancel exception taskId = {}", taskId);
        }
        return task;
    }

    private void removeTaskFromCache(Task task){
        String key = task.getTaskType() + "_" + task.getPriority();
        if(task.getExecuteTime() <= System.currentTimeMillis()){
            cacheService.lRemove(ScheduleConstants.TOPIC + key, 0, JSON.toJSONString(task));
        }
        else{
            cacheService.zRemove(ScheduleConstants.FUTURE + key, JSON.toJSONString(task));
        }
    }

    //未来数据定时刷新(每分钟刷新一次)
    @Scheduled(cron = "0 */1 * * * ?")   //cron表达式由6个字段组成, 分别代表秒、分钟、小时、日期、月份和星期几
    public void refresh(){
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);  //30s
        if(StringUtils.isNotBlank(token)){       //分布式锁
            log.info("未来数据定时刷新---定时任务");
            //获取所有未来数据的集合key
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            for (String futureKey : futureKeys) {  //future_100_50
                //按照key和分值查询符合条件的数据
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis()); //找出分值在0-当前时间的value
                //获取当前时间的数据key
                String topicKey = ScheduleConstants.TOPIC + futureKey.split(ScheduleConstants.FUTURE)[1];  //切割成俩部分, 取右边
                //同步数据
                if(!tasks.isEmpty()){
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    log.info("成功地将" + futureKey + "刷新到了" + topicKey);
                }
            }
        }
    }

    @PostConstruct      //开机自启动
    @Scheduled(cron = "0 */5 * * * ?")
    public void reloadData(){
        //清理缓存中的数据 list zset  防止重复添加
        clearCache();
        //查询符合条件的任务, 小于未来5分钟的数据
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        List<Taskinfo> taskinfoList = taskinfoMapper.selectList(Wrappers.lambdaQuery(Taskinfo.class).lt(Taskinfo::getExecuteTime, calendar.getTime()));

        //把任务添加到redis
        if(taskinfoList != null && taskinfoList.size() > 0){
            for (Taskinfo taskinfo : taskinfoList) {
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo, task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }

        log.info("数据库的任务同步到了redis");
    }

    public void clearCache(){
        Set<String> topicKeys = cacheService.scan(ScheduleConstants.TOPIC + "*");
        Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
        cacheService.delete(topicKeys);
        cacheService.delete(futureKeys);
    }
}
