package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernanceHookAudit;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookAuditRepository;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Hook 规则应用服务，负责记录工具调用和任务生命周期中的可审计事件。
 */
@Service
@RequiredArgsConstructor
public class HookRuleService {

    /** Hook 规则仓储，用于按触发点读取当前启用规则。 */
    private final GovernanceHookRuleRepository ruleRepository;
    /** Hook 审计仓储，用于追加生命周期触发记录。 */
    private final GovernanceHookAuditRepository auditRepository;

    /**
     * 触发生命周期 Hook。MVP 仅支持 AUDIT 动作，不执行外部命令或脚本。
     * @param triggerPoint 触发点。
     * @param conversationId 会话 ID，可为空。
     * @param runId 运行 ID，可为空。
     * @param toolCode 工具编码，可为空。
     * @param contextText 当前触发上下文摘要。
     */
    public void trigger(String triggerPoint, Long conversationId, Long runId, String toolCode, String contextText) {
        // 步骤 1：只读取当前触发点启用规则，禁用规则不会写入新审计。
        for (GovernanceHookRule rule : ruleRepository.findEnabledByTriggerPoint(triggerPoint)) {
            if (!matchesCondition(rule, contextText)) {
                continue;
            }
            // 步骤 2：MVP 不执行 actionConfigJson，只把触发证据写入审计。
            auditRepository.save(GovernanceHookAudit.builder()
                .id(IdUtil.getSnowflakeNextId())
                .hookCode(rule.getHookCode())
                .triggerPoint(triggerPoint)
                .conversationId(conversationId)
                .runId(runId)
                .toolCode(toolCode)
                .status("SUCCESS")
                .message("Hook 已触发并记录审计")
                .createdAt(LocalDateTime.now())
                .build());
        }
    }

    /**
     * 查询全部 Hook 规则。
     * @return 未删除规则列表。
     */
    public List<GovernanceHookRule> listRules() {
        return ruleRepository.findAll();
    }

    /**
     * 查询最近 Hook 审计。
     * @param limit 最大返回条数。
     * @return 最近审计列表。
     */
    public List<GovernanceHookAudit> listRecentAudits(int limit) {
        return auditRepository.findRecent(limit);
    }

    /**
     * 新增 Hook 规则，默认动作限制为 AUDIT，避免绕过权限策略产生副作用。
     */
    public GovernanceHookRule createRule(GovernanceHookRule rule) {
        LocalDateTime now = LocalDateTime.now();
        GovernanceHookRule saved = rule.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .hookCode(required(rule.getHookCode(), "Hook编码不能为空"))
            .hookName(required(rule.getHookName(), "Hook名称不能为空"))
            .triggerPoint(required(rule.getTriggerPoint(), "Hook触发点不能为空").toUpperCase(Locale.ROOT))
            .actionType("AUDIT")
            .actionConfigJson(StrUtil.blankToDefault(rule.getActionConfigJson(), "{}"))
            .enabled(rule.getEnabled() == null ? 1 : rule.getEnabled())
            .sortNo(rule.getSortNo() == null ? 0 : rule.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        ruleRepository.save(saved);
        return saved;
    }

    /**
     * 更新 Hook 规则，继续强制动作类型为 AUDIT。
     */
    public GovernanceHookRule updateRule(Long id, GovernanceHookRule rule) {
        GovernanceHookRule existing = ruleRepository.findById(id);
        if (existing == null) {
            throw new BusinessException("GOVERNANCE_HOOK_NOT_FOUND", "Hook规则不存在");
        }
        GovernanceHookRule saved = rule.toBuilder()
            .id(id)
            .hookCode(required(rule.getHookCode(), "Hook编码不能为空"))
            .hookName(required(rule.getHookName(), "Hook名称不能为空"))
            .triggerPoint(required(rule.getTriggerPoint(), "Hook触发点不能为空").toUpperCase(Locale.ROOT))
            .actionType("AUDIT")
            .actionConfigJson(StrUtil.blankToDefault(rule.getActionConfigJson(), "{}"))
            .enabled(rule.getEnabled() == null ? existing.getEnabled() : rule.getEnabled())
            .sortNo(rule.getSortNo() == null ? existing.getSortNo() : rule.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        ruleRepository.save(saved);
        return saved;
    }

    /**
     * 逻辑删除 Hook 规则。
     * @param id Hook 规则主键。
     */
    public void deleteRule(Long id) {
        ruleRepository.softDeleteById(id);
    }

    private boolean matchesCondition(GovernanceHookRule rule, String contextText) {
        return StrUtil.isBlank(rule.getConditionKeyword())
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(contextText), rule.getConditionKeyword());
    }

    private String required(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException("GOVERNANCE_REQUIRED_FIELD", message);
        }
        return value.trim();
    }
}
