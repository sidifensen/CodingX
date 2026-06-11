package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernancePermissionAudit;
import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import com.codingx.governance.domain.repository.GovernancePermissionAuditRepository;
import com.codingx.governance.domain.repository.GovernancePermissionPolicyRepository;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 权限策略应用服务，负责在本地工具执行前完成策略匹配、审计写入和阻断判定。
 */
@Service
public class PermissionPolicyService {

    /** 权限策略仓储，用于读取当前启用策略和维护管理端策略配置。 */
    private final GovernancePermissionPolicyRepository policyRepository;
    /** 权限审计仓储，用于记录每次工具调用的判定结果。 */
    private final GovernancePermissionAuditRepository auditRepository;
    /** 危险命令一次性审批服务，用于创建、等待和消费 CONFIRM 请求。 */
    private final PermissionApprovalService approvalService;

    /**
     * Spring 构造器，注入策略仓储、审计仓储和一次性审批服务。
     *
     * @param policyRepository 权限策略仓储。
     * @param auditRepository 权限审计仓储。
     * @param approvalService 危险命令一次性审批服务。
     */
    @Autowired
    public PermissionPolicyService(
        GovernancePermissionPolicyRepository policyRepository,
        GovernancePermissionAuditRepository auditRepository,
        PermissionApprovalService approvalService
    ) {
        this.policyRepository = policyRepository;
        this.auditRepository = auditRepository;
        this.approvalService = approvalService;
    }

    /**
     * 兼容旧单测的构造器；未显式传入审批服务时使用进程内默认实现。
     *
     * @param policyRepository 权限策略仓储。
     * @param auditRepository 权限审计仓储。
     */
    public PermissionPolicyService(
        GovernancePermissionPolicyRepository policyRepository,
        GovernancePermissionAuditRepository auditRepository
    ) {
        this(policyRepository, auditRepository, new PermissionApprovalService());
    }

    /**
     * 执行权限策略判定并写入审计。
     * @param userId 触发用户 ID，可为空。
     * @param conversationId 触发会话 ID，可为空。
     * @param runId 触发运行 ID，可为空。
     * @param toolCode 工具编码。
     * @param toolInput 工具输入 JSON 或文本。
     * @param workingDirectory 当前工具工作目录，可为空。
     * @return 策略判定结果。
     */
    public PermissionPolicyDecision evaluateAndAudit(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory
    ) {
        // 步骤 1：先完成纯策略判定，再统一写入审计，便于审批重试路径复用无审计判定。
        PermissionPolicyDecision decision = evaluatePolicyDecision(toolCode, toolInput, workingDirectory);
        writePolicyAudit(userId, conversationId, runId, toolCode, toolInput, workingDirectory, decision);
        return decision;
    }

    /**
     * 在工具执行链路中强制校验策略；DENY 直接拒绝，CONFIRM 创建一次性审批请求。
     */
    public void requireAllowed(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory
    ) {
        requireAllowed(userId, conversationId, runId, toolCode, toolInput, workingDirectory, null);
    }

    /**
     * 在工具执行链路中强制校验策略，并在用户允许后消费对应一次性审批。
     *
     * @param userId 触发用户 ID，可为空。
     * @param conversationId 触发会话 ID，可为空。
     * @param runId 触发运行 ID，可为空。
     * @param toolCode 工具编码。
     * @param toolInput 工具输入 JSON 或文本。
     * @param workingDirectory 当前工具工作目录，可为空。
     * @param approvalRequestId 前端或 CLI 允许后的审批请求 ID，可为空。
     */
    public void requireAllowed(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory,
        String approvalRequestId
    ) {
        // 步骤 1：首次校验必须写入策略命中审计；带审批 ID 的重试先只判定，不重复写 CONFIRM_REQUIRED。
        boolean retryingApprovedRequest = StrUtil.isNotBlank(approvalRequestId);
        PermissionPolicyDecision decision = retryingApprovedRequest
            ? evaluatePolicyDecision(toolCode, toolInput, workingDirectory)
            : evaluateAndAudit(userId, conversationId, runId, toolCode, toolInput, workingDirectory);
        // 步骤 2：拒绝策略仍立即终止，避免危险命令进入审批旁路。
        if ("DENY".equalsIgnoreCase(decision.action())) {
            if (retryingApprovedRequest) {
                writePolicyAudit(userId, conversationId, runId, toolCode, toolInput, workingDirectory, decision);
            }
            throw new BusinessException("GOVERNANCE_PERMISSION_DENIED", decision.message());
        }
        // 步骤 3：确认策略必须先由用户显式处理；允许结果只可被精确匹配的当前工具调用消费一次。
        if ("CONFIRM".equalsIgnoreCase(decision.action())) {
            if (StrUtil.isNotBlank(approvalRequestId)) {
                approvalService.consumeAllowed(
                    approvalRequestId,
                    userId,
                    conversationId,
                    runId,
                    toolCode,
                    toolInput,
                    workingDirectory,
                    decision.matchedPolicyCode()
                );
                writeApprovalAudit(userId, conversationId, runId, toolCode, toolInput, workingDirectory, decision, "APPROVED_ALLOWED", "用户已确认，允许本次执行");
                return;
            }
            PermissionApprovalRequest request = approvalService.createPendingRequest(
                userId,
                conversationId,
                runId,
                toolCode,
                toolInput,
                workingDirectory,
                decision
            );
            throw new PermissionApprovalRequiredException(request);
        }
        if (retryingApprovedRequest) {
            // 步骤 4：审批 requestId 只能消费原 CONFIRM 策略；若管理员已改成 ALLOW/未命中，必须重新发起确认。
            throw new BusinessException("GOVERNANCE_PERMISSION_APPROVAL_MISMATCH", "命令确认请求与当前策略不匹配");
        }
    }

