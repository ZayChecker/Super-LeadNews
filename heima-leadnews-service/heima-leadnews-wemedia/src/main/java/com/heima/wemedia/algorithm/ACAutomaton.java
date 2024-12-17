package com.heima.wemedia.algorithm;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.util.CollectionUtils;

import java.util.*;

public class ACAutomaton {
    @Data
    @NoArgsConstructor
    public static class ACNode{
        Character am;
        //子节点
        Map<Character, ACNode> children = new HashMap<>();
        //失败节点
        ACNode failNode;
        //存储匹配到的敏感字符长度
        List<Integer> wordLength = new ArrayList<>();
        //是否是结束字符
        private boolean endOfWord;

        public ACNode(Character am){
            this.am = am;
        }

        public String toString(){
            return "ACNode{" +
                    "am=" + am + ","  +
                    "children=" + children + "," +
                    "wordLength=" + wordLength + "}";
        }
    }

    public static ACNode root = new ACNode('-');

    public static ACNode getRoot(){
        root.failNode = null;
        return root;
    }

    //构建字典树
    public static void insert(ACNode root, String s){
        ACNode temp = root;
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if(!temp.children.containsKey(chars[i])){
                temp.children.put(chars[i], new ACNode(chars[i]));
            }
            temp = temp.children.get(chars[i]);
            //如果是最后一个字符，则设置为结束字符
            if(i == chars.length-1){
                if(!temp.endOfWord){
                    temp.setEndOfWord(true);
                    temp.getWordLength().add(chars.length);
                }
            }
        }
    }

    //构建失败指针
    public static void buildFailPoint(ACNode root){
        //第一层的失败指针都是指向root
        Queue<ACNode> queue = new LinkedList<>();
        Map<Character, ACNode> children = root.getChildren();
        for(ACNode acNode: children.values()){
            queue.offer(acNode);
            acNode.setFailNode(root);
        }
        //构建剩余节点的失败指针，按层序遍历
        while (!queue.isEmpty()){
            ACNode pNode = queue.poll();
            children = pNode.getChildren();
            Set<Map.Entry<Character, ACNode>> entries = children.entrySet();
            for(Map.Entry<Character, ACNode> entry : entries){
                Character key = entry.getKey();
                ACNode cNode = entry.getValue();
                //如果当前节点的父节点的fail指针指向的节点下存在与当前节点一样的子节点，则当前节点的fail指针指向该子节点
                if(pNode.failNode.children.containsKey(key)){
                    cNode.setFailNode(pNode.failNode.children.get(key));
                }
                else{
                    cNode.setFailNode(root);
                }
                //如果当前节点的失败节点的wordLength不为空，则合并
                if(!CollectionUtils.isEmpty(cNode.failNode.wordLength)){
                    cNode.getWordLength().addAll(cNode.failNode.wordLength);
                }
                queue.offer(cNode);
            }
        }
    }

    public static Boolean query(ACNode root, String s){
        ACNode temp = root;
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            //如果这个字符串在当前节点的孩子节点找不到，则去失败指针寻找
            while(!temp.getChildren().containsKey(chars[i]) && temp.failNode != null){
                temp = temp.failNode;
            }
            if(temp.getChildren().containsKey(chars[i])){
                temp = temp.getChildren().get(chars[i]);
            }
            else continue;
            if(temp.isEndOfWord()){
                handle(temp, s, i);
                return true;
            }
        }
        return false;
    }

    public static void handle(ACNode acNode, String word, int curPoint){
        for (Integer wordLen : acNode.wordLength){
            int start = curPoint - wordLen + 1;
            String matchStr = word.substring(start, curPoint+1);
            System.out.println("位置信息:[" + start + "," + curPoint + "], 敏感词=" + matchStr);
        }
    }

}
