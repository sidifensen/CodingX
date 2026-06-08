package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hook 规则应用服务，负责按任务生命周期事件匹配自动化 Hook 配置。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HookRuleService {

    /** Hook 规则仓储，用于按触发点读取当前启用规则。 */
    private final GovernanceHookRuleRepository ruleRepository;

    /**
     * 触发生命周期 Hook 并返回命中的自动化规则；当前方法只做规则匹配，不执行外部脚本或写审计表。
     * @param triggerPoint 触发点。
     * @param conversationId 会话 ID，可为空。
     * @param runId 运行 ID，可为空。
     * @param toolCode 工具编码，可为空。
     * @param contextText 当前触发上下文摘要。
     * @return 本次命中的启用 Hook 规则，调用方可交给桌面通知、宠物联动或脚本执行器处理。
     */
    public List<GovernanceHookRule> trigger(String triggerPoint, Long conversationId, Long runId, String toolCode, String contextText) {
        // 步骤 1：只读取当前触发点启用规则，禁用规则不会进入后续桌面通知或宠物联动。
        List<GovernanceHookRule> matchedRules = ruleRepository.findEnabledByTriggerPoint(triggerPoint).stream()
            .filter(rule -> matchesCondition(rule, contextText))
            .sorted(Comparator
                .comparing((GovernanceHookRule rule) -> rule.getSortNo() == null ? Integer.MAX_VALUE : rule.getSortNo())
                .thenComparing(rule -> StrUtil.blankToDefault(rule.getHookCode(), "")))
            .toList();
        // 步骤 2：Hook 执行器尚未接入时只写应用日志，避免重新引入数据库 Hook 日志表。
        for (GovernanceHookRule rule : matchedRules) {
            log.info(
                "Hook规则已匹配: hookCode={}, triggerPoint={}, actionType={}, conversationId={}, runId={}, toolCode={}",
                rule.getHookCode(),
                triggerPoint,
                rule.getActionType(),
                conversationId,
                runId,
                toolCode
            );
        }
        return matchedRules;
    }

    /**
     * 查询全部 Hook 规则。
     * @return 未删除规则列表。
     */
    public List<GovernanceHookRule> listRules() {
        return ruleRepository.findAll();
    }

    /**
     * 新增 Hook 规则，默认动作使用桌面通知；真实本机能力由后续桌面执行器按 actionType 消费。
     */
    public GovernanceHookRule createRule(GovernanceHookRule rule) {
        LocalDateTime now = LocalDateTime.now();
        GovernanceHookRule saved = rule.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .hookCode(required(rule.getHookCode(), "Hook编码不能为空"))
            .hookName(required(rule.getHookName(), "Hook名称不能为空"))
            .triggerPoint(required(rule.getTriggerPoint(), "Hook触发点不能为空").toUpperCase(Locale.ROOT))
            .actionType(StrUtil.blankToDefault(rule.getActionType(), "DESKTOP_NOTIFY").toUpperCase(Locale.ROOT))
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
     * 更新 Hook 规则，保留管理员配置的动作类型，供后续自动化执行器分发。
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
            .actionType(StrUtil.blankToDefault(rule.getActionType(), existing.getActionType()).toUpperCase(Locale.ROOT))
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
