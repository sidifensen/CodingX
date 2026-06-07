package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.application.service.ChatWorkspaceBindingService;
import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证用户态工具服务的白名单和高风险确认规则。
 */
@ExtendWith(MockitoExtension.class)
class ChatToolUserServiceTest {

    /**
     * 工具配置仓储，用于模拟管理端已启用的工具记录。
     */
    @Mock
    private ChatToolRepository chatToolRepository;

    /**
     * 工具执行服务，用于断言用户态校验通过后会调用真实执行链路。
     */
    @Mock
    private ChatToolExecutionService chatToolExecutionService;

    /**
     * 本地工作区绑定服务，用于模拟用户已经在桌面端选择的仓库目录。
     */
    @Mock
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    @InjectMocks
    private ChatToolUserService chatToolUserService;

    /**
     * git_diff 只读取工作区差异，不应被写入类高风险确认拦截，供右侧代码审查栏直接调用。
     */
    @Test
    void invokeForCurrentUserShouldAllowGitDiffWithoutHighRiskConfirmation() {
        String question = "{\"mode\":\"unstaged\"}";
        when(chatToolRepository.findByToolCode("git_diff")).thenReturn(
            ChatTool.builder()
                .id(9135L)
                .toolCode("git_diff")
                .displayName("Git 差异读取")
                .enabled(1)
                .build()
        );
        when(chatToolExecutionService.execute(eq("git_diff"), eq(question))).thenReturn(
            new ChatToolExecutionResult(
                "git_diff",
                "已读取 1 个文件差异",
                Map.of("mode", "unstaged")
            )
        );

        try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
            // 步骤：用户态工具入口必须先确认登录态；单测中静态 mock 只放行 checkLogin。
            mockedStpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);

            ChatToolExecutionResult result = chatToolUserService.invokeForCurrentUser(
                "git_diff",
                question,
                false
            );

            assertEquals("git_diff", result.toolCode());
        }

        verify(chatToolExecutionService).execute("git_diff", question);
    }

    /**
     * 用户态侧栏直接调用 git_diff 时必须沿用桌面端已绑定仓库，而不是回退到后端进程目录。
     */
    @Test
    void invokeForCurrentUserShouldBindCurrentUsersRepositoryPathForExecution() {
        String question = "{\"mode\":\"unstaged\"}";
        Path repositoryPath = Path.of("D:/code/test");
        when(chatToolRepository.findByToolCode("git_diff")).thenReturn(
            ChatTool.builder()
                .id(9135L)
                .toolCode("git_diff")
                .displayName("Git 差异读取")
                .enabled(1)
                .build()
        );
        when(chatWorkspaceBindingService.findRepositoryPathByUserId(1001L)).thenReturn(Optional.of(repositoryPath));
        when(chatToolExecutionService.execute(eq("git_diff"), eq(question))).thenAnswer(invocation -> {
            // 步骤：工具执行期间必须能从 ThreadLocal 读取当前用户绑定的工作目录，供 git_diff/read/write 等工具复用。
            assertEquals(
                repositoryPath.toAbsolutePath().normalize(),
                ChatToolExecutionContext.currentToolWorkingDirectory().orElseThrow()
            );
            return new ChatToolExecutionResult(
                "git_diff",
                "已读取 1 个文件差异",
                Map.of("workingDirectory", repositoryPath.toString())
            );
        });

        try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::checkLogin).thenAnswer(invocation -> null);
            mockedStpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1001L);

            ChatToolExecutionResult result = chatToolUserService.invokeForCurrentUser(
                "git_diff",
                question,
                false
            );

            assertEquals("git_diff", result.toolCode());
            // 步骤：调用完成后清理临时绑定，避免 servlet 线程复用时串到下一次工具调用。
            assertEquals(Optional.empty(), ChatToolExecutionContext.currentToolWorkingDirectory());
        }

        verify(chatWorkspaceBindingService).findRepositoryPathByUserId(1001L);
        verify(chatToolExecutionService).execute("git_diff", question);
    }
}
