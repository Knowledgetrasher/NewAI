package com.example.newai.repository;

import java.util.List;

public interface ChatHistoryRepository {
    /*
     *
     * type业务类型
     * ChatID会话ID
     * */
    void save(String type, String chatId);
    /*
     * 获取ID，添加类型
     * */
    List<String> getChatIds(String type);
}
