package com.codingx.tool.application.service;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * 维护工具执行线程上下文，向工具链路传递当前会话绑定的工作目录与 skill 目录。
 */
public final class ChatToolExecutionContext {

    private static final ThreadLocal<Path> CURRENT_TOOL_WORKING_DIRECTORY = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Path>> SKILL_DIRECTORIES = new ThreadLocal<>();

    private ChatToolExecutionContext() {
    }

    /**
     * 绑定工具执行工作目录。
     * @param workingDirectory 本次工具调用可访问的目录。
     */
    public static void bindToolWorkingDirectory(Path workingDirectory) {
        if (workingDirectory == null) {
            CURRENT_TOOL_WORKING_DIRECTORY.remove();
            return;
        }
        CURRENT_TOOL_WORKING_DIRECTORY.set(workingDirectory.toAbsolutePath().normalize());
    }

    /**
     * 获取当前线程绑定的工具工作目录。
     * @return 工作目录。
     */
    public static Optional<Path> currentToolWorkingDirectory() {
        return Optional.ofNullable(CURRENT_TOOL_WORKING_DIRECTORY.get());
    }

    /**
     * 绑定 skill 目录映射。
     * @param skillDirectories skill 编码到目录路径的映射。
     */
    public static void bindSkillDirectories(Map<String, Path> skillDirectories) {
        if (skillDirectories == null || skillDirectories.isEmpty()) {
            SKILL_DIRECTORIES.remove();
            return;
        }
        SKILL_DIRECTORIES.set(Map.copyOf(skillDirectories));
    }

    /**
     * 获取当前线程绑定的 skill 目录映射。
     * @return skill 编码到目录路径的映射，未绑定时返回空 Map。
     */
    public static Map<String, Path> currentSkillDirectories() {
        Map<String, Path> skillDirs = SKILL_DIRECTORIES.get();
        return skillDirs == null ? Map.of() : skillDirs;
    }

    /**
     * 清理当前线程的工具上下文。
     */
    public static void clear() {
        CURRENT_TOOL_WORKING_DIRECTORY.remove();
        SKILL_DIRECTORIES.remove();
    }
}
