package com.codingx.mcp.infrastructure.persistence.repository;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.mcp.domain.model.ChatMcp;
import com.codingx.mcp.domain.repository.ChatMcpRepository;
import com.codingx.mcp.infrastructure.persistence.dataobject.ChatMcpDO;
import com.codingx.mcp.infrastructure.persistence.dataobject.TaskMcpDO;
import com.codingx.mcp.infrastructure.persistence.mapper.ChatMcpMapper;
import com.codingx.mcp.infrastructure.persistence.mapper.TaskMcpMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 聊天 MCP 配置仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class ChatMcpRepositoryImpl implements ChatMcpRepository {

    private final ChatMcpMapper chatMcpMapper;
    private final TaskMcpMapper taskMcpMapper;

    @Override
    public List<ChatMcp> findAll() {
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
        if (id == null) {
            return null;
        }
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
        ChatMcpDO dataObject = toDataObject(chatMcp);
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
        if (taskId == null) {
            return List.of();
        }
        List<String> mcpCodes = taskMcpMapper.selectList(new LambdaQueryWrapper<TaskMcpDO>()
                .eq(TaskMcpDO::getTaskId, taskId)
                .orderByAsc(TaskMcpDO::getId))
            .stream()
            .map(TaskMcpDO::getMcpCode)
            .filter(StrUtil::isNotBlank)
            .toList();
        if (mcpCodes.isEmpty()) {
            return List.of();
        }
        return chatMcpMapper.selectList(new LambdaQueryWrapper<ChatMcpDO>()
                .in(ChatMcpDO::getMcpCode, mcpCodes)
                .eq(ChatMcpDO::getDeleted, 0)
                .orderByAsc(ChatMcpDO::getSortNo)
                .orderByAsc(ChatMcpDO::getMcpCode))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void bindTaskMcps(Long taskId, List<String> mcpCodes) {
        if (taskId == null) {
            return;
        }
        taskMcpMapper.delete(new LambdaQueryWrapper<TaskMcpDO>().eq(TaskMcpDO::getTaskId, taskId));
        if (mcpCodes == null || mcpCodes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LinkedHashSet<String> uniqueCodes = new LinkedHashSet<>();
        for (String mcpCode : mcpCodes) {
            if (StrUtil.isNotBlank(mcpCode)) {
                uniqueCodes.add(mcpCode.trim());
            }
        }
        for (String mcpCode : uniqueCodes) {
            TaskMcpDO dataObject = new TaskMcpDO();
            dataObject.setId(IdUtil.getSnowflakeNextId());
            dataObject.setTaskId(taskId);
            dataObject.setMcpCode(mcpCode);
            dataObject.setCreatedAt(now);
            taskMcpMapper.insert(dataObject);
        }
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
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
