package com.heima.common.event;

import lombok.Data;
import org.apache.kafka.common.protocol.types.Field;

import java.util.List;
import java.util.Map;

@Data
public class CanalBinlogEvent {

    //变更数据
    private List<Map<String, Object>> data;

    //数据库名称
    private String database;

    //数据原始变更的时间，Canal的消费延迟=ts-es
    private Long es;

    //递增id，从1开始
    private Long id;

    //当前变更是否是DDL语句
    private Boolean isDdl;

    //UPDATE模式下旧数据
    private List<Map<String, Object>> old;

    //主键名称
    private List<String> pkNames;

    //SQL语句
    private String sql;

    //SQL类型
    private Map<String, Object> sqlType;

    //表名
    private String table;

    //ts是指Canal收到这个Binlog，构造为自己协议对象的时间，应用消费的延迟=now-ts
    private Long ts;

    //INSERT、UPDATE、DELETE等
    private String type;
}
