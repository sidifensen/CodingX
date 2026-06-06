package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.governance.application.service.PermissionPolicyDecision;
import com.codingx.governance.application.service.PermissionPolicyService;
import com.codingx.governance.domain.model.GovernancePermissionAudit;
import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import com.codingx.governance.domain.repository.GovernancePermissionAuditRepository;
import com.codingx.governance.domain.repository.GovernancePermissionPolicyRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证权限策略服务能在工具执行前给出确定性判定并记录审计。
 */
class PermissionPolicyServiceTest {

    /**
     * 命中拒绝策略时应返回 DENY 判定，并写入拒绝审计。
     */
    @Test
    void evaluateShouldDenyDangerousCommandAndWriteAudit() {
        InMemoryPermissionAuditRepository auditRepository = new InMemoryPermissionAuditRepository();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(
                GovernancePermissionPolicy.builder()
                    .id(1L)
                    .policyCode("deny-rm-rf")
                    .policyName("拒绝强制删除")
                    .toolCode("bash")
                    .commandPattern("rm -rf")
                    .action("DENY")
                    .riskLevel("HIGH")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            )),
            auditRepository
        );

        PermissionPolicyDecision decision = service.evaluateAndAudit(
            1002L,
            2001L,
            3001L,
            "bash",
            "{\"command\":\"rm -rf node_modules\"}",
            Path.of("D:/code/CodingX")
        );

        assertEquals("DENY", decision.action());
        assertTrue(decision.message().contains("已拒绝"));
        assertEquals(1, auditRepository.savedAudits.size());
        assertEquals("DENY", auditRepository.savedAudits.getFirst().getDecision());
        assertEquals("HIGH", auditRepository.savedAudits.getFirst().getRiskLevel());
    }

    /**
     * 需要确认策略在 MVP 中必须阻断执行，不能自动放行。
     */
    @Test
    void evaluateShouldReturnConfirmRequiredForConfirmPolicy() {
        InMemoryPermissionAuditRepository auditRepository = new InMemoryPermissionAuditRepository();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(
                GovernancePermissionPolicy.builder()
                    .id(2L)
                    .policyCode("confirm-git-push")
                    .policyName("推送确认")
                    .toolCode("shell_command")
                    .commandPattern("git push")
                    .action("CONFIRM")
                    .riskLevel("MEDIUM")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            )),
            auditRepository
        );

        PermissionPolicyDecision decision = service.evaluateAndAudit(
            1002L,
            2001L,
            3001L,
            "shell_command",
            "{\"command\":\"git push origin main\"}",
            Path.of("D:/code/CodingX")
        );

        assertEquals("CONFIRM", decision.action());
        assertTrue(decision.message().contains("需要确认"));
        assertEquals("CONFIRM_REQUIRED", auditRepository.savedAudits.getFirst().getResult());
    }

    private record InMemoryPermissionPolicyRepository(List<GovernancePermissionPolicy> policies)
        implements GovernancePermissionPolicyRepository {

        @Override
        public List<GovernancePermissionPolicy> findEnabledPolicies() {
            return policies;
        }

        @Override
        public List<GovernancePermissionPolicy> findAll() {
            return policies;
        }

        @Override
        public GovernancePermissionPolicy findById(Long id) {
            return policies.stream().filter(policy -> policy.getId().equals(id)).findFirst().orElse(null);
        }

        @Override
        public void save(GovernancePermissionPolicy policy) {
        }

        @Override
        public void softDeleteById(Long id) {
        }
    }

    private static final class InMemoryPermissionAuditRepository implements GovernancePermissionAuditRepository {
        private final List<GovernancePermissionAudit> savedAudits = new ArrayList<>();

        @Override
        public void save(GovernancePermissionAudit audit) {
            savedAudits.add(audit);
        }

        @Override
        public List<GovernancePermissionAudit> findRecent(int limit) {
            return savedAudits;
        }
    }
}
