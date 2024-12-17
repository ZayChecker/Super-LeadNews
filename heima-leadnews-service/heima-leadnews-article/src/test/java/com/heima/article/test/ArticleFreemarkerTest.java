package com.heima.article.test;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.ArticleApplication;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.service.ApArticleService;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleContent;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.units.qual.A;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = ArticleApplication.class)
@RunWith(SpringRunner.class)
public class ArticleFreemarkerTest {

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ApArticleService apArticleService;

    @Test
    public void createStaticUrlTest() throws Exception{
        //1.获取文章内容
        ApArticleContent apArticleContent = apArticleContentMapper.selectOne(
                Wrappers.lambdaQuery(ApArticleContent.class)
                        .eq(ApArticleContent::getArticleId, 1383827787629252610L));
        if(apArticleContent != null && StringUtils.isNoneBlank(apArticleContent.getContent())){
            //2.文章内容通过freemarker生成html文件
            Template template = configuration.getTemplate("article.ftl");
            //数据模型
            Map<String, Object> content = new HashMap<>();
            content.put("content", JSONArray.parseArray(apArticleContent.getContent()));
            StringWriter out = new StringWriter();   //输出流
            //合成
            template.process(content, out);

            //3.把html文件上传到minio中
            InputStream in = new ByteArrayInputStream(out.toString().getBytes());
            String path = fileStorageService.uploadHtmlFile("", apArticleContent.getArticleId() + ".html", in);

            //4.修改ap_article表，保存static_url字段
            apArticleService.lambdaUpdate()
                    .set(ApArticle::getStaticUrl, path)
                    .eq(ApArticle::getId, apArticleContent.getArticleId())
                    .update();
        }
    }


}
