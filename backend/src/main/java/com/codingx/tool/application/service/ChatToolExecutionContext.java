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
    private static final ThreadLocal<GovernanceContext> GOVERNANCE_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> APPROVAL_REQUEST_ID = new ThreadLocal<>();

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
     * 绑定当前工具调用的治理上下文，供权限策略和审计记录使用。
     * @param userId 当前用户 ID，可为空。
     * @param conversationId 当前会话 ID，可为空。
     * @param runId 当前运行 ID，可为空。
     */
    public static void bindGovernanceContext(Long userId, Long conversationId, Long runId) {
        if (userId == null && conversationId == null && runId == null) {
            GOVERNANCE_CONTEXT.remove();
            return;
        }
        GOVERNANCE_CONTEXT.set(new GovernanceContext(userId, conversationId, runId));
    }

    /**
     * 获取当前线程绑定的治理上下文。
     * @return 治理上下文，未绑定时返回空。
     */
    public static Optional<GovernanceContext> currentGovernanceContext() {
        return Optional.ofNullable(GOVERNANCE_CONTEXT.get());
    }

    /**
     * 绑定当前工具调用要消费的一次性危险命令审批请求。
     *
     * @param requestId 审批请求 ID；为空时清理绑定，下一次 CONFIRM 会重新创建请求。
     */
    public static void bindApprovalRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            APPROVAL_REQUEST_ID.remove();
            return;
        }
        APPROVAL_REQUEST_ID.set(requestId.trim());
    }

    /**
     * 获取当前工具调用要消费的一次性审批请求。
     *
     * @return 审批请求 ID，未绑定时为空。
     */
    public static Optional<String> currentApprovalRequestId() {
        return Optional.ofNullable(APPROVAL_REQUEST_ID.get());
    }

    /**
     * 清理当前线程的工具上下文。
     */
    public static void clear() {
        CURRENT_TOOL_WORKING_DIRECTORY.remove();
        SKILL_DIRECTORIES.remove();
        GOVERNANCE_CONTEXT.remove();
        APPROVAL_REQUEST_ID.remove();
    }

    /**
     * 工具调用治理上下文，保存权限策略审计需要的运行归属。
     *
     * @param userId 当前用户 ID，可为空。
     * @param conversationId 当前会话 ID，可为空。
     * @param runId 当前运行 ID，可为空。
     */
    public record GovernanceContext(Long userId, Long conversationId, Long runId) {
    }
}
