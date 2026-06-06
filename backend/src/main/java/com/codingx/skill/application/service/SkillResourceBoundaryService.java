package com.codingx.skill.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.common.storage.RustFsSkillPackageClient;
import com.codingx.skill.domain.model.ChatSkill;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 技能资源边界服务。
 * <p>
 * 业务意图：技能包内的 resources/scripts 可以被运行时描述和后续工具读取，但读取路径必须限定在技能包目录内，
 * 禁止模型通过 {@code ../} 或绝对路径访问技能包之外的对象。
 */
@Service
@RequiredArgsConstructor
public class SkillResourceBoundaryService {

    /** 技能包对象存储客户端，用于在路径校验后读取目录化技能文件。 */
    private final RustFsSkillPackageClient rustFsSkillPackageClient;

    /**
     * 读取技能包内文本资源。
     *
     * @param skill 技能配置，必须是目录化存储且带 storageKey。
     * @param relativePath 技能包内相对路径。
     * @return UTF-8 文本内容。
     */
    public String readTextResource(ChatSkill skill, String relativePath) {
        String safePath = normalizeRelativeResourcePath(relativePath);
        if (skill == null || StrUtil.isBlank(skill.getStorageKey())) {
            throw new IllegalArgumentException("技能资源目录不能为空");
        }
        byte[] bytes = rustFsSkillPackageClient.downloadDirectoryFile(skill.getStorageKey(), safePath);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 归一化技能包内相对路径。
     *
     * @param relativePath 原始相对路径。
     * @return 安全相对路径。
     */
    public String normalizeRelativeResourcePath(String relativePath) {
        String normalized = StrUtil.blankToDefault(relativePath, "")
            .replace("\\", "/")
            .trim();
        while (StrUtil.startWith(normalized, "./")) {
            normalized = normalized.substring(2);
        }
        normalized = StrUtil.removePrefix(normalized, "/");
        normalized = StrUtil.removeSuffix(normalized, "/");
        if (StrUtil.isBlank(normalized) || Path.of(normalized).isAbsolute()) {
            throw new IllegalArgumentException("技能资源路径越界");
        }
        for (String segment : normalized.split("/")) {
            if (StrUtil.isBlank(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("技能资源路径越界：" + relativePath);
            }
        }
        return normalized;
    }
}
