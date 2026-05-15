package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.chat.domain.repository.ChatIntentNodeRepository;
import com.codingx.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端意图树服务的 ragent 字段兼容、树形组装与删除保护规则。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatIntentServiceTest {

    @Mock
    private ChatIntentNodeRepository chatIntentNodeRepository;

    @InjectMocks
    private AdminChatIntentService adminChatIntentService;

    /**
     * 树形接口需要按父子编码组装节点，并保留子节点排序，供管理端左树稳定渲染。
     */
    @Test
    void listTreeBuildsChildren() {
        when(chatIntentNodeRepository.findAllNodes()).thenReturn(List.of(
            ChatIntentNode.builder().id(1L).intentCode("root").name("根节点").kind(0).intentType("kb").sortOrder(1).sortNo(1).build(),
            ChatIntentNode.builder().id(3L).intentCode("child-b").parentCode("root").name("子节点B").kind(2).intentType("mcp").sortOrder(3).sortNo(3).build(),
            ChatIntentNode.builder().id(2L).intentCode("child-a").parentCode("root").name("子节点A").kind(1).intentType("system").sortOrder(2).sortNo(2).build()
        ));

        List<ChatIntentNode> tree = adminChatIntentService.listTree();

        assertEquals(1, tree.size());
        ChatIntentNode root = tree.getFirst();
        assertEquals("root", root.getIntentCode());
        assertEquals(2, root.getChildren().size());
        assertEquals("child-a", root.getChildren().getFirst().getIntentCode());
        assertEquals("child-b", root.getChildren().get(1).getIntentCode());
    }

    /**
     * 新管理端请求只传 kind 时，服务必须派生 intentType，避免运行时分流字段缺失。
     */
    @Test
    void saveDerivesIntentTypeFromKind() {
        when(chatIntentNodeRepository.existsByIntentCode(eq("sys-welcome"), isNull())).thenReturn(false);

        ChatIntentNode saved = adminChatIntentService.save(
            ChatIntentNode.builder().intentCode("sys-welcome").name("欢迎").kind(1).sortOrder(5).build()
        );

        assertEquals("system", saved.getIntentType());
        assertEquals(1, saved.getKind());
        verify(chatIntentNodeRepository).save(argThat(node ->
            "system".equals(node.getIntentType()) && Integer.valueOf(1).equals(node.getKind())
        ));
    }

    /**
     * 旧接口仍可能只传 intentType，服务必须反向派生 kind 供新管理端回显。
     */
    @Test
    void saveDerivesKindFromIntentType() {
        when(chatIntentNodeRepository.existsByIntentCode(eq("sales-data"), isNull())).thenReturn(false);

        ChatIntentNode saved = adminChatIntentService.save(
            ChatIntentNode.builder().intentCode("sales-data").name("销售数据").intentType("mcp").sortNo(7).build()
        );

        assertEquals(2, saved.getKind());
        assertEquals("mcp", saved.getIntentType());
        verify(chatIntentNodeRepository).save(argThat(node ->
            Integer.valueOf(2).equals(node.getKind()) && "mcp".equals(node.getIntentType())
        ));
    }

    /**
     * 意图编码是跨前后端引用的业务唯一键，保存时必须阻止重复编码写入。
     */
    @Test
    void saveRejectsDuplicateIntentCode() {
        when(chatIntentNodeRepository.existsByIntentCode(eq("group"), isNull())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () ->
            adminChatIntentService.save(ChatIntentNode.builder().intentCode("group").name("重复节点").kind(0).build())
        );

        assertEquals("意图编码已存在", exception.getMessage());
        verify(chatIntentNodeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 删除父节点会破坏树结构，因此存在未删除子节点时必须返回中文业务错误。
     */
    @Test
    void deleteRejectsNodeWithChildren() {
        when(chatIntentNodeRepository.findById(3001L)).thenReturn(
            ChatIntentNode.builder().id(3001L).intentCode("group").name("集团信息化").build()
        );
        when(chatIntentNodeRepository.hasChildren("group")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> adminChatIntentService.delete(3001L));

        assertEquals("该意图存在子节点，不能删除", exception.getMessage());
        verify(chatIntentNodeRepository, never()).softDeleteById(3001L);
    }

    /**
     * 更新接口必须以路径 ID 为准，防止请求体 ID 被前端误传后覆盖其他节点。
     */
    @Test
    void updateUsesPathIdAndKeepsCompatibilityFields() {
        when(chatIntentNodeRepository.findById(3002L)).thenReturn(
            ChatIntentNode.builder().id(3002L).intentCode("group-hr").name("人事").createdAt(java.time.LocalDateTime.now()).build()
        );
        when(chatIntentNodeRepository.existsByIntentCode(eq("group-hr-renamed"), eq(3002L))).thenReturn(false);

        ChatIntentNode updated = adminChatIntentService.update(
            3002L,
            ChatIntentNode.builder().id(9999L).intentCode("group-hr-renamed").name("人力资源").kind(0).sortOrder(8).build()
        );

        assertNotNull(updated.getUpdatedAt());
        assertEquals(3002L, updated.getId());
        assertEquals("kb", updated.getIntentType());
        assertEquals(8, updated.getSortNo());
        verify(chatIntentNodeRepository).save(argThat(node ->
            Long.valueOf(3002L).equals(node.getId()) && Integer.valueOf(8).equals(node.getSortNo())
        ));
    }
}
