package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageMapper;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证消息仓储能够正确映射聊天运行时扩展字段。
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageRepositoryImplTest {

    /**
     * 消息 Mapper 依赖。
     */
    @Mock
    private ChatMessageMapper chatMessageMapper;

    /**
     * 被测仓储实现。
     */
    @InjectMocks
    private ChatMessageRepositoryImpl chatMessageRepository;

    /**
     * 保存消息时需要带上运行时扩展字段，避免后续回放丢失上下文。
     * @throws Exception 反射访问字段失败时抛出。
     */
    @Test
    void saveMapsRuntimeFields() throws Exception {
        ChatMessage message = ChatMessage.assistantMessage(1L, "answer", ChatMessageStatus.COMPLETED, "deepseek", "v3", null);
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 14, 20, 30, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 5, 14, 20, 31, 0);
        setField(message, "runId", 7001L);
        setField(message, "thinkingContent", "thinking");
        setField(message, "thinkingDuration", 12);
        setField(message, "createdAt", createdAt);
        setField(message, "updatedAt", updatedAt);
        when(chatMessageMapper.selectById(message.getId())).thenReturn(null);

        chatMessageRepository.save(message);

        ArgumentCaptor<ChatMessageDO> captor = ArgumentCaptor.forClass(ChatMessageDO.class);
        verify(chatMessageMapper).insert(captor.capture());
        ChatMessageDO dataObject = captor.getValue();
        assertEquals(7001L, readField(dataObject, "runId"));
        assertEquals("thinking", readField(dataObject, "thinkingContent"));
        assertEquals(12, readField(dataObject, "thinkingDuration"));
        assertEquals(createdAt, dataObject.getCreatedAt());
        assertEquals(updatedAt, dataObject.getUpdatedAt());
    }

    /**
     * 读取消息列表时需要还原运行时字段与原始时间，供会话回放直接消费。
     * @throws Exception 反射访问字段失败时抛出。
     */
    @Test
    void findByConversationIdRestoresRuntimeFields() throws Exception {
        ChatMessageDO dataObject = new ChatMessageDO();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 14, 20, 40, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 5, 14, 20, 45, 0);
        dataObject.setId(2L);
        dataObject.setConversationId(1L);
        dataObject.setRole(ChatMessageRole.ASSISTANT.name());
        dataObject.setContent("answer");
        dataObject.setStatus(ChatMessageStatus.COMPLETED.name());
        dataObject.setProvider("deepseek");
        dataObject.setModel("v3");
        dataObject.setCreatedAt(createdAt);
        dataObject.setUpdatedAt(updatedAt);
        setField(dataObject, "runId", 7002L);
        setField(dataObject, "thinkingContent", "reasoning");
        setField(dataObject, "thinkingDuration", 20);
        when(chatMessageMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(dataObject));

        ChatMessage message = chatMessageRepository.findByConversationId(1L).getFirst();

        assertEquals(7002L, readField(message, "runId"));
        assertEquals("reasoning", readField(message, "thinkingContent"));
        assertEquals(20, readField(message, "thinkingDuration"));
        assertEquals(createdAt, readField(message, "createdAt"));
        assertEquals(updatedAt, readField(message, "updatedAt"));
    }

    /**
     * 消息级删除应限制在当前会话内执行逻辑删除，避免误删其他会话同 ID 集合之外的数据。
     */
    @Test
    void softDeleteByConversationIdAndIdsUpdatesDeletedFlagInConversationScope() {
        chatMessageRepository.softDeleteByConversationIdAndIds(1L, List.of(101L, 102L));

        ArgumentCaptor<UpdateWrapper<ChatMessageDO>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(chatMessageMapper).update(isNull(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getValue().getSqlSet();
        assertNotNull(sqlSet);
        assertTrue(sqlSet.contains("deleted"));
        assertTrue(sqlSet.contains("updated_at"));
    }

    /**
     * 通过反射设置私有字段，覆盖新增映射字段的红绿回归。
     * @param target 目标对象。
     * @param fieldName 字段名称。
     * @param value 字段值。
     * @throws Exception 字段不存在或不可访问时抛出。
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 通过反射读取私有字段，校验仓储映射结果。
     * @param target 目标对象。
     * @param fieldName 字段名称。
     * @return 字段值。
     * @throws Exception 字段不存在或不可访问时抛出。
     */
    private Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
