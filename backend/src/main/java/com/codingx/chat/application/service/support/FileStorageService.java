package com.codingx.chat.application.service;

/**
 * 定义聊天产物文件存储抽象。
 */
public interface FileStorageService {

    /**
     * 保存一份聊天产物文件。
     * @param conversationId 会话标识。
     * @param fileName 文件名。
     * @param content 文件内容。
     * @return 存储结果。
     */
    StoredArtifact saveArtifact(Long conversationId, String fileName, String content);
}
