package com.heima.article.websocket;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ServerEndpoint(value = "/notification/{authorId}")
public class WebSocketProcess {

    private static ConcurrentHashMap<Long, WebSocketProcess> map = new ConcurrentHashMap<>();
    private Session session;

    @OnOpen
    public void onOpen(Session session, @PathParam("authorId") Long authorId){
        this.session = session;
        map.put(authorId, this);
        log.info("server get a socket from author :" + authorId);
    }

    //收到客户端消息
    @OnMessage
    public void onMessage(String message, @PathParam("authorId") Long authorId){
        System.out.println("server get message from author:" + authorId +", and message is :" + message);
    }

    @OnClose
    public void onClose(Session session, @PathParam("authorId") Long authorId){
        map.remove(authorId);
    }

    public void sendMsg(Long authorId, String message){
        WebSocketProcess socket = map.get(authorId);
        if(socket != null){
            if(socket.session.isOpen()){
                try {
                    socket.session.getBasicRemote().sendText(message);
                    System.out.println("server has send message to author :"+ authorId +", and message is:"+ message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else System.out.println("this author "+ authorId +" socket has closed");
        }
        else System.out.println("this author "+ authorId +" socket has exit");
    }
}
