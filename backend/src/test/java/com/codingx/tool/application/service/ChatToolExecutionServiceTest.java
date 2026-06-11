package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.codingx.common.exception.BusinessException;
import com.codingx.governance.application.service.PermissionApprovalDecision;
import com.codingx.governance.application.service.PermissionApprovalRequiredException;
import com.codingx.governance.application.service.PermissionApprovalService;
import com.codingx.governance.application.service.PermissionPolicyService;
import com.codingx.governance.domain.model.GovernancePermissionAudit;
import com.codingx.governance.domain.model.GovernancePermissionPolicy;
import com.codingx.governance.domain.repository.GovernancePermissionAuditRepository;
import com.codingx.governance.domain.repository.GovernancePermissionPolicyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 验证工具执行服务会在进入注册表前统一处理模型可见别名。
 */
class ChatToolExecutionServiceTest {

    /**
     * Claude Code 风格工具名应归一到既有短工具编码执行，并在结果元数据保留归一化信息。
     */
    @Test
    void executeShouldNormalizeClaudeCodeAliasBeforeRegistryLookup() {
        ChatToolExecutor readExecutor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("read");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "读取成功", Map.of("question", question));
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(readExecutor));
        registry.init();
        ChatToolExecutionService service = new ChatToolExecutionService(registry, new LocalToolAliasService());

        ChatToolExecutionResult result = service.execute("ReadFile", "{\"path\":\"README.md\"}");

        assertEquals("read", result.toolCode());
        assertEquals("read", result.metadata().get("canonicalToolCode"));
        assertEquals("ReadFile", result.metadata().get("requestedToolCode"));
        assertEquals("{\"path\":\"README.md\"}", result.metadata().get("question"));
    }

    /**
     * 旧短工具名仍按原编码执行，元数据只补充统一 canonical 字段。
     */
    @Test
    void executeShouldKeepLegacyToolCodeBehavior() {
        ChatToolExecutor grepExecutor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("grep");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "命中 1 处", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(grepExecutor));
        registry.init();
        ChatToolExecutionService service = new ChatToolExecutionService(registry, new LocalToolAliasService());

        ChatToolExecutionResult result = service.execute("grep", "{\"pattern\":\"Agent\"}");

        assertEquals("grep", result.toolCode());
        assertEquals("grep", result.metadata().get("canonicalToolCode"));
        assertEquals("grep", result.metadata().get("requestedToolCode"));
    }

    /**
     * 工具执行前必须先经过权限策略判定；命中拒绝策略时不能进入真实执行器。
     */
    @Test
    void executeShouldRejectToolCallWhenPermissionPolicyDenies() {
        ChatToolExecutor shellExecutor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("shell_command");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                return new ChatToolExecutionResult(toolCode, "不应执行", Map.of());
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(shellExecutor));
        registry.init();
        InMemoryPermissionAuditRepository auditRepository = new InMemoryPermissionAuditRepository();
        PermissionPolicyService permissionPolicyService = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(
                GovernancePermissionPolicy.builder()
                    .id(1L)
                    .policyCode("deny-rm-rf")
                    .policyName("拒绝危险删除")
                    .commandPattern("rm -rf")
                    .action("DENY")
                    .riskLevel("HIGH")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            )),
            auditRepository
        );
        ChatToolExecutionService service = new ChatToolExecutionService(
            registry,
            new LocalToolAliasService(),
            permissionPolicyService
        );

        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.execute("shell_command", "{\"command\":\"rm -rf node_modules\"}")
        );

        assertEquals("GOVERNANCE_PERMISSION_DENIED", exception.getCode());
        assertEquals(1, auditRepository.savedAudits.size());
        assertEquals("DENIED", auditRepository.savedAudits.getFirst().getResult());
    }

    /**
     * 命中确认策略时，第一次调用只创建审批请求，不能提前执行真实命令。
     */
    @Test
    void executeShouldPauseBeforeExecutorWhenPermissionPolicyRequiresApproval() {
        AtomicInteger executionCount = new AtomicInteger();
        ChatToolExecutionService service = buildConfirmProtectedShellService(executionCount);

        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.execute("shell_command", "{\"command\":\"git clean -fd\"}")
        );

        assertEquals(0, executionCount.get());
        assertEquals("git clean -fd", exception.request().commandText());
    }

    /**
     * 用户允许同一审批请求后，工具执行服务才会进入真实执行器且只执行一次。
     */
    @Test
    void executeShouldRunExecutorAfterMatchingApprovalIsAllowed() {
        AtomicInteger executionCount = new AtomicInteger();
        PermissionApprovalService approvalService = new PermissionApprovalService();
        ChatToolExecutionService service = buildConfirmProtectedShellService(executionCount, approvalService);
        PermissionApprovalRequiredException exception = assertThrows(
            PermissionApprovalRequiredException.class,
            () -> service.execute("shell_command", "{\"command\":\"git clean -fd\"}")
        );
        approvalService.resolve(exception.request().requestId(), null, PermissionApprovalDecision.ALLOW);
        ChatToolExecutionContext.bindApprovalRequestId(exception.request().requestId());

        ChatToolExecutionResult result = service.execute("shell_command", "{\"command\":\"git clean -fd\"}");
        ChatToolExecutionContext.bindApprovalRequestId(null);

        assertEquals("已执行", result.content());
        assertEquals(1, executionCount.get());
    }

    /**
     * 构造被确认策略保护的 shell 工具服务，默认使用独立的一次性审批服务。
     */
    private ChatToolExecutionService buildConfirmProtectedShellService(AtomicInteger executionCount) {
        return buildConfirmProtectedShellService(executionCount, new PermissionApprovalService());
    }

    /**
     * 构造被确认策略保护的 shell 工具服务，并注入指定审批服务便于测试 allow/deny 状态。
     */
    private ChatToolExecutionService buildConfirmProtectedShellService(
        AtomicInteger executionCount,
        PermissionApprovalService approvalService
    ) {
        ChatToolExecutor shellExecutor = new ChatToolExecutor() {
            @Override
            public List<String> toolCodes() {
                return List.of("shell_command");
            }

            @Override
            public ChatToolExecutionResult execute(String toolCode, String question) {
                executionCount.incrementAndGet();
                return new ChatToolExecutionResult(toolCode, "已执行", Map.of("question", question));
            }
        };
        ChatToolRegistry registry = new ChatToolRegistry(List.of(shellExecutor));
        registry.init();
        PermissionPolicyService permissionPolicyService = new PermissionPolicyService(
            new InMemoryPermissionPolicyRepository(List.of(
                GovernancePermissionPolicy.builder()
                    .id(2L)
                    .policyCode("confirm-git-clean")
                    .policyName("清理工作区确认")
                    .toolCode("shell_command")
                    .commandPattern("git clean")
                    .action("CONFIRM")
                    .riskLevel("HIGH")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            )),
            new InMemoryPermissionAuditRepository(),
            approvalService
        );
        return new ChatToolExecutionService(registry, new LocalToolAliasService(), permissionPolicyService);
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
