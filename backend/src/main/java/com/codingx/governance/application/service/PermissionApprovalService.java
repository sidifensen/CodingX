package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.common.exception.BusinessException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

/**
 * 管理危险命令的一次性审批请求。
 *
 * <p>业务约束：审批只保存在服务端内存中，且允许结果必须被同一用户、同一会话、同一运行、
 * 同一工具、同一输入、同一工作目录和同一策略精确消费一次，不能演变成长期授权。</p>
 */
@Service
public class PermissionApprovalService {

    /** 默认等待用户确认的时长，避免后台工具线程无限挂起。 */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    /** 活跃审批请求映射，key 为 requestId。 */
    private final Map<String, ApprovalState> requests = new ConcurrentHashMap<>();

    /**
     * 创建待用户确认的一次性审批请求。
     *
     * @param userId 当前用户 ID，可为空。
     * @param conversationId 当前会话 ID，可为空。
     * @param runId 当前运行 ID，可为空。
     * @param toolCode 工具编码。
     * @param toolInput 工具输入 JSON 或文本。
     * @param workingDirectory 工具工作目录，可为空。
     * @param decision 策略判定结果。
     * @return 待确认请求快照。
     */
    public PermissionApprovalRequest createPendingRequest(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory,
        PermissionPolicyDecision decision
    ) {
        LocalDateTime now = LocalDateTime.now();
        PermissionApprovalRequest request = new PermissionApprovalRequest(
            IdUtil.fastSimpleUUID(),
            userId,
            conversationId,
            runId,
            StrUtil.blankToDefault(toolCode, "unknown"),
            StrUtil.nullToEmpty(toolInput),
            extractCommandText(toolInput),
            workingDirectory == null ? null : workingDirectory.toAbsolutePath().normalize().toString(),
            decision == null ? null : decision.matchedPolicyCode(),
            decision == null ? "MEDIUM" : StrUtil.blankToDefault(decision.riskLevel(), "MEDIUM"),
            decision == null ? "当前操作需要确认后再执行" : StrUtil.blankToDefault(decision.message(), "当前操作需要确认后再执行"),
            PermissionApprovalStatus.PENDING,
            now,
            now.plus(DEFAULT_TIMEOUT),
            null
        );
        requests.put(request.requestId(), new ApprovalState(request));
        return request;
    }

    /**
     * 用户在前端或 CLI 中处理审批请求。
     *
     * @param requestId 请求 ID。
     * @param userId 当前用户 ID；请求创建时 userId 为空则不做归属校验。
     * @param decision 用户决定。
     * @return 处理后的请求快照。
     */
    public PermissionApprovalRequest resolve(String requestId, Long userId, PermissionApprovalDecision decision) {
        ApprovalState state = findState(requestId);
        synchronized (state) {
            PermissionApprovalRequest current = state.request();
            ensureOwnedByCurrentUser(current, userId);
            if (current.status() == PermissionApprovalStatus.EXPIRED || isExpired(current)) {
                complete(state, current.withStatus(PermissionApprovalStatus.EXPIRED, LocalDateTime.now()));
                throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_EXPIRED", "命令确认请求已过期，请重新发起");
            }
            if (current.status() != PermissionApprovalStatus.PENDING) {
                throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_RESOLVED", "该命令确认请求已处理");
            }
            PermissionApprovalStatus nextStatus = decision == PermissionApprovalDecision.ALLOW
                ? PermissionApprovalStatus.ALLOWED
                : PermissionApprovalStatus.DENIED;
            return complete(state, current.withStatus(nextStatus, LocalDateTime.now()));
        }
    }

    /**
     * 等待用户处理审批请求，供聊天执行线程在发布确认卡片后暂停当前工具调用。
     *
     * @param requestId 请求 ID。
     * @param timeout 最长等待时间；为空时使用默认值。
     * @return 用户处理后的请求快照。
     */
    public PermissionApprovalRequest awaitDecision(String requestId, Duration timeout) {
        ApprovalState state = findState(requestId);
        try {
            Duration waitTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
            PermissionApprovalRequest request = state.future().get(waitTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (request.status() == PermissionApprovalStatus.ALLOWED || request.status() == PermissionApprovalStatus.DENIED) {
                return request;
            }
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_EXPIRED", "命令确认请求已过期，请重新发起");
        } catch (TimeoutException exception) {
            synchronized (state) {
                PermissionApprovalRequest expired = state.request().withStatus(PermissionApprovalStatus.EXPIRED, LocalDateTime.now());
                state.update(expired);
                state.future().complete(expired);
            }
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_TIMEOUT", "等待命令确认超时，请重新发起");
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_INTERRUPTED", "命令确认等待已中断");
        } catch (Exception exception) {
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_FAILED", "命令确认处理失败：" + exception.getMessage());
        }
    }

    /**
     * 消费已允许的审批请求，成功后该 requestId 立即变为不可复用。
     *
     * @param requestId 请求 ID。
     * @param userId 当前用户 ID，可为空。
     * @param conversationId 当前会话 ID，可为空。
     * @param runId 当前运行 ID，可为空。
     * @param toolCode 工具编码。
     * @param toolInput 工具输入。
     * @param workingDirectory 工具工作目录，可为空。
     * @param matchedPolicyCode 命中的策略编码。
     * @return 已消费请求快照。
     */
    public PermissionApprovalRequest consumeAllowed(
        String requestId,
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory,
        String matchedPolicyCode
    ) {
        ApprovalState state = findState(requestId);
        synchronized (state) {
            PermissionApprovalRequest current = state.request();
            ensureOwnedByCurrentUser(current, userId);
            if (isExpired(current)) {
                state.update(current.withStatus(PermissionApprovalStatus.EXPIRED, LocalDateTime.now()));
                throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_EXPIRED", "命令确认请求已过期，请重新发起");
            }
            if (current.status() == PermissionApprovalStatus.DENIED) {
                throw new BusinessException("GOVERNANCE_PERMISSION_DENIED", "用户已拒绝执行该命令");
            }
            if (current.status() != PermissionApprovalStatus.ALLOWED) {
                throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_INVALID", "命令确认请求不可用，请重新确认");
            }
            ensureSameRequest(current, userId, conversationId, runId, toolCode, toolInput, workingDirectory, matchedPolicyCode);
            PermissionApprovalRequest consumed = current.withStatus(PermissionApprovalStatus.CONSUMED, LocalDateTime.now());
            state.update(consumed);
            return consumed;
        }
    }

    /**
     * 将审批请求转换为 SSE/接口可序列化载荷，前端和 CLI 使用同一字段名展示确认信息。
     *
     * @param request 审批请求。
     * @return 可序列化 Map。
     */
    public Map<String, Object> toPayload(PermissionApprovalRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.requestId());
        payload.put("conversationId", request.conversationId());
        payload.put("runId", request.runId());
        payload.put("toolCode", request.toolCode());
        payload.put("toolInput", request.toolInput());
        payload.put("command", request.commandText());
        payload.put("workingDirectory", request.workingDirectory());
        payload.put("matchedPolicyCode", request.matchedPolicyCode());
        payload.put("riskLevel", request.riskLevel());
        payload.put("message", request.message());
        payload.put("status", request.status().name());
        payload.put("createdAt", request.createdAt().toString());
        payload.put("expiresAt", request.expiresAt().toString());
        payload.put("summary", buildSummary(request));
        return payload;
    }

