package com.codingx.chat.infrastructure.storage;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.FileStorageService;
import com.codingx.chat.application.service.StoredArtifact;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * 提供基于本地文件系统的聊天产物存储实现。
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    @Override
    public StoredArtifact saveArtifact(Long conversationId, String fileName, String content) {
        String normalizedFileName = StrUtil.blankToDefault(fileName, "artifact.txt");
        File targetFile = FileUtil.file("artifacts", String.valueOf(conversationId), normalizedFileName);
        FileUtil.mkParentDirs(targetFile);
        FileUtil.writeString(StrUtil.blankToDefault(content, ""), targetFile, StandardCharsets.UTF_8);
        return new StoredArtifact(targetFile.getPath().replace('\\', '/'), targetFile.getPath().replace('\\', '/'));
    }
}
