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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限策略应用服务，负责在本地工具执行前完成策略匹配、审计写入和阻断判定。
 */
@Service
@RequiredArgsConstructor
public class PermissionPolicyService {

    /** 权限策略仓储，用于读取当前启用策略和维护管理端策略配置。 */
    private final GovernancePermissionPolicyRepository policyRepository;
    /** 权限审计仓储，用于记录每次工具调用的判定结果。 */
    private final GovernancePermissionAuditRepository auditRepository;

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
        // 步骤 1：按仓储排序读取启用策略，逐条收窄匹配工具编码、命令片段和路径片段。
        GovernancePermissionPolicy matchedPolicy = policyRepository.findEnabledPolicies().stream()
            .filter(policy -> matchesPolicy(policy, toolCode, toolInput, workingDirectory))
            .findFirst()
            .orElse(null);
        // 步骤 2：未命中策略时按低风险 ALLOW 记录审计，命中策略时以策略动作作为最终判定。
        PermissionPolicyDecision decision = buildDecision(matchedPolicy);
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
        return decision;
    }

    /**
     * 在工具执行链路中强制校验策略，DENY 与 CONFIRM 均会抛出中文业务异常。
     */
    public void requireAllowed(
        Long userId,
        Long conversationId,
        Long runId,
        String toolCode,
        String toolInput,
        Path workingDirectory
    ) {
        // 步骤 1：先做判定并写审计，保证即使后续阻断也能在管理端追溯。
        PermissionPolicyDecision decision = evaluateAndAudit(
            userId,
            conversationId,
            runId,
            toolCode,
            toolInput,
            workingDirectory
        );
        // 步骤 2：MVP 暂无 Electron 二次确认回写，CONFIRM 必须阻断真实工具执行。
        if ("DENY".equalsIgnoreCase(decision.action())) {
            throw new BusinessException("GOVERNANCE_PERMISSION_DENIED", decision.message());
        }
        if ("CONFIRM".equalsIgnoreCase(decision.action())) {
            throw new BusinessException("GOVERNANCE_PERMISSION_CONFIRM_REQUIRED", decision.message());
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