    /**
     * 根据用户提交的字符串解析审批决定，Controller 可直接复用并返回统一中文错误。
     *
     * @param rawDecision 前端提交的决定。
     * @return 标准审批决定。
     */
    public PermissionApprovalDecision parseDecision(String rawDecision) {
        String decision = StrUtil.trimToEmpty(rawDecision).toUpperCase();
        if ("ALLOW".equals(decision)) {
            return PermissionApprovalDecision.ALLOW;
        }
        if ("DENY".equals(decision)) {
            return PermissionApprovalDecision.DENY;
        }
        throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_DECISION_INVALID", "命令确认决定无效");
    }

    private ApprovalState findState(String requestId) {
        if (StrUtil.isBlank(requestId)) {
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_INVALID", "命令确认请求不存在或已失效");
        }
        ApprovalState state = requests.get(requestId);
        if (state == null) {
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_INVALID", "命令确认请求不存在或已失效");
        }
        return state;
    }

    private PermissionApprovalRequest complete(ApprovalState state, PermissionApprovalRequest request) {
        state.update(request);
        state.future().complete(request);
        return request;
    }

    private void ensureOwnedByCurrentUser(PermissionApprovalRequest request, Long userId) {
        if (request.userId() != null && !Objects.equals(request.userId(), userId)) {
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_FORBIDDEN", "无权处理该命令确认请求");
        }
    }

    private void ensureSameRequest(
        PermissionApprovalRequest request,
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory,
        String matchedPolicyCode
    ) {
        String normalizedWorkingDirectory = workingDirectory == null ? null : workingDirectory.toAbsolutePath().normalize().toString();
        boolean same = Objects.equals(request.userId(), userId)
            && Objects.equals(request.conversationId(), conversationId)
            && Objects.equals(request.runId(), runId)
            && StrUtil.equals(request.toolCode(), StrUtil.blankToDefault(toolCode, "unknown"))
            && StrUtil.equals(request.toolInput(), StrUtil.nullToEmpty(toolInput))
            && StrUtil.equals(request.workingDirectory(), normalizedWorkingDirectory)
            && StrUtil.equals(request.matchedPolicyCode(), matchedPolicyCode);
        if (!same) {
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_MISMATCH", "命令确认请求与当前工具调用不匹配");
        }
    }

    private boolean isExpired(PermissionApprovalRequest request) {
        return request.expiresAt() != null && LocalDateTime.now().isAfter(request.expiresAt());
    }

    private String extractCommandText(String toolInput) {
        String input = StrUtil.trimToEmpty(toolInput);
        if (input.isBlank()) {
            return "";
        }
        try {
            JSONObject object = JSONUtil.parseObj(input);
            String command = object.getStr("command");
            if (StrUtil.isNotBlank(command)) {
                return command;
            }
            String cmd = object.getStr("cmd");
            if (StrUtil.isNotBlank(cmd)) {
                return cmd;
            }
        } catch (RuntimeException ignored) {
            // 非 JSON 工具输入直接回退原文摘要，避免解析失败影响审批创建。
        }
        return StrUtil.maxLength(input, 400);
    }

    private String buildSummary(PermissionApprovalRequest request) {
        String command = StrUtil.blankToDefault(request.commandText(), request.toolCode());
        return "需要确认执行：" + StrUtil.maxLength(command, 120);
    }

    /**
     * 活跃审批请求及其等待 future，future 只在用户处理或超时时完成。
     */
    private static final class ApprovalState {
        /** 当前请求快照。 */
        private PermissionApprovalRequest request;
        /** 等待用户处理的 future。 */
        private final CompletableFuture<PermissionApprovalRequest> future = new CompletableFuture<>();

        private ApprovalState(PermissionApprovalRequest request) {
            this.request = request;
        }

        private PermissionApprovalRequest request() {
            return request;
        }

        private CompletableFuture<PermissionApprovalRequest> future() {
            return future;
        }

        private void update(PermissionApprovalRequest request) {
            this.request = request;
        }
    }
}
