package com.codingx.chat.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证会话仓储能够正确映射运行时扩展字段。
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationRepositoryImplTest {

    /**
     * 会话 Mapper 依赖。
     */
    @Mock
    private ChatConversationMapper chatConversationMapper;

    /**
     * 被测仓储实现。
     */
    @InjectMocks
    private ChatConversationRepositoryImpl chatConversationRepository;

    /**
     * 保存会话时需要带上最近消息时间与最近执行记录。
     * @throws Exception 反射设置字段失败时抛出。
     */
    @Test
    void saveMapsLastMessageAtAndLastRunId() throws Exception {
        ChatConversation conversation = ChatConversation.create(1L, "Runtime", 1001L, ChatConversationStatus.ACTIVE);
        LocalDateTime lastMessageAt = LocalDateTime.of(2026, 5, 14, 20, 15, 30);
        setField(conversation, "lastMessageAt", lastMessageAt);
        setField(conversation, "lastRunId", 9001L);
        when(chatConversationMapper.selectById(1L)).thenReturn(null);

        chatConversationRepository.save(conversation);

        ArgumentCaptor<ChatConversationDO> captor = ArgumentCaptor.forClass(ChatConversationDO.class);
        verify(chatConversationMapper).insert(captor.capture());
        assertEquals(lastMessageAt, captor.getValue().getLastMessageAt());
        assertEquals(9001L, readField(captor.getValue(), "lastRunId"));
    }

    /**
     * 读取会话时需要还原数据库中的运行时扩展字段。
     * @throws Exception 反射访问字段失败时抛出。
     */
    @Test
    void requireByIdRestoresLastMessageAtAndLastRunId() throws Exception {
        ChatConversationDO dataObject = new ChatConversationDO();
        LocalDateTime lastMessageAt = LocalDateTime.of(2026, 5, 14, 21, 0, 0);
        dataObject.setId(1L);
        dataObject.setTitle("Runtime");
        dataObject.setCreatedBy(1001L);
        dataObject.setStatus(ChatConversationStatus.ACTIVE.name());
        dataObject.setDeleted(0);
        dataObject.setLastMessageAt(lastMessageAt);
        setField(dataObject, "lastRunId", 9002L);
        when(chatConversationMapper.selectById(1L)).thenReturn(dataObject);

        ChatConversation conversation = chatConversationRepository.requireById(1L);

        assertEquals(lastMessageAt, readField(conversation, "lastMessageAt"));
        assertEquals(9002L, readField(conversation, "lastRunId"));
    }

    /**
     * 通过反射设置私有字段，保证红测能覆盖新增映射字段。
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
