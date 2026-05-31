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

    /**
     * 专家配置仓储，用于管理端分页、保存和逻辑删除专家。
     */
    private final ChatExpertRepository chatExpertRepository;

    /**
     * 分页返回专家列表。
     * @param current 当前页码（从 1 开始）。
     * @param size 每页条数。
     * @return 专家分页结果。
     */
    public PageResult<ChatExpert> pageExperts(int current, int size) {
        // 步骤 1：分页边界和排序由仓储层统一处理，服务层保持查询入口轻量。
        return chatExpertRepository.pageQuery(current, size);
    }

    /**
     * 创建专家。
     * @param request 请求对象。
     * @return 新增后的专家。
     */
    public ChatExpert create(ChatExpert request) {
        // 步骤 1：校验专家编码、展示名称和系统提示词必填。
        validateRequired(request);
        // 步骤 2：专家编码去首尾空格后做唯一性校验，避免运行时选择歧义。
        String normalizedExpertCode = request.getExpertCode().trim();
        if (chatExpertRepository.existsByExpertCode(normalizedExpertCode, null)) {
            throw new BusinessException("CHAT_EXPERT_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_EXPERT_DUPLICATE_CODE);
        }
        // 步骤 3：补齐新增专家默认启用、排序、审计和删除标记后保存。
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
        // 步骤 1：先读取旧专家配置，缺失时返回统一不存在错误。
        ChatExpert existing = chatExpertRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_EXPERT_NOT_FOUND);
        }
        // 步骤 2：校验更新请求和专家编码唯一性，允许当前记录复用自己的编码。
        validateRequired(request);
        String normalizedExpertCode = request.getExpertCode().trim();
        if (chatExpertRepository.existsByExpertCode(normalizedExpertCode, id)) {
            throw new BusinessException("CHAT_EXPERT_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_EXPERT_DUPLICATE_CODE);
        }
        // 步骤 3：空值字段沿用旧启用状态和排序，保留创建时间与删除标记。
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
        // 步骤 1：删除前确认专家存在，避免管理端误判删除成功。
        ChatExpert existing = chatExpertRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_EXPERT_NOT_FOUND);
        }
        // 步骤 2：执行逻辑删除，保留历史数据用于审计和旧任务引用兼容。
        chatExpertRepository.softDeleteById(id);
    }

    private void validateRequired(ChatExpert request) {
        // 步骤 1：请求体必须存在，避免后续字段读取空指针。
        if (request == null) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_REQUIRED);
        }
        // 步骤 2：专家编码是聊天运行时引用专家的稳定标识，不能为空。
        if (StrUtil.isBlank(request.getExpertCode())) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_CODE_REQUIRED);
        }
        // 步骤 3：展示名称用于管理端和用户侧呈现，不能为空。
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_NAME_REQUIRED);
        }
        // 步骤 4：系统提示词是专家生效的核心内容，不能为空。
        if (StrUtil.isBlank(request.getSystemPrompt())) {
            throw new BusinessException("CHAT_EXPERT_INVALID", ErrorMessageCatalog.CHAT_EXPERT_PROMPT_REQUIRED);
        }
    }
}
