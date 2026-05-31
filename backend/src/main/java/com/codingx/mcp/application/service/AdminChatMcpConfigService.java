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

    /**
     * MCP 配置仓储，承接管理端配置的查询、保存和逻辑删除。
     */
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
        // 步骤 1：校验请求体、编码和展示名称，避免保存不可展示或不可调用的配置。
        validateRequired(request);
        String normalizedMcpCode = request.getMcpCode().trim();
        if (chatMcpRepository.existsByMcpCode(normalizedMcpCode, null)) {
            throw new BusinessException("CHAT_MCP_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_MCP_DUPLICATE_CODE);
        }
        // 步骤 2：补齐默认来源、启用状态、排序和审计字段后保存。
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
        // 步骤 1：先读取旧配置，缺失时返回统一不存在错误。
        ChatMcp existing = chatMcpRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_MCP_CONFIG_NOT_FOUND);
        }
        // 步骤 2：校验更新请求和编码唯一性，允许当前记录复用自己的编码。
        validateRequired(request);
        String normalizedMcpCode = request.getMcpCode().trim();
        if (chatMcpRepository.existsByMcpCode(normalizedMcpCode, id)) {
            throw new BusinessException("CHAT_MCP_DUPLICATE_CODE", ErrorMessageCatalog.CHAT_MCP_DUPLICATE_CODE);
        }
        // 步骤 3：空值字段沿用旧配置，避免局部编辑误清空来源、启用状态或排序。
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
        // 步骤 1：删除前确认配置存在，避免管理端误判删除成功。
        ChatMcp existing = chatMcpRepository.findById(id);
        if (existing == null) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_MCP_CONFIG_NOT_FOUND);
        }
        // 步骤 2：执行逻辑删除，保留历史配置用于审计和兼容。
        chatMcpRepository.softDeleteById(id);
    }

    private void validateRequired(ChatMcp request) {
        // 步骤 1：请求体必须存在，避免后续字段读取空指针。
        if (request == null) {
            throw new BusinessException("CHAT_MCP_INVALID", ErrorMessageCatalog.CHAT_MCP_CONFIG_REQUIRED);
        }
        // 步骤 2：MCP 编码是配置和执行器绑定的核心标识，不能为空。
        if (StrUtil.isBlank(request.getMcpCode())) {
            throw new BusinessException("CHAT_MCP_INVALID", ErrorMessageCatalog.CHAT_MCP_CODE_REQUIRED);
        }
        // 步骤 3：展示名称用于管理端和用户侧展示，不能为空。
        if (StrUtil.isBlank(request.getDisplayName())) {
            throw new BusinessException("CHAT_MCP_INVALID", ErrorMessageCatalog.CHAT_MCP_NAME_REQUIRED);
        }
    }
}
