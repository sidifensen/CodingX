package com.codingx.backend.chat.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.backend.chat.domain.model.ChatMessage;
import com.codingx.backend.chat.domain.model.ChatMessageRole;
import com.codingx.backend.chat.domain.model.ChatMessageStatus;
import com.codingx.backend.chat.domain.repository.ChatMessageRepository;
import com.codingx.backend.chat.infrastructure.persistence.dataobject.ChatMessageDO;
import com.codingx.backend.chat.infrastructure.persistence.mapper.ChatMessageMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatMessageRepositoryImpl implements ChatMessageRepository {

    private final ChatMessageMapper chatMessageMapper;

    @Override
    public void save(ChatMessage message) {
        ChatMessageDO dataObject = toDataObject(message);
        if (chatMessageMapper.selectById(message.getId()) == null) {
            chatMessageMapper.insert(dataObject);
        } else {
            chatMessageMapper.updateById(dataObject);
        }
    }

    @Override
    public List<ChatMessage> findByConversationId(Long conversationId) {
        return chatMessageMapper.selectList(new LambdaQueryWrapper<ChatMessageDO>()
                .eq(ChatMessageDO::getConversationId, conversationId)
                .orderByAsc(ChatMessageDO::getCreatedAt))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    private ChatMessage toDomain(ChatMessageDO dataObject) {
        return ChatMessage.create(
            dataObject.getId(),
            dataObject.getConversationId(),
            ChatMessageRole.valueOf(dataObject.getRole()),
            dataObject.getContent(),
            ChatMessageStatus.valueOf(dataObject.getStatus()),
            dataObject.getProvider(),
            dataObject.getModel(),
            dataObject.getErrorMessage()
        );
    }

    private ChatMessageDO toDataObject(ChatMessage message) {
        ChatMessageDO dataObject = new ChatMessageDO();
        dataObject.setId(message.getId());
        dataObject.setConversationId(message.getConversationId());
        dataObject.setRole(message.getRole().name());
        dataObject.setContent(message.getContent());
        dataObject.setStatus(message.getStatus().name());
        dataObject.setProvider(message.getProvider());
        dataObject.setModel(message.getModel());
        dataObject.setErrorMessage(message.getErrorMessage());
        dataObject.setCreatedAt(message.getCreatedAt());
        dataObject.setUpdatedAt(message.getUpdatedAt());
        return dataObject;
    }
}