package com.codingx.chat.application.service;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.command.CreateConversationCommand;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.domain.repository.ChatMessageRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责协调 ChatConversationApplicationService 的应用流程，串联领域对象与基础设施服务。
 */
@Service
@RequiredArgsConstructor
public class ChatConversationApplicationService {

    /**
     * ChatConversationRepository 依赖。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * ChatMessageRepository 依赖。
     */
    private final ChatMessageRepository chatMessageRepository;
    /**
     * WorkspaceRepository 依赖。
     */
    private final WorkspaceRepository workspaceRepository;
    /**
     * WorkspaceRepositoryImpl 依赖，负责默认云端空间与用户归属校验。
     */
    private final WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /**
     * 创建 createConversation 所需数据并返回结果。
     * @param command 输入参数。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public ChatConversation createConversation(CreateConversationCommand command, Long userId) {
        Long actualWorkspaceId = resolveWorkspaceId(command.workspaceId(), userId);
        String title = StrUtil.blankToDefault(command.title(), ErrorMessageCatalog.CHAT_CONVERSATION_DEFAULT_TITLE);
        ChatConversation conversation = ChatConversation.create(
            IdUtil.getSnowflakeNextId(),
            title,
            userId,
            actualWorkspaceId,
            ChatConversationStatus.ACTIVE
        );
        chatConversationRepository.save(conversation);
        return conversation;
    }

    /**
     * 返回 listConversations 需要的结果集合。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public List<ChatConversation> listConversations(Long userId, Long workspaceId) {
        if (workspaceId != null) {
            workspaceRepositoryImpl.requireOwnedWorkspace(workspaceId, userId);
            return chatConversationRepository.findByCreatedByAndWorkspaceId(userId, workspaceId);
        }
        // 默认云端历史页只应展示“默认云端空间 + 历史遗留未归属记录”，避免本地工作空间会话串进 Web 历史。
        List<ChatConversation> conversations = new ArrayList<>();
        workspaceRepositoryImpl.findDefaultCloudWorkspaceByUserId(userId)
            .ifPresent(workspace -> conversations.addAll(chatConversationRepository.findByCreatedByAndWorkspaceId(userId, workspace.getId())));
        conversations.addAll(chatConversationRepository.findByCreatedByAndWorkspaceId(userId, null));
        return conversations.stream()
            .sorted(
                Comparator.comparing(ChatConversation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ChatConversation::getId, Comparator.nullsLast(Comparator.reverseOrder()))
            )
            .toList();
    }

    /**
     * 返回 listMessages 需要的结果集合。
     * @param conversationId 输入参数。
     * @param userId 输入参数。
     * @return 输入参数。
     */
    public List<ChatMessage> listMessages(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        return chatMessageRepository.findByConversationId(conversationId);
    }

    /**
     * 更新指定会话的标题，用于自动生成标题后的持久化。
     * @param conversationId 会话标识。
     * @param title 新标题。
     * @param userId 当前用户标识。
     */
    public void updateConversationTitle(Long conversationId, String title, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        conversation.rename(title);
        chatConversationRepository.save(conversation);
    }

    /**
     * 删除指定会话，供前端历史列表菜单触发逻辑删除。
     * @param conversationId 会话标识。
     * @param userId 当前用户标识。
     */
    public void deleteConversation(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!conversation.getCreatedBy().equals(userId)) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
        chatConversationRepository.deleteById(conversationId);
    }

    /**
     * 解析会话归属工作空间：未显式指定时自动绑定默认云端空间，显式指定时校验用户归属。
     * @param requestedWorkspaceId 请求中传入的工作空间标识。
     * @param userId 当前用户标识。
     * @return 会话最终归属的工作空间标识。
     */
    private Long resolveWorkspaceId(Long requestedWorkspaceId, Long userId) {
        if (requestedWorkspaceId != null) {
            WorkspaceDO workspace = workspaceRepositoryImpl.requireOwnedWorkspace(requestedWorkspaceId, userId);
            return workspace.getId();
        }
        WorkspaceDO cloudWorkspace = workspaceRepositoryImpl.ensureDefaultCloudWorkspace(userId, null);
        return cloudWorkspace.getId();
    }
}
