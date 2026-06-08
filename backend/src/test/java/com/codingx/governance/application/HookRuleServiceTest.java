package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.governance.application.service.HookRuleService;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 Hook 生命周期规则作为自动化配置匹配器使用，不再依赖 Hook 审计持久化。
 */
class HookRuleServiceTest {

    /**
     * 启用的 Hook 规则命中触发点和关键字后，应按排序返回给后续桌面通知或宠物联动执行器。
     */
    @Test
    void triggerShouldReturnMatchedEnabledRulesInSortOrder() {
        HookRuleService service = new HookRuleService(
            new InMemoryHookRuleRepository(List.of(
                GovernanceHookRule.builder()
                    .id(1L)
                    .hookCode("task-completed-pet")
                    .hookName("任务完成宠物提示")
                    .triggerPoint("TASK_COMPLETED")
                    .conditionKeyword("编码")
                    .actionType("PET_EVENT")
                    .enabled(1)
                    .sortNo(20)
                    .build(),
                GovernanceHookRule.builder()
                    .id(2L)
                    .hookCode("task-completed-notify")
                    .hookName("任务完成桌面通知")
                    .triggerPoint("TASK_COMPLETED")
                    .actionType("DESKTOP_NOTIFY")
                    .enabled(1)
                    .sortNo(10)
                    .build(),
                GovernanceHookRule.builder()
                    .id(3L)
                    .hookCode("task-failed-notify")
                    .hookName("任务失败桌面通知")
                    .triggerPoint("TASK_FAILED")
                    .actionType("DESKTOP_NOTIFY")
                    .enabled(1)
                    .sortNo(1)
                    .build(),
                GovernanceHookRule.builder()
                    .id(4L)
                    .hookCode("disabled-completed")
                    .hookName("停用完成通知")
                    .triggerPoint("TASK_COMPLETED")
                    .actionType("DESKTOP_NOTIFY")
                    .enabled(0)
                    .sortNo(1)
                    .build()
            ))
        );

        List<GovernanceHookRule> matchedRules = service.trigger("TASK_COMPLETED", 2001L, 3001L, null, "编码任务已完成");

        assertEquals(2, matchedRules.size());
        assertEquals("task-completed-notify", matchedRules.get(0).getHookCode());
        assertEquals("task-completed-pet", matchedRules.get(1).getHookCode());
    }

    private record InMemoryHookRuleRepository(List<GovernanceHookRule> rules) implements GovernanceHookRuleRepository {
        @Override
        public List<GovernanceHookRule> findEnabledByTriggerPoint(String triggerPoint) {
            return rules.stream()
                .filter(rule -> triggerPoint.equals(rule.getTriggerPoint()))
                .filter(rule -> rule.getEnabled() != null && rule.getEnabled() == 1)
                .toList();
        }

        @Override
        public List<GovernanceHookRule> findAll() {
            return rules;
        }

        @Override
        public GovernanceHookRule findById(Long id) {
            return rules.stream().filter(rule -> rule.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public void save(GovernanceHookRule rule) {
        }

        @Override
        public void softDeleteById(Long id) {
        }
    }
}
