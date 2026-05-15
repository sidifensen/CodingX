package com.codingx.chat.application.service;

/**
 * 描述一次文件存储后的结果。
 * @param storagePath 存储路径。
 * @param publicPath 可访问路径。
 */
public record StoredArtifact(String storagePath, String publicPath) {
}
