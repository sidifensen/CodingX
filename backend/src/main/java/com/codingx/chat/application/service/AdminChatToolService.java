package com.codingx.chat.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatTool;
import com.codingx.chat.domain.repository.ChatToolRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端工具配置管理能力。
 */
@Service
@RequiredArgsConstructor
public class AdminChatToolService {

    private final ChatToolRepository chatToolRepository;

    /**
     * 查询全部工具配置。
     * @return 工具配置列表。
     */
    public List<ChatTool> listAll() {
        return chatToolRepository.findAll();
    }

    /**
     * 创建工具配置。
     * @param request 请求参数。
     * @return 新增后的工具配置。
     */
    public ChatTool create(ChatTool request) {
        validateRequired(request);
        String normalizedToolCode = request.getToolCode().trim();
        if (chatToolRepository.existsByToolCode(normalizedToolCode, null)) {
            throw new BusinessException("CHAT_TOOL_DUPLICATE_CODE", "工具编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatTool persisted = request.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .toolCode(normalizedToolCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), "codex-cli"))
            .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
            .sortNo(request.getSortNo() == null ? 0 : request.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatToolRepository.save(persisted);
        return persisted;
    }

    /**
     * 更新工具配置。
     * @param id 主键。
     * @param request 请求参数。
     * @return 更新后的工具配置。
     */
    public ChatTool update(Long id, ChatTool request) {
        ChatTool existing = chatToolRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("工具配置不存在");
        }
        validateRequired(request);
        String normalizedToolCode = request.getToolCode().trim();
        if (chatToolRepository.existsByToolCode(normalizedToolCode, id)) {
            throw new BusinessException("CHAT_TOOL_DUPLICATE_CODE", "工具编码已存在");
        }
        ChatTool persisted = request.toBuilder()
            .id(id)
            .toolCode(normalizedToolCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), existing.getSourceType()))
            .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
            .sortNo(request.getSortNo() == null ? existing.getSortNo() : request.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted())
            .build();
        chatToolRepository.save(persisted);
        return persisted;
    }

    /**
     * 删除工具配置。
     * @param id 主键。
     */
    public void delete(Long id) {
        ChatTool existing = chatToolRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException("工具配置不存在");
        }
        chatToolRepository.softDeleteById(id);
    }

    private void validateRequired(ChatTool request) {
        if (request == null) {
            throw new BusinessException("CHAT_TOOL_INVALID", "工具配置不能为空");
        }
        if (StrUtil.isBlank(request.getToolCode())) {
            throw new BusinessException("CHAT_TOOL_INVALID", "工具编码不能为空");
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_TOOL_INVALID", "工具名称不能为空");
        }
    }
}