package com.heima.minio.test;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;

import java.io.FileInputStream;

public class MinIOTest {

    //把list.html文件上传到minio中，并且可以在浏览器中访问
    public static void main(String[] args) {
        try {
            FileInputStream fileInputStream = new FileInputStream("D:\\list.html");
            //获取minio的链接信息，创建一个minio的客户端
            MinioClient minioClient = MinioClient.builder().credentials("minio", "minio123")
                                    .endpoint("http://192.168.200.130:9000").build();
            //上传
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object("list.html")      //上传后的文件名称，可以带目录
                    .contentType("text/html")       //文件类型
                    .bucket("leadnews")       //桶名称，与minio管理界面创建的桶一致即可
                    .stream(fileInputStream, fileInputStream.available(), -1).build(); //输入流，文件大小，要上传多少(-1表示全部)
            minioClient.putObject(putObjectArgs);
            //访问路径
            System.out.println("http://192.168.200.130:9000/leadnews/list.html");
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
