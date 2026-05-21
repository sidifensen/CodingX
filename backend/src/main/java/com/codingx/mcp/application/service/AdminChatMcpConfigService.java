package com.codingx.mcp.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 提供管理端 MCP 配置管理能力。
 */
@Service
@RequiredArgsConstructor
public class AdminChatMcpConfigService {

    private final ChatMcpRepository chatMcpRepository;

    /**
     * 查询全部 MCP 配置。
     * @return MCP 列表。
     */
    public List<ChatMcp> listAll() {
        return chatMcpRepository.findAll();
    }

    /**
     * 创建 MCP 配置。
     * @param request 请求参数。
     * @return 新增后的 MCP。
     */
    public ChatMcp create(ChatMcp request) {
        validateRequired(request);
        String normalizedMcpCode = request.getMcpCode().trim();
        if (chatMcpRepository.existsByMcpCode(normalizedMcpCode, null)) {
            throw new BusinessException("CHAT_MCP_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_MCP_DUPLICATE_CODE);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatMcp persisted = request.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .mcpCode(normalizedMcpCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), "built-in"))
            .enabled(request.getEnabled() == null ? 1 : request.getEnabled())
            .sortNo(request.getSortNo() == null ? 0 : request.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        chatMcpRepository.save(persisted);
        return persisted;
    }

    /**
     * 更新 MCP 配置。
     * @param id 主键。
     * @param request 请求参数。
     * @return 更新后的 MCP。
     */
    public ChatMcp update(Long id, ChatMcp request) {
        ChatMcp existing = chatMcpRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_MCP_CONFIG_NOT_FOUND);
        }
        validateRequired(request);
        String normalizedMcpCode = request.getMcpCode().trim();
        if (chatMcpRepository.existsByMcpCode(normalizedMcpCode, id)) {
            throw new BusinessException("CHAT_MCP_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_MCP_DUPLICATE_CODE);
        }
        ChatMcp persisted = request.toBuilder()
            .id(id)
            .mcpCode(normalizedMcpCode)
            .displayName(request.getDisplayName().trim())
            .sourceType(StrUtil.blankToDefault(request.getSourceType(), existing.getSourceType()))
            .enabled(request.getEnabled() == null ? existing.getEnabled() : request.getEnabled())
            .sortNo(request.getSortNo() == null ? existing.getSortNo() : request.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(existing.getDeleted())
            .build();
        chatMcpRepository.save(persisted);
        return persisted;
    }

    /**
     * 删除 MCP 配置。
     * @param id 主键。
     */
    public void delete(Long id) {
        ChatMcp existing = chatMcpRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_MCP_CONFIG_NOT_FOUND);
        }
        chatMcpRepository.softDeleteById(id);
    }

    private void validateRequired(ChatMcp request) {
        if (request == null) {
            throw new BusinessException("CHAT_MCP_INVALID", ErrorMessageCatalog.CHAT_MCP_CONFIG_REQUIRED);
        }
        if (StrUtil.isBlank(request.getMcpCode())) {
            throw new BusinessException("CHAT_MCP_INVALID", ErrorMessageCatalog.CHAT_MCP_CODE_REQUIRED);
        }
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_MCP_INVALID", ErrorMessageCatalog.CHAT_MCP_NAME_REQUIRED);
        }
    }
}
