package com.codingx.backend.chat.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.backend.chat.domain.model.ChatConversation;
import com.codingx.backend.chat.domain.model.ChatConversationStatus;
import com.codingx.backend.chat.domain.repository.ChatConversationRepository;
import com.codingx.backend.chat.infrastructure.persistence.dataobject.ChatConversationDO;
import com.codingx.backend.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.backend.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Implements the persistence behavior required by ChatConversationRepositoryImpl.
 */
@Repository
@RequiredArgsConstructor
public class ChatConversationRepositoryImpl implements ChatConversationRepository {

    /**
     * chatConversationMapper value.
     */
    private final ChatConversationMapper chatConversationMapper;

    /**
     * Resolves the required data for requireById or throws when it is missing.
     * @param conversationId input argument.
     * @return processing result.
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
     * Persists the state handled by save.
     * @param conversation input argument.
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
     * Finds the data required by findByCreatedBy.
     * @param userId input argument.
     * @return processing result.
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
     * Executes the logic defined by toDomain.
     * @param dataObject input argument.
     * @return processing result.
     */
    private ChatConversation toDomain(ChatConversationDO dataObject) {
        ChatConversation conversation = ChatConversation.create(
            dataObject.getId(),
            dataObject.getTitle(),
            dataObject.getCreatedBy(),
            ChatConversationStatus.valueOf(dataObject.getStatus())
        );
        if (dataObject.getLastMessageAt() != null) {
            conversation.touch();
        }
        return conversation;
    }

    /**
     * Executes the logic defined by toDataObject.
     * @param conversation input argument.
     * @return processing result.
     */
    private ChatConversationDO toDataObject(ChatConversation conversation) {
        ChatConversationDO dataObject = new ChatConversationDO();
        dataObject.setId(conversation.getId());
        dataObject.setTitle(conversation.getTitle());
        dataObject.setCreatedBy(conversation.getCreatedBy());
        dataObject.setStatus(conversation.getStatus().name());
        dataObject.setLastMessageAt(conversation.getLastMessageAt());
        dataObject.setDeleted(0);
        return dataObject;
    }
}
