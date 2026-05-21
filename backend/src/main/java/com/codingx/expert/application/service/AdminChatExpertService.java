package com.codingx.expert.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供聊天专家后台管理服务。
 */
@Service
@RequiredArgsConstructor
public class AdminChatExpertService {

    private final ChatExpertRepository chatExpertRepository;

    /**
     * 分页返回专家列表。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 专家分页结果。
     */
    public PageResult<ChatExpert> pageExperts(int current, int size) {
        return chatExpertRepository.pageQuery(current, size);
    }

    /**
     * 创建专家。
     * @param request 请求对象。
     * @return 新增后的专家。
     */
    public ChatExpert create(ChatExpert request) {
        validateRequired(request);
        String normalizedExpertCode = request.getExpertCode().trim();
        if (chatExpertRepository.existsByExpertCode(normalizedExpertCode, null)) {
            throw new BusinessException("CHAT_EXPERT_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_EXPERT_DUPLICATE_CODE);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatExpert persisted = request.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .expertCode(normalizedExpertCode)
            .displayName(request.getDisplayName().trim())
            .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
            .sortNo(request.getSortNo() == null ? 0 : request.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatExpertRepository.save(persisted);
        return persisted;
    }

    /**
     * 更新专家。
     * @param id 主键。
     * @param request 请求对象。
     * @return 更新后的专家。
     */
    public ChatExpert update(Long id, ChatExpert request) {
        ChatExpert existing = chatExpertRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_EXPERT_NOT_FOUND);
        }
        validateRequired(request);
        String normalizedExpertCode = request.getExpertCode().trim();
        if (chatExpertRepository.existsByExpertCode(normalizedExpertCode, id)) {
            throw new BusinessException("CHAT_EXPERT_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_EXPERT_DUPLICATE_CODE);
        }
        ChatExpert persisted = request.toBuilder()
            .id(id)
            .expertCode(normalizedExpertCode)
            .displayName(request.getDisplayName().trim())
            .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
            .sortNo(request.getSortNo() == null ? existing.getSortNo() : request.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted())
            .build();
        chatExpertRepository.save(persisted);
        return persisted;
    }

    /**
     * 删除专家。
     * @param id 主键。
     */
    public void delete(Long id) {
        ChatExpert existing = chatExpertRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_EXPERT_NOT_FOUND);
        }
        chatExpertRepository.softDeleteById(id);
    }

    private void validateRequired(ChatExpert request) {
        if (request == null) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_REQUIRED);
        }
        if (StrUtil.isBlank(request.getExpertCode())) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_CODE_REQUIRED);
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_NAME_REQUIRED);
        }
        if (StrUtil.isBlank(request.getSystemPrompt())) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_PROMPT_REQUIRED);
        }
    }
}
