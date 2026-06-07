package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.tool.domain.model.ChatTool;
import com.codingx.tool.domain.repository.ChatToolRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证内置工具启动同步逻辑，避免旧数据库缺少新工具 seed 时用户态调用失败。
 */
@ExtendWith(MockitoExtension.class)
class BuiltinChatToolBootstrapTest {

    /**
     * 工具配置仓储，用于模拟旧库缺记录或已有管理员配置。
     */
    @Mock
    private ChatToolRepository chatToolRepository;

    /**
     * 旧库缺少 git_diff 时应补齐 enabled=1 记录，保证右侧差异栏可调用。
     */
    @Test
    void ensureBuiltinToolsShouldCreateMissingGitDiffTool() {
        when(chatToolRepository.findByToolCode("git_diff")).thenReturn(null);
        BuiltinChatToolBootstrap bootstrap = new BuiltinChatToolBootstrap(chatToolRepository);

        bootstrap.ensureBuiltinTools();

        ArgumentCaptor<ChatTool> toolCaptor = ArgumentCaptor.forClass(ChatTool.class);
        verify(chatToolRepository).save(toolCaptor.capture());
        ChatTool savedTool = toolCaptor.getValue();
        assertEquals(9135L, savedTool.getId());
        assertEquals("git_diff", savedTool.getToolCode());
        assertEquals("Git 差异读取", savedTool.getDisplayName());
        assertEquals(1, savedTool.getEnabled());
        assertEquals(0, savedTool.getDeleted());
    }

    /**
     * 已有记录可能被管理员改过启用状态或描述，启动同步不应覆盖管理员配置。
     */
    @Test
    void ensureBuiltinToolsShouldKeepExistingGitDiffTool() {
        when(chatToolRepository.findByToolCode("git_diff")).thenReturn(
            ChatTool.builder()
                .id(9135L)
                .toolCode("git_diff")
                .displayName("Git 差异读取")
                .enabled(0)
                .deleted(0)
                .build()
        );
        BuiltinChatToolBootstrap bootstrap = new BuiltinChatToolBootstrap(chatToolRepository);

        bootstrap.ensureBuiltinTools();

        verify(chatToolRepository, never()).save(any());
        verify(chatToolRepository).findByToolCode(eq("git_diff"));
    }
}
