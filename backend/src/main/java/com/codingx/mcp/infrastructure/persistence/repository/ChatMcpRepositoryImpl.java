package com.codingx.mcp.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.mcp.infrastructure.persistence.dataobject.ChatMcpDO;
import com.codingx.mcp.infrastructure.persistence.mapper.ChatMcpMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 聊天 MCP 配置仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class ChatMcpRepositoryImpl implements ChatMcpRepository {

    /**
     * MCP MyBatis Mapper，负责访问 mcp 配置表。
     */
    private final ChatMcpMapper chatMcpMapper;

    @Override
    public List<ChatMcp> findAll() {
        // 步骤 1：复用基础列表条件，只读取未删除配置。
        // 步骤 2：逐条转换为领域对象，available 运行态字段由查询服务补齐。
        return chatMcpMapper.selectList(baseListWrapper())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ChatMcp> findAllEnabled() {
        return chatMcpMapper.selectList(baseListWrapper().eq(ChatMcpDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public ChatMcp findById(Long id) {
        // 步骤 1：空主键直接返回 null，交由应用服务转换为业务异常。
        if (id == null) {
            return null;
        }
        // 步骤 2：按主键和未删除条件查单条配置，避免读到逻辑删除数据。
        ChatMcpDO dataObject = chatMcpMapper.selectOne(new LambdaQueryWrapper<ChatMcpDO>()
            .eq(ChatMcpDO::getId, id)
            .eq(ChatMcpDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public ChatMcp findByMcpCode(String mcpCode) {
        if (StrUtil.isBlank(mcpCode)) {
            return null;
        }
        ChatMcpDO dataObject = chatMcpMapper.selectOne(new LambdaQueryWrapper<ChatMcpDO>()
            .eq(ChatMcpDO::getMcpCode, mcpCode.trim())
            .eq(ChatMcpDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public boolean existsByMcpCode(String mcpCode, Long excludedId) {
        if (StrUtil.isBlank(mcpCode)) {
            return false;
        }
        LambdaQueryWrapper<ChatMcpDO> queryWrapper = new LambdaQueryWrapper<ChatMcpDO>()
            .eq(ChatMcpDO::getMcpCode, mcpCode.trim())
            .eq(ChatMcpDO::getDeleted, 0)
            .ne(excludedId != null, ChatMcpDO::getId, excludedId);
        return chatMcpMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public void save(ChatMcp chatMcp) {
        // 步骤 1：领域对象先映射为数据对象，保持表字段与领域字段隔离。
        ChatMcpDO dataObject = toDataObject(chatMcp);
        // 步骤 2：数据库不存在时插入，已存在时按主键覆盖更新。
        if (chatMcpMapper.selectById(chatMcp.getId()) == null) {
            chatMcpMapper.insert(dataObject);
            return;
        }
        chatMcpMapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        chatMcpMapper.update(
            null,
            new LambdaUpdateWrapper<ChatMcpDO>()
                .set(ChatMcpDO::getDeleted, 1)
                .set(ChatMcpDO::getUpdatedAt, LocalDateTime.now())
                .eq(ChatMcpDO::getId, id)
        );
    }

    @Override
    public List<ChatMcp> findByTaskId(Long taskId) {
        // task_mcp 已迁移到 chat_execution_step；保留旧接口为空实现，避免误访问已删除表。
        return List.of();
    }

    @Override
    public void bindTaskMcps(Long taskId, List<String> mcpCodes) {
        // task_mcp 已删除，新运行上下文统一由 ChatRunContextStepSupport 写入 chat_execution_step。
    }

    private LambdaQueryWrapper<ChatMcpDO> baseListWrapper() {
        return new LambdaQueryWrapper<ChatMcpDO>()
            .eq(ChatMcpDO::getDeleted, 0)
            .orderByAsc(ChatMcpDO::getSortNo)
            .orderByAsc(ChatMcpDO::getMcpCode);
    }

    private ChatMcpDO toDataObject(ChatMcp chatMcp) {
        ChatMcpDO dataObject = new ChatMcpDO();
        dataObject.setId(chatMcp.getId());
        dataObject.setMcpCode(chatMcp.getMcpCode());
        dataObject.setDisplayName(chatMcp.getDisplayName());
        dataObject.setDescription(chatMcp.getDescription());
        dataObject.setCategory(chatMcp.getCategory());
        dataObject.setSourceType(chatMcp.getSourceType());
        dataObject.setTransportType(chatMcp.getTransportType());
        dataObject.setCommand(chatMcp.getCommand());
        dataObject.setArgsJson(chatMcp.getArgsJson());
        dataObject.setEnvJson(chatMcp.getEnvJson());
        dataObject.setEndpointUrl(chatMcp.getEndpointUrl());
        dataObject.setHeadersJson(chatMcp.getHeadersJson());
        dataObject.setToolSchemaJson(chatMcp.getToolSchemaJson());
        dataObject.setHealthStatus(chatMcp.getHealthStatus());
        dataObject.setLastConnectedAt(chatMcp.getLastConnectedAt());
        dataObject.setLastErrorMessage(chatMcp.getLastErrorMessage());
        dataObject.setEnabled(chatMcp.getEnabled());
        dataObject.setSortNo(chatMcp.getSortNo());
        dataObject.setCreatedAt(chatMcp.getCreatedAt());
        dataObject.setUpdatedAt(chatMcp.getUpdatedAt());
        dataObject.setDeleted(chatMcp.getDeleted());
        return dataObject;
    }

    private ChatMcp toDomain(ChatMcpDO dataObject) {
        return ChatMcp.builder()
            .id(dataObject.getId())
            .mcpCode(dataObject.getMcpCode())
            .displayName(dataObject.getDisplayName())
            .description(dataObject.getDescription())
            .category(dataObject.getCategory())
            .sourceType(dataObject.getSourceType())
            .transportType(dataObject.getTransportType())
            .command(dataObject.getCommand())
            .argsJson(dataObject.getArgsJson())
            .envJson(dataObject.getEnvJson())
            .endpointUrl(dataObject.getEndpointUrl())
            .headersJson(dataObject.getHeadersJson())
            .toolSchemaJson(dataObject.getToolSchemaJson())
            .healthStatus(dataObject.getHealthStatus())
            .lastConnectedAt(dataObject.getLastConnectedAt())
            .lastErrorMessage(dataObject.getLastErrorMessage())
            .enabled(dataObject.getEnabled())
            // 运行态可用性由查询服务基于执行器注册动态补充，仓储层默认置空。
            .available(null)
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
