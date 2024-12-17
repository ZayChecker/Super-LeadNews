package com.heima.wemedia.listener;

import com.heima.wemedia.algorithm.ACAutomaton;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

@Component
public class FileChangeListener {

    private WatchService watchService;

    private Path directory;

    @PostConstruct
    public void startFileWatcher() throws IOException, URISyntaxException {
        ACAutomaton.ACNode root = ACAutomaton.getRoot();
        File file = new File("");
        //从文件中读取敏感词构建AC自动机
        BufferedReader reader = new BufferedReader(new FileReader(file.getAbsolutePath() + "\\heima-leadnews-service\\heima-leadnews-wemedia\\src\\main\\java\\com\\heima\\wemedia\\listener\\words"));
        String line;
        while ((line = reader.readLine()) != null){
            System.out.println(line);
            ACAutomaton.insert(root, line);
        }
        ACAutomaton.buildFailPoint(root);

        //指定要监视的目录
        directory = Paths.get(file.getAbsolutePath() + "\\heima-leadnews-service\\heima-leadnews-wemedia\\src\\main\\java\\com\\heima\\wemedia\\listener");
        //获取文件系统的WatchService
        watchService = FileSystems.getDefault().newWatchService();
        //注册目录以便监视
        directory.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
        //开始监视
        watchFileChanges();
    }

    public void watchFileChanges(){
        new Thread(() -> {
            while (true){
                try{
                    WatchKey key = watchService.take();
                    for(WatchEvent<?> event : key.pollEvents()){
                        WatchEvent.Kind<?> kind = event.kind();
                        if(kind == StandardWatchEventKinds.OVERFLOW){
                            continue;
                        }
                        else if(kind == StandardWatchEventKinds.ENTRY_MODIFY){
                            Path changedFile = (Path) event.context();
                            Path fullPath = directory.resolve(changedFile);

                            //读取文件内容并打印
                            byte[] fileContent = Files.readAllBytes(fullPath);
                            String content = new String(fileContent, StandardCharsets.UTF_8);
                            ACAutomaton.ACNode root = ACAutomaton.getRoot();

                            if(content.length() > 0){
                                System.out.println("File " + fullPath + " has been modified:");
                                String[] sensitiveWords = content.split(System.getProperty("line.separator"));
                                for (String sensitiveWord : sensitiveWords){
                                    System.out.println(sensitiveWord);
                                    ACAutomaton.insert(root, sensitiveWord);
                                }
                            }
                        }
                    }

                    boolean valid = key.reset();
                    if(!valid){
                        break;
                    }
                }catch (InterruptedException | IOException e){
                    e.printStackTrace();
                    //需要优雅关闭线程和WatchService
                }
            }
        }).start();
    }

    @PreDestroy
    public void stopFileWatcher(){
        //关闭WatchService和线程
    }
}
