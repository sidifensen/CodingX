package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import com.codingx.governance.application.service.PermissionPolicyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 验证工具执行服务在 Spring 容器中能选择治理构造器完成注入。
 */
class ChatToolExecutionServiceSpringWiringTest {

    /**
     * 多构造器场景必须显式指定 Spring 注入入口，否则启动时会回退寻找无参构造器。
     */
    @Test
    void serviceShouldUseGovernanceConstructorWhenManagedBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // 步骤 1：注册最小依赖集合，隔离真实数据库和工具执行器，专注验证构造器选择。
            ChatToolRegistry registry = new ChatToolRegistry(List.of());
            registry.init();
            context.registerBean(ChatToolRegistry.class, () -> registry);
            context.registerBean(LocalToolAliasService.class, LocalToolAliasService::new);
            context.registerBean(PermissionPolicyService.class, () -> mock(PermissionPolicyService.class));

            // 步骤 2：按真实 Spring Bean 方式注册目标服务，刷新容器时会暴露构造器选择错误。
            context.register(ChatToolExecutionService.class);
            context.refresh();

            assertNotNull(context.getBean(ChatToolExecutionService.class));
        }
    }
}