    /**
     * 查询全部权限策略。
     * @return 未删除策略列表。
     */
    public List<GovernancePermissionPolicy> listPolicies() {
        return policyRepository.findAll();
    }

    /**
     * 查询最近权限审计。
     * @param limit 最大返回条数。
     * @return 最近审计列表。
     */
    public List<GovernancePermissionAudit> listRecentAudits(int limit) {
        return auditRepository.findRecent(limit);
    }

    /**
     * 新增权限策略并补齐默认状态。
     * @param policy 前端提交的策略对象。
     * @return 保存后的策略。
     */
    public GovernancePermissionPolicy createPolicy(GovernancePermissionPolicy policy) {
        LocalDateTime now = LocalDateTime.now();
        GovernancePermissionPolicy saved = policy.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .policyCode(required(policy.getPolicyCode(), "策略编码不能为空"))
            .policyName(required(policy.getPolicyName(), "策略名称不能为空"))
            .action(StrUtil.blankToDefault(policy.getAction(), "DENY").toUpperCase(Locale.ROOT))
            .riskLevel(StrUtil.blankToDefault(policy.getRiskLevel(), "MEDIUM").toUpperCase(Locale.ROOT))
            .enabled(policy.getEnabled() == null ? 1 : policy.getEnabled())
            .sortNo(policy.getSortNo() == null ? 0 : policy.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        policyRepository.save(saved);
        return saved;
    }

    /**
     * 更新权限策略，主键由路径参数决定，避免请求体覆盖其他记录。
     */
    public GovernancePermissionPolicy updatePolicy(Long id, GovernancePermissionPolicy policy) {
        GovernancePermissionPolicy existing = policyRepository.findById(id);
        if (existing == null) {
            throw new BusinessException("GOVERNANCE_POLICY_NOT_FOUND", "权限策略不存在");
        }
        GovernancePermissionPolicy saved = policy.toBuilder()
            .id(id)
            .policyCode(required(policy.getPolicyCode(), "策略编码不能为空"))
            .policyName(required(policy.getPolicyName(), "策略名称不能为空"))
            .action(StrUtil.blankToDefault(policy.getAction(), existing.getAction()).toUpperCase(Locale.ROOT))
            .riskLevel(StrUtil.blankToDefault(policy.getRiskLevel(), existing.getRiskLevel()).toUpperCase(Locale.ROOT))
            .enabled(policy.getEnabled() == null ? existing.getEnabled() : policy.getEnabled())
            .sortNo(policy.getSortNo() == null ? existing.getSortNo() : policy.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        policyRepository.save(saved);
        return saved;
    }

    /**
     * 逻辑删除权限策略。
     * @param id 策略主键。
     */
    public void deletePolicy(Long id) {
        policyRepository.softDeleteById(id);
    }

    private PermissionPolicyDecision buildDecision(GovernancePermissionPolicy policy) {
        if (policy == null) {
            return new PermissionPolicyDecision("ALLOW", "ALLOWED", "LOW", null, "权限策略允许执行");
        }
        String action = StrUtil.blankToDefault(policy.getAction(), "DENY").toUpperCase(Locale.ROOT);
        String result = switch (action) {
            case "ALLOW" -> "ALLOWED";
            case "CONFIRM" -> "CONFIRM_REQUIRED";
            case "DENY" -> "DENIED";
            default -> "DENIED";
        };
        String message = switch (action) {
            case "ALLOW" -> "命中允许策略，已放行执行";
            case "CONFIRM" -> "当前操作需要确认后再执行";
            case "DENY" -> "命令命中禁用策略，已拒绝执行";
            default -> "策略动作不支持，已拒绝执行";
        };
        return new PermissionPolicyDecision(
            action,
            result,
            StrUtil.blankToDefault(policy.getRiskLevel(), "MEDIUM").toUpperCase(Locale.ROOT),
            policy.getPolicyCode(),
            message
        );
    }

    /**
     * 只计算当前工具调用命中的权限策略，不写审计。
     * 业务意图：危险命令审批允许后的重试仍要重新确认当前策略和命令上下文，
     * 但不能再生成一条新的 `CONFIRM_REQUIRED`，否则管理端审计会把一次审批误看成二次拦截。
     *
     * @param toolCode 工具编码。
     * @param toolInput 工具输入 JSON 或文本。
     * @param workingDirectory 当前工具工作目录，可为空。
     * @return 策略判定结果。
     */
    private PermissionPolicyDecision evaluatePolicyDecision(
        String toolCode,
        String toolInput,
        Path workingDirectory
    ) {
        // 步骤 1：按仓储排序读取启用策略，逐条收窄匹配工具编码、命令片段和路径片段。
        GovernancePermissionPolicy matchedPolicy = policyRepository.findEnabledPolicies().stream()
            .filter(policy -> matchesPolicy(policy, toolCode, toolInput, workingDirectory))
            .findFirst()
            .orElse(null);
        // 步骤 2：未命中策略时按低风险 ALLOW 返回，命中策略时以策略动作作为最终判定。
        return buildDecision(matchedPolicy);
    }

    /**
     * 写入常规权限策略审计。
     * @param userId 触发用户 ID，可为空。
     * @param conversationId 触发会话 ID，可为空。
     * @param runId 触发运行 ID，可为空。
     * @param toolCode 工具编码。
     * @param toolInput 工具输入 JSON 或文本。
     * @param workingDirectory 当前工具工作目录，可为空。
     * @param decision 策略判定结果。
     */
    private void writePolicyAudit(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory,
        PermissionPolicyDecision decision
    ) {
        auditRepository.save(GovernancePermissionAudit.builder()
            .id(IdUtil.getSnowflakeNextId())
            .userId(userId)
            .conversationId(conversationId)
            .runId(runId)
            .toolCode(StrUtil.blankToDefault(toolCode, "unknown"))
            .toolInput(StrUtil.maxLength(StrUtil.nullToEmpty(toolInput), 4000))
            .workingDirectory(workingDirectory == null ? null : workingDirectory.toAbsolutePath().normalize().toString())
            .matchedPolicyCode(decision.matchedPolicyCode())
            .decision(decision.action())
            .riskLevel(decision.riskLevel())
            .result(decision.result())
            .message(decision.message())
            .createdAt(LocalDateTime.now())
            .build());
    }

    /**
     * 写入用户已允许后的补充审计记录。
     * 业务意图：首次命中 `CONFIRM` 已记录拦截结果；真正消费允许请求时还要记录一次
     * `APPROVED_ALLOWED`，让管理端能区分“等待确认”和“用户确认后实际放行”两个阶段。
     *
     * @param userId 触发用户 ID，可为空。
     * @param conversationId 触发会话 ID，可为空。
     * @param runId 触发运行 ID，可为空。
     * @param toolCode 工具编码。
     * @param toolInput 工具输入 JSON 或文本。
     * @param workingDirectory 当前工具工作目录，可为空。
     * @param decision 原始策略判定。
     * @param result 审计结果编码。
     * @param message 审计展示消息。
     */
    private void writeApprovalAudit(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory,
        PermissionPolicyDecision decision,
        String result,
        String message
    ) {
        auditRepository.save(GovernancePermissionAudit.builder()
            .id(IdUtil.getSnowflakeNextId())
            .userId(userId)
            .conversationId(conversationId)
            .runId(runId)
            .toolCode(StrUtil.blankToDefault(toolCode, "unknown"))
            .toolInput(StrUtil.maxLength(StrUtil.nullToEmpty(toolInput), 4000))
            .workingDirectory(workingDirectory == null ? null : workingDirectory.toAbsolutePath().normalize().toString())
            .matchedPolicyCode(decision.matchedPolicyCode())
            .decision(decision.action())
            .riskLevel(decision.riskLevel())
            .result(result)
            .message(message)
            .createdAt(LocalDateTime.now())
            .build());
    }

    private boolean matchesPolicy(
        GovernancePermissionPolicy policy,
        String toolCode,
        String toolInput,
        Path workingDirectory
    ) {
        // 步骤 1：工具编码为空表示通配；非空时必须与当前工具编码忽略大小写一致。
        if (StrUtil.isNotBlank(policy.getToolCode()) && !StrUtil.equalsIgnoreCase(policy.getToolCode(), toolCode)) {
            return false;
        }
        String searchableInput = StrUtil.trimToEmpty(toolInput).toLowerCase(Locale.ROOT);
        String searchablePath = (workingDirectory == null ? "" : workingDirectory.toAbsolutePath().normalize().toString())
            .toLowerCase(Locale.ROOT);
        // 步骤 2：命令片段为空表示不限制；非空时必须出现在工具入参文本中。
        if (StrUtil.isNotBlank(policy.getCommandPattern())
            && !searchableInput.contains(policy.getCommandPattern().toLowerCase(Locale.ROOT))) {
            return false;
        }
        // 步骤 3：路径片段可命中工作目录或工具入参，兼容 write/edit/apply_patch 的路径字段。
        return StrUtil.isBlank(policy.getPathPattern())
            || searchablePath.contains(policy.getPathPattern().toLowerCase(Locale.ROOT))
            || searchableInput.contains(policy.getPathPattern().toLowerCase(Locale.ROOT));
    }

    private String required(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException("GOVERNANCE_REQUIRED_FIELD", message);
        }
        return value.trim();
    }
}
