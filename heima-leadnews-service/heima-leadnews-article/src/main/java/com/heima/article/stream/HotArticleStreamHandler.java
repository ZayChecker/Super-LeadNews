package com.heima.article.stream;

import com.alibaba.fastjson.JSON;
import com.heima.common.constants.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.mess.UpdateArticleMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class HotArticleStreamHandler {

    @Bean
    public KStream<String, String> kStream(StreamsBuilder streamsBuilder){  //自动注入
        //接收消息
        KStream<String, String> stream = streamsBuilder.stream(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC);
        //聚合流式处理
        stream.map((key, value) -> {
            UpdateArticleMess updateArticleMess = JSON.parseObject(value, UpdateArticleMess.class);
            //重置消息的key和value
            return new KeyValue<>(updateArticleMess.getArticleId().toString(), updateArticleMess.getType().name() + ":" + updateArticleMess.getAdd());
        })
                .groupBy((key, value) -> key)  //按照文章id进行聚合
                .windowedBy(TimeWindows.of(Duration.ofSeconds(10)))  //时间窗口
                .aggregate(new Initializer<String>() {
                    //初始方法
                    @Override
                    public String apply() {
                        return "COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0";
                    }
                }, new Aggregator<String, String, String>() {
                    //真正的聚合操作 返回值是消息的value
                    @Override
                    public String apply(String key, String value, String aggValue) {  //第三个参数是上面apply返回的结果
                        if(StringUtils.isBlank(value)){
                            return aggValue;
                        }
                        String[] aggArray = aggValue.split(",");
                        int collection = 0, comment = 0, likes = 0, views = 0;
                        for (String agg : aggArray) {
                            String[] split = agg.split(":");
                            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])){
                                case COLLECTION:
                                    collection = Integer.parseInt(split[1]);
                                    break;
                                case COMMENT:
                                    comment = Integer.parseInt(split[1]);
                                    break;
                                case LIKES:
                                    likes = Integer.parseInt(split[1]);
                                    break;
                                case VIEWS:
                                    views = Integer.parseInt(split[1]);
                                    break;
                            }
                        }
                        String[] valArray = value.split(":");
                        switch (UpdateArticleMess.UpdateArticleType.valueOf(valArray[0])){
                            case COLLECTION:
                                collection += Integer.parseInt(valArray[1]);
                                break;
                            case COMMENT:
                                comment += Integer.parseInt(valArray[1]);
                                break;
                            case LIKES:
                                likes += Integer.parseInt(valArray[1]);
                                break;
                            case VIEWS:
                                views += Integer.parseInt(valArray[1]);
                                break;
                        }

                        String formatStr = String.format("COLLECTION:%d,COMMENT:%d,LIKES:%d,VIEWS:%d", collection, comment, likes, views);
                        System.out.println("文章的id:" + key);
                        System.out.println("当前时间窗口内的消息处理结果:" + formatStr);
                        return formatStr;
                    }
                }, Materialized.as("hot-article-stream-count-001"))  //流式处理的状态, 随便给, 多个流式处理只要不重复即可
                .toStream()
                .map((key, value) -> {
                    return new KeyValue<>(key.key().toString(), formatObj(key.key().toString(), value));
                })
                .to(HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC);  //发送消息

        return stream;
    }

    //格式化消息的value数据 把articleId加上去
    public String formatObj(String articleId, String value){
        ArticleVisitStreamMess mess = new ArticleVisitStreamMess();
        mess.setArticleId(Long.valueOf(articleId));
        String[] valArray = value.split(",");
        for (String val : valArray) {
            String[] split = val.split(":");
            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])){
                case COLLECTION:
                    mess.setCollect(Integer.parseInt(split[1]));
                    break;
                case COMMENT:
                    mess.setComment(Integer.parseInt(split[1]));
                    break;
                case LIKES:
                    mess.setLike(Integer.parseInt(split[1]));
                    break;
                case VIEWS:
                    mess.setView(Integer.parseInt(split[1]));
                    break;
            }
        }
        log.info("聚合消息处理之后大的结果为:{}",JSON.toJSONString(mess));
        return JSON.toJSONString(mess);
    }
}
