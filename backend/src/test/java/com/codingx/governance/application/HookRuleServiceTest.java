package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codingx.governance.application.service.HookRuleService;
import com.codingx.governance.domain.model.GovernanceHookAudit;
import com.codingx.governance.domain.model.GovernanceHookRule;
import com.codingx.governance.domain.repository.GovernanceHookAuditRepository;
import com.codingx.governance.domain.repository.GovernanceHookRuleRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证 Hook 生命周期规则只记录可审计事件，不直接执行外部副作用。
 */
class HookRuleServiceTest {

    /**
     * 启用的 Hook 规则命中触发点后应写入成功审计。
     */
    @Test
    void triggerShouldWriteAuditForEnabledRule() {
        InMemoryHookAuditRepository auditRepository = new InMemoryHookAuditRepository();
        HookRuleService service = new HookRuleService(
            new InMemoryHookRuleRepository(List.of(
                GovernanceHookRule.builder()
                    .id(1L)
                    .hookCode("before-tool-audit")
                    .hookName("工具前审计")
                    .triggerPoint("BEFORE_TOOL_CALL")
                    .actionType("AUDIT")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            )),
            auditRepository
        );

        service.trigger("BEFORE_TOOL_CALL", 2001L, 3001L, "bash", "执行 git status");

        assertEquals(1, auditRepository.savedAudits.size());
        assertEquals("before-tool-audit", auditRepository.savedAudits.getFirst().getHookCode());
        assertEquals("SUCCESS", auditRepository.savedAudits.getFirst().getStatus());
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

    private static final class InMemoryHookAuditRepository implements GovernanceHookAuditRepository {
        private final List<GovernanceHookAudit> savedAudits = new ArrayList<>();

        @Override
        public void save(GovernanceHookAudit audit) {
            savedAudits.add(audit);
        }

        @Override
        public List<GovernanceHookAudit> findRecent(int limit) {
            return savedAudits;
        }
    }
}
