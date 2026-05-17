package com.codingx.chat.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.chat.domain.repository.ChatSkillRepository;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatSkillDO;
import com.codingx.chat.infrastructure.persistence.dataobject.TaskSkillDO;
import com.codingx.chat.infrastructure.persistence.mapper.ChatSkillMapper;
import com.codingx.chat.infrastructure.persistence.mapper.TaskSkillMapper;
import com.codingx.chat.interfaces.response.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天技能配置仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatSkillRepositoryImpl implements ChatSkillRepository {

    private final ChatSkillMapper chatSkillMapper;
    private final TaskSkillMapper taskSkillMapper;

    @Override
    public List<ChatSkill> findAll() {
        return chatSkillMapper.selectList(baseListWrapper())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public PageResult<ChatSkill> pageQuery(int current, int size) {
        Page<ChatSkillDO> page = chatSkillMapper.selectPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            baseListWrapper()
        );
        return PageResult.<ChatSkill>builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .build();
    }

    @Override
    public List<ChatSkill> findAllEnabled() {
        return chatSkillMapper.selectList(baseListWrapper()
                .eq(ChatSkillDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public ChatSkill findById(Long id) {
        if (id == null) {
            return null;
        }
        ChatSkillDO dataObject = chatSkillMapper.selectOne(new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getId, id)
            .eq(ChatSkillDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public ChatSkill findBySkillCode(String skillCode) {
        if (StrUtil.isBlank(skillCode)) {
            return null;
        }
        ChatSkillDO dataObject = chatSkillMapper.selectOne(new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getSkillCode, skillCode.trim())
            .eq(ChatSkillDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public boolean existsBySkillCode(String skillCode, Long excludedId) {
        if (StrUtil.isBlank(skillCode)) {
            return false;
        }
        LambdaQueryWrapper<ChatSkillDO> wrapper = new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getSkillCode, skillCode.trim())
            .eq(ChatSkillDO::getDeleted, 0)
            .ne(excludedId != null, ChatSkillDO::getId, excludedId);
        return chatSkillMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void save(ChatSkill skill) {
        ChatSkillDO dataObject = toDataObject(skill);
        if (chatSkillMapper.selectById(skill.getId()) == null) {
            chatSkillMapper.insert(dataObject);
            return;
        }
        chatSkillMapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        chatSkillMapper.update(
            null,
            new LambdaUpdateWrapper<ChatSkillDO>()
                .set(ChatSkillDO::getDeleted, 1)
                .set(ChatSkillDO::getUpdatedAt, LocalDateTime.now())
                .eq(ChatSkillDO::getId, id)
        );
    }

    @Override
    public List<ChatSkill> findByTaskId(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        List<String> skillCodes = taskSkillMapper.selectList(new LambdaQueryWrapper<TaskSkillDO>()
                .eq(TaskSkillDO::getTaskId, taskId)
                .orderByAsc(TaskSkillDO::getId))
            .stream()
            .map(TaskSkillDO::getSkillCode)
            .filter(StrUtil::isNotBlank)
            .toList();
        if (skillCodes.isEmpty()) {
            return List.of();
        }
        return chatSkillMapper.selectList(new LambdaQueryWrapper<ChatSkillDO>()
                .in(ChatSkillDO::getSkillCode, skillCodes)
                .eq(ChatSkillDO::getDeleted, 0)
                .orderByAsc(ChatSkillDO::getSortNo)
                .orderByAsc(ChatSkillDO::getSkillCode))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public void bindTaskSkills(Long taskId, List<String> skillCodes) {
        if (taskId == null) {
            return;
        }
        taskSkillMapper.delete(new LambdaQueryWrapper<TaskSkillDO>()
            .eq(TaskSkillDO::getTaskId, taskId));
        if (skillCodes == null || skillCodes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LinkedHashSet<String> uniqueCodes = new LinkedHashSet<>();
        for (String skillCode : skillCodes) {
            if (StrUtil.isNotBlank(skillCode)) {
                uniqueCodes.add(skillCode.trim());
            }
        }
        for (String skillCode : uniqueCodes) {
            TaskSkillDO dataObject = new TaskSkillDO();
            dataObject.setId(IdUtil.getSnowflakeNextId());
            dataObject.setTaskId(taskId);
            dataObject.setSkillCode(skillCode);
            dataObject.setCreatedAt(now);
            taskSkillMapper.insert(dataObject);
        }
    }

    private LambdaQueryWrapper<ChatSkillDO> baseListWrapper() {
        return new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getDeleted, 0)
            .orderByAsc(ChatSkillDO::getSortNo)
            .orderByAsc(ChatSkillDO::getSkillCode);
    }

    private ChatSkillDO toDataObject(ChatSkill skill) {
        ChatSkillDO dataObject = new ChatSkillDO();
        dataObject.setId(skill.getId());
        dataObject.setSkillCode(skill.getSkillCode());
        dataObject.setDisplayName(skill.getDisplayName());
        dataObject.setDescription(skill.getDescription());
        dataObject.setCategory(skill.getCategory());
        dataObject.setSourceType(skill.getSourceType());
        dataObject.setEnabled(skill.getEnabled());
        dataObject.setSortNo(skill.getSortNo());
        dataObject.setStorageKey(skill.getStorageKey());
        dataObject.setPackageFileName(skill.getPackageFileName());
        dataObject.setPackageSize(skill.getPackageSize());
        dataObject.setPackageChecksum(skill.getPackageChecksum());
        dataObject.setUploadedBy(skill.getUploadedBy());
        dataObject.setUploadedAt(skill.getUploadedAt());
        dataObject.setCreatedAt(skill.getCreatedAt());
        dataObject.setUpdatedAt(skill.getUpdatedAt());
        dataObject.setDeleted(skill.getDeleted());
        return dataObject;
    }

    private ChatSkill toDomain(ChatSkillDO dataObject) {
        return ChatSkill.builder()
            .id(dataObject.getId())
            .skillCode(dataObject.getSkillCode())
            .displayName(dataObject.getDisplayName())
            .description(dataObject.getDescription())
            .category(dataObject.getCategory())
            .sourceType(dataObject.getSourceType())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .storageKey(dataObject.getStorageKey())
            .packageFileName(dataObject.getPackageFileName())
            .packageSize(dataObject.getPackageSize())
            .packageChecksum(dataObject.getPackageChecksum())
            .uploadedBy(dataObject.getUploadedBy())
            .uploadedAt(dataObject.getUploadedAt())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
