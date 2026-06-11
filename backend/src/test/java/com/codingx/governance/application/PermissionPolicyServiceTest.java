package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.common.exception.BusinessException;
import com.codingx.governance.application.service.PermissionApprovalDecision;
import com.codingx.governance.application.service.PermissionApprovalRequiredException;
import com.codingx.governance.application.service.PermissionApprovalRequest;
import com.codingx.governance.application.service.PermissionApprovalService;
import com.codingx.governance.application.service.PermissionPolicyDecision;
import com.codingx.governance.application.service.PermissionPolicyService;
import com.codingx.governance.domain.model.GovernancePermissionAudit;
import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import com.codingx.governance.domain.repository.GovernancePermissionAuditRepository;
import com.codingx.governance.domain.repository.GovernancePermissionPolicyRepository;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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

    /**
     * 需要确认的策略应创建一次性审批请求，并把请求上下文字段固定下来供前端确认。
     */
    @Test
    void requireAllowedShouldCreatePendingApprovalRequestForConfirmPolicy() {
        InMemoryPermissionAuditRepository auditRepository = new InMemoryPermissionAuditRepository();
        PermissionApprovalService approvalService = new PermissionApprovalService();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(confirmGitCleanPolicy())),
            auditRepository,
            approvalService
        );

        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.requireAllowed(
                1002L,
                2001L,
                3001L,
                "shell_command",
                "{\"command\":\"git clean -fd\"}",
                Path.of("D:/code/CodingX")
            )
        );

        PermissionApprovalRequest request = exception.request();
        assertNotNull(request.requestId());
        assertEquals(1002L, request.userId());
        assertEquals(2001L, request.conversationId());
        assertEquals(3001L, request.runId());
        assertEquals("shell_command", request.toolCode());
        assertEquals("{\"command\":\"git clean -fd\"}", request.toolInput());
        assertEquals("git clean -fd", request.commandText());
        assertEquals("D:\\code\\CodingX", request.workingDirectory());
        assertEquals("confirm-git-clean", request.matchedPolicyCode());
        assertEquals("HIGH", request.riskLevel());
        assertEquals("PENDING", request.status().name());
        assertEquals("CONFIRM_REQUIRED", auditRepository.savedAudits.getFirst().getResult());
    }

    /**
     * 用户允许后只能放行与原请求完全一致的一次工具调用，重复消费必须失败。
     */
    @Test
    void requireAllowedShouldConsumeAllowedApprovalOnlyOnce() {
        InMemoryPermissionAuditRepository auditRepository = new InMemoryPermissionAuditRepository();
        PermissionApprovalService approvalService = new PermissionApprovalService();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(confirmGitCleanPolicy())),
            auditRepository,
            approvalService
        );
        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.requireAllowed(1002L, 2001L, 3001L, "shell_command", "{\"command\":\"git clean -fd\"}", Path.of("D:/code/CodingX"))
        );
        String requestId = exception.request().requestId();
        approvalService.resolve(requestId, 1002L, PermissionApprovalDecision.ALLOW);

        service.requireAllowed(
            1002L,
            2001L,
            3001L,
            "shell_command",
            "{\"command\":\"git clean -fd\"}",
            Path.of("D:/code/CodingX"),
            requestId
        );
        BusinessException secondUse = assertThrows(
            BusinessException.class,
            () -> service.requireAllowed(
                1002L,
                2001L,
                3001L,
                "shell_command",
                "{\"command\":\"git clean -fd\"}",
                Path.of("D:/code/CodingX"),
                requestId
            )
        );

        assertEquals("GOVERNANCE_PERMISSION_APPROVAL_INVALID", secondUse.getCode());
        assertEquals("APPROVED_ALLOWED", auditRepository.savedAudits.get(1).getResult());
    }

    /**
     * 用户拒绝审批后，即使命令参数一致也不能进入工具执行链路。
     */
    @Test
    void requireAllowedShouldRejectDeniedApproval() {
        InMemoryPermissionAuditRepository auditRepository = new InMemoryPermissionAuditRepository();
        PermissionApprovalService approvalService = new PermissionApprovalService();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(confirmGitCleanPolicy())),
            auditRepository,
            approvalService
        );
        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.requireAllowed(1002L, 2001L, 3001L, "shell_command", "{\"command\":\"git clean -fd\"}", Path.of("D:/code/CodingX"))
        );
        approvalService.resolve(exception.request().requestId(), 1002L, PermissionApprovalDecision.DENY);

        BusinessException denied = assertThrows(
            BusinessException.class,
            () -> service.requireAllowed(
                1002L,
                2001L,
                3001L,
                "shell_command",
                "{\"command\":\"git clean -fd\"}",
                Path.of("D:/code/CodingX"),
                exception.request().requestId()
            )
        );

        assertEquals("GOVERNANCE_PERMISSION_DENIED", denied.getCode());
    }

    /**
     * 审批请求必须精确绑定原命令，不能用一次允许去执行另一条危险命令。
     */
    @Test
    void requireAllowedShouldRejectMismatchedApproval() {
        PermissionApprovalService approvalService = new PermissionApprovalService();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(confirmGitCleanPolicy())),
            new InMemoryPermissionAuditRepository(),
            approvalService
        );
        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.requireAllowed(1002L, 2001L, 3001L, "shell_command", "{\"command\":\"git clean -fd\"}", Path.of("D:/code/CodingX"))
        );
        approvalService.resolve(exception.request().requestId(), 1002L, PermissionApprovalDecision.ALLOW);

        BusinessException mismatch = assertThrows(
            BusinessException.class,
            () -> service.requireAllowed(
                1002L,
                2001L,
                3001L,
                "shell_command",
                "{\"command\":\"git clean -fdx\"}",
                Path.of("D:/code/CodingX"),
                exception.request().requestId()
            )
        );

        assertEquals("GOVERNANCE_PERMISSION_APPROVAL_MISMATCH", mismatch.getCode());
    }

    /**
     * 审批允许后仍必须命中原 CONFIRM 策略；若策略被管理员改成未命中，旧 requestId 不得绕过治理。
     */
    @Test
    void requireAllowedShouldRejectApprovalWhenPolicyNoLongerMatches() {
        PermissionApprovalService approvalService = new PermissionApprovalService();
        PermissionPolicyService createRequestService = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(confirmGitCleanPolicy())),
            new InMemoryPermissionAuditRepository(),
            approvalService
        );
        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> createRequestService.requireAllowed(1002L, 2001L, 3001L, "shell_command", "{\"command\":\"git clean -fd\"}", Path.of("D:/code/CodingX"))
        );
        approvalService.resolve(exception.request().requestId(), 1002L, PermissionApprovalDecision.ALLOW);
        PermissionPolicyService changedPolicyService = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of()),
            new InMemoryPermissionAuditRepository(),
            approvalService
        );

        BusinessException mismatch = assertThrows(
            BusinessException.class,
            () -> changedPolicyService.requireAllowed(
                1002L,
                2001L,
                3001L,
                "shell_command",
                "{\"command\":\"git clean -fd\"}",
                Path.of("D:/code/CodingX"),
                exception.request().requestId()
            )
        );

        assertEquals("GOVERNANCE_PERMISSION_APPROVAL_MISMATCH", mismatch.getCode());
    }

    /**
     * 过期的审批请求必须返回中文业务错误，不能被前端或 CLI 当作成功处理。
     */
    @Test
    void resolveShouldRejectExpiredApprovalRequest() {
        PermissionApprovalService approvalService = new PermissionApprovalService();
        PermissionPolicyService service = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(confirmGitCleanPolicy())),
            new InMemoryPermissionAuditRepository(),
            approvalService
        );
        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.requireAllowed(1002L, 2001L, 3001L, "shell_command", "{\"command\":\"git clean -fd\"}", Path.of("D:/code/CodingX"))
        );
        assertThrows(
            BusinessException.class,
            () -> approvalService.awaitDecision(exception.request().requestId(), Duration.ZERO)
        );

        BusinessException expired = assertThrows(
            BusinessException.class,
            () -> approvalService.resolve(exception.request().requestId(), 1002L, PermissionApprovalDecision.ALLOW)
        );

        assertEquals("GOVERNANCE_PERMISSION_APPROVAL_EXPIRED", expired.getCode());
    }

    /**
     * Spring 启动时必须选择三参构造器注入审批服务，避免多构造器类被误判为需要无参构造。
     */
    @Test
    void springContextShouldCreatePermissionPolicyServiceWithApprovalDependency() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PermissionPolicyServiceSpringConfig.class)) {
            PermissionPolicyService service = context.getBean(PermissionPolicyService.class);

            assertNotNull(service);
        }
    }

    /**
     * 权限策略服务的最小 Spring 依赖集合，用于复现真实启动阶段的构造器选择。
     */
    @Configuration
    @Import(PermissionPolicyService.class)
    static class PermissionPolicyServiceSpringConfig {

        /**
         * 提供权限策略仓储依赖，保持测试只覆盖 Bean 构造，不连接真实数据库。
         */
        @Bean
        GovernancePermissionPolicyRepository governancePermissionPolicyRepository() {
            return new InMemoryPermissionPolicyRepository(List.of());
        }

        /**
         * 提供审计仓储依赖，保持测试只覆盖 Bean 构造，不写真实审计表。
         */
        @Bean
        GovernancePermissionAuditRepository governancePermissionAuditRepository() {
            return new InMemoryPermissionAuditRepository();
        }

        /**
         * 提供审批服务依赖，验证 Spring 会选择包含该依赖的生产构造器。
         */
        @Bean
        PermissionApprovalService permissionApprovalService() {
            return new PermissionApprovalService();
        }
    }

    /**
     * 构造一个需要确认的 git clean 策略，供一次性审批请求测试复用。
     */
    private GovernancePermissionPolicy confirmGitCleanPolicy() {
        return GovernancePermissionPolicy.builder()
            .id(3L)
            .policyCode("confirm-git-clean")
            .policyName("清理工作区确认")
            .toolCode("shell_command")
            .commandPattern("git clean")
            .action("CONFIRM")
            .riskLevel("HIGH")
            .enabled(1)
            .sortNo(1)
            .build();
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
