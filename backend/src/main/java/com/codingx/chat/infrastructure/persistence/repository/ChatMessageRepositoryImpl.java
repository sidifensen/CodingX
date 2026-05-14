package com.codingx.chat.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatMessageMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 ChatMessageRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    /**
     * ChatMessageMapper 依赖。
     */
    private final ChatMessageMapper chatMessageMapper;

    /**
     * 持久化 save 处理的状态。
     * @param message 输入参数。
     */
    @Override
    public void save(ChatMessage message) {
        ChatMessageDO dataObject = toDataObject(message);
        if (chatMessageMapper.selectById(message.getId()) == null) {
            chatMessageMapper.insert(dataObject);
        } else {
            chatMessageMapper.updateById(dataObject);
        }
    }

    /**
     * 查询 findByConversationId 需要的数据。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @Override
    public List<ChatMessage> findByConversationId(Long conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .orderByAsc(ChatMessageDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 执行 toDomain 定义的处理逻辑。
     * @param dataObject 输入参数。
     * @return 输入参数。
     */
    private ChatMessage toDomain(ChatMessageDO dataObject) {
        ChatMessage message = ChatMessage.create(
            dataObject.getId(),
            dataObject.getConversationId(),
            ChatMessageRole.valueOf(dataObject.getRole()),
            dataObject.getContent(),
            ChatMessageStatus.valueOf(dataObject.getStatus()),
            dataObject.getProvider(),
            dataObject.getModel(),
            dataObject.getErrorMessage()
        );
        message.restoreRuntimeState(
            dataObject.getRunId(),
            dataObject.getThinkingContent(),
            dataObject.getThinkingDuration(),
            dataObject.getIntentCode(),
            dataObject.getCreatedAt(),
            dataObject.getUpdatedAt()
        );
        return message;
    }

    /**
     * 执行 toDataObject 定义的处理逻辑。
     * @param message 输入参数。
     * @return 输入参数。
     */
    private ChatMessageDO toDataObject(ChatMessage message) {
        ChatMessageDO dataObject = new ChatMessageDO();
        dataObject.setId(message.getId());
        dataObject.setConversationId(message.getConversationId());
        dataObject.setRunId(message.getRunId());
        dataObject.setRole(message.getRole().name());
        dataObject.setContent(message.getContent());
        dataObject.setThinkingContent(message.getThinkingContent());
        dataObject.setThinkingDuration(message.getThinkingDuration());
        dataObject.setIntentCode(message.getIntentCode());
        dataObject.setStatus(message.getStatus().name());
        dataObject.setProvider(message.getProvider());
        dataObject.setModel(message.getModel());
        dataObject.setErrorMessage(message.getErrorMessage());
        dataObject.setCreatedAt(message.getCreatedAt());
        dataObject.setUpdatedAt(message.getUpdatedAt());
        return dataObject;
    }
}
