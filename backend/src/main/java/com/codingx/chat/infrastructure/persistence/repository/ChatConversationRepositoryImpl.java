package com.codingx.chat.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 ChatConversationRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class ChatConversationRepositoryImpl implements ChatConversationRepository {

    /**
     * ChatConversationMapper 依赖。
     */
    private final ChatConversationMapper chatConversationMapper;

    /**
     * 加载 requireById 所需数据，不存在时抛出异常。
     * @param conversationId 输入参数。
     * @return 输入参数。
     */
    @Override
    public ChatConversation requireById(Long conversationId) {
        ChatConversationDO dataObject = chatConversationMapper.selectById(conversationId);
        if (dataObject == null || Integer.valueOf(1).equals(dataObject.getDeleted())) {
            throw new NotFoundException("Conversation not found");
        }
        return toDomain(dataObject);
    }

    /**
     * 持久化 save 处理的状态。
     * @param conversation 输入参数。
     */
    @Override
    public void save(ChatConversation conversation) {
        ChatConversationDO dataObject = toDataObject(conversation);
        if (chatConversationMapper.selectById(conversation.getId()) == null) {
            chatConversationMapper.insert(dataObject);
        } else {
            chatConversationMapper.updateById(dataObject);
        }
    }

    /**
     * 执行逻辑删除，避免误删历史数据。
     * @param conversationId 会话标识。
     */
    @Override
    public void deleteById(Long conversationId) {
        chatConversationMapper.update(
            null,
            new LambdaUpdateWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getId, conversationId)
                .set(ChatConversationDO::getDeleted, 1)
        );
    }

    /**
     * 查询 findByCreatedBy 需要的数据。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    @Override
    public List<ChatConversation> findByCreatedBy(Long userId) {
        return chatConversationMapper.selectList(new LambdaQueryWrapper<ChatConversationDO>()
                .eq(ChatConversationDO::getCreatedBy, userId)
                .eq(ChatConversationDO::getDeleted, 0)
                .orderByDesc(ChatConversationDO::getUpdatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 执行 toDomain 定义的处理逻辑。
     * @param dataObject 输入参数。
     * @return 输入参数。
     */
    private ChatConversation toDomain(ChatConversationDO dataObject) {
        ChatConversation conversation = ChatConversation.create(
            dataObject.getId(),
            dataObject.getTitle(),
            dataObject.getCreatedBy(),
            ChatConversationStatus.valueOf(dataObject.getStatus())
        );
        conversation.restoreRuntimeState(dataObject.getLastMessageAt(), dataObject.getLastRunId());
        return conversation;
    }

    /**
     * 执行 toDataObject 定义的处理逻辑。
     * @param conversation 输入参数。
     * @return 输入参数。
     */
    private ChatConversationDO toDataObject(ChatConversation conversation) {
        ChatConversationDO dataObject = new ChatConversationDO();
        dataObject.setId(conversation.getId());
        dataObject.setTitle(conversation.getTitle());
        dataObject.setCreatedBy(conversation.getCreatedBy());
        dataObject.setStatus(conversation.getStatus().name());
        dataObject.setLastMessageAt(conversation.getLastMessageAt());
        dataObject.setLastRunId(conversation.getLastRunId());
        dataObject.setDeleted(0);
        return dataObject;
    }
}
