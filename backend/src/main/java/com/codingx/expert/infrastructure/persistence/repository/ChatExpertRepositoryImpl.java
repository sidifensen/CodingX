package com.codingx.expert.infrastructure.persistence.repository;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.expert.domain.model.ChatExpert;
import com.codingx.expert.domain.repository.ChatExpertRepository;
import com.codingx.expert.infrastructure.persistence.dataobject.ChatExpertDO;
import com.codingx.expert.infrastructure.persistence.dataobject.TaskExpertDO;
import com.codingx.expert.infrastructure.persistence.mapper.ChatExpertMapper;
import com.codingx.expert.infrastructure.persistence.mapper.TaskExpertMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天专家配置仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatExpertRepositoryImpl implements ChatExpertRepository {

    private final ChatExpertMapper chatExpertMapper;
    private final TaskExpertMapper taskExpertMapper;

    @Override
    public List<ChatExpert> findAll() {
        return chatExpertMapper.selectList(baseListWrapper())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public PageResult<ChatExpert> pageQuery(int current, int size) {
        Page<ChatExpertDO> page = chatExpertMapper.selectPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            baseListWrapper()
        );
        return PageResult.<ChatExpert>builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .build();
    }

    @Override
    public List<ChatExpert> findAllEnabled() {
        return chatExpertMapper.selectList(baseListWrapper().eq(ChatExpertDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public ChatExpert findById(Long id) {
        if (id == null) {
            return null;
        }
        ChatExpertDO dataObject = chatExpertMapper.selectOne(new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getId, id)
            .eq(ChatExpertDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public ChatExpert findByExpertCode(String expertCode) {
        if (StrUtil.isBlank(expertCode)) {
            return null;
        }
        ChatExpertDO dataObject = chatExpertMapper.selectOne(new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getExpertCode, expertCode.trim())
            .eq(ChatExpertDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public boolean existsByExpertCode(String expertCode, Long excludedId) {
        if (StrUtil.isBlank(expertCode)) {
            return false;
        }
        LambdaQueryWrapper<ChatExpertDO> wrapper = new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getExpertCode, expertCode.trim())
            .eq(ChatExpertDO::getDeleted, 0)
            .ne(excludedId != null, ChatExpertDO::getId, excludedId);
        return chatExpertMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void save(ChatExpert expert) {
        ChatExpertDO dataObject = toDataObject(expert);
        if (chatExpertMapper.selectById(expert.getId()) == null) {
            chatExpertMapper.insert(dataObject);
            return;
        }
        chatExpertMapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        chatExpertMapper.update(
            null,
            new LambdaUpdateWrapper<ChatExpertDO>()
                .set(ChatExpertDO::getDeleted, 1)
                .set(ChatExpertDO::getUpdatedAt, LocalDateTime.now())
                .eq(ChatExpertDO::getId, id)
        );
    }

    @Override
    public List<ChatExpert> findByTaskId(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        List<String> expertCodes = taskExpertMapper.selectList(new LambdaQueryWrapper<TaskExpertDO>()
                .eq(TaskExpertDO::getTaskId, taskId)
                .orderByAsc(TaskExpertDO::getId))
            .stream()
            .map(TaskExpertDO::getExpertCode)
            .filter(StrUtil::isNotBlank)
            .toList();
        if (expertCodes.isEmpty()) {
            return List.of();
        }
        return chatExpertMapper.selectList(new LambdaQueryWrapper<ChatExpertDO>()
                .in(ChatExpertDO::getExpertCode, expertCodes)
                .eq(ChatExpertDO::getDeleted, 0)
                .orderByAsc(ChatExpertDO::getSortNo)
                .orderByAsc(ChatExpertDO::getExpertCode))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void bindTaskExpert(Long taskId, String expertCode) {
        if (taskId == null) {
            return;
        }
        taskExpertMapper.delete(new LambdaQueryWrapper<TaskExpertDO>()
            .eq(TaskExpertDO::getTaskId, taskId));
        if (StrUtil.isBlank(expertCode)) {
            return;
        }
        TaskExpertDO dataObject = new TaskExpertDO();
        dataObject.setId(IdUtil.getSnowflakeNextId());
        dataObject.setTaskId(taskId);
        dataObject.setExpertCode(expertCode.trim());
        dataObject.setCreatedAt(LocalDateTime.now());
        taskExpertMapper.insert(dataObject);
    }

    private LambdaQueryWrapper<ChatExpertDO> baseListWrapper() {
        return new LambdaQueryWrapper<ChatExpertDO>()
            .eq(ChatExpertDO::getDeleted, 0)
            .orderByAsc(ChatExpertDO::getSortNo)
            .orderByAsc(ChatExpertDO::getExpertCode);
    }

    private ChatExpertDO toDataObject(ChatExpert expert) {
        ChatExpertDO dataObject = new ChatExpertDO();
        dataObject.setId(expert.getId());
        dataObject.setExpertCode(expert.getExpertCode());
        dataObject.setDisplayName(expert.getDisplayName());
        dataObject.setDescription(expert.getDescription());
        dataObject.setCategory(expert.getCategory());
        dataObject.setTagsJson(expert.getTagsJson());
        dataObject.setAvatarUrl(expert.getAvatarUrl());
        dataObject.setPresetQuestion(expert.getPresetQuestion());
        dataObject.setSystemPrompt(expert.getSystemPrompt());
        dataObject.setEnabled(expert.getEnabled());
        dataObject.setSortNo(expert.getSortNo());
        dataObject.setCreatedAt(expert.getCreatedAt());
        dataObject.setUpdatedAt(expert.getUpdatedAt());
        dataObject.setDeleted(expert.getDeleted());
        return dataObject;
    }

    private ChatExpert toDomain(ChatExpertDO dataObject) {
        return ChatExpert.builder()
            .id(dataObject.getId())
            .expertCode(dataObject.getExpertCode())
            .displayName(dataObject.getDisplayName())
            .description(dataObject.getDescription())
            .category(dataObject.getCategory())
            .tagsJson(dataObject.getTagsJson())
            .avatarUrl(dataObject.getAvatarUrl())
            .presetQuestion(dataObject.getPresetQuestion())
            .systemPrompt(dataObject.getSystemPrompt())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
