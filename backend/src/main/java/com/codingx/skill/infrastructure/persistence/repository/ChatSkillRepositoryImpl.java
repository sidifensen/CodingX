package com.codingx.skill.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.domain.repository.ChatSkillRepository;
import com.codingx.skill.infrastructure.persistence.dataobject.ChatSkillDO;
import com.codingx.skill.infrastructure.persistence.mapper.ChatSkillMapper;
import com.codingx.chat.interfaces.response.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现聊天技能配置仓储的 MyBatis 持久化逻辑。
 */
@Repository
@RequiredArgsConstructor
public class ChatSkillRepositoryImpl implements ChatSkillRepository {

    /**
     * 技能表 Mapper，用于执行技能配置的查询、分页、新增、更新和删除。
     */
    private final ChatSkillMapper chatSkillMapper;

    /**
     * 查询全部未删除技能。
     * @return 按排序值和技能编码升序排列的技能列表。
     */
    @Override
    public List<ChatSkill> findAll() {
        // 步骤 1：复用基础列表条件过滤 deleted=0 并保持稳定排序。
        // 步骤 2：持久化对象只在仓储内部存在，对外返回领域对象。
        return chatSkillMapper.selectList(baseListWrapper())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 分页查询全部未删除技能。
     * @param current 当前页码。
     * @param size 每页条数。
     * @return 技能分页结果。
     */
    @Override
    public PageResult<ChatSkill> pageQuery(int current, int size) {
        // 步骤 1：页码和页大小兜底为至少 1，避免非法分页参数传入 MyBatis-Plus。
        Page<ChatSkillDO> page = chatSkillMapper.selectPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            baseListWrapper()
        );
        // 步骤 2：分页记录转换为领域对象，分页元数据按查询结果透传。
        return PageResult.<ChatSkill>builder()
            .records(page.getRecords().stream().map(this::toDomain).toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .build();
    }

    /**
     * 查询当前启用的未删除技能。
     * @return 启用技能列表。
     */
    @Override
    public List<ChatSkill> findAllEnabled() {
        // 步骤 1：在基础列表条件上追加 enabled=1，保留统一排序。
        return chatSkillMapper.selectList(baseListWrapper()
                .eq(ChatSkillDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    /**
     * 按主键查询技能。
     * @param id 技能主键。
     * @return 技能领域对象，未找到时返回 null。
     */
    @Override
    public ChatSkill findById(Long id) {
        // 步骤 1：空主键直接返回 null，由应用服务决定业务异常语义。
        if (id == null) {
            return null;
        }
        // 步骤 2：只读取未删除记录，并用 limit 1 约束单行结果。
        ChatSkillDO dataObject = chatSkillMapper.selectOne(new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getId, id)
            .eq(ChatSkillDO::getDeleted, 0)
            .last("limit 1"));
        // 步骤 3：查询命中后转换为领域对象。
        return dataObject == null ? null : toDomain(dataObject);
    }

    /**
     * 按技能编码查询技能。
     * @param skillCode 技能编码。
     * @return 技能领域对象，未找到时返回 null。
     */
    @Override
    public ChatSkill findBySkillCode(String skillCode) {
        // 步骤 1：空技能编码不参与查询，避免误命中脏数据。
        if (StrUtil.isBlank(skillCode)) {
            return null;
        }
        // 步骤 2：技能编码按去空白值匹配未删除记录。
        ChatSkillDO dataObject = chatSkillMapper.selectOne(new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getSkillCode, skillCode.trim())
            .eq(ChatSkillDO::getDeleted, 0)
            .last("limit 1"));
        // 步骤 3：查询命中后转换为领域对象。
        return dataObject == null ? null : toDomain(dataObject);
    }

    /**
     * 判断技能编码是否已存在。
     * @param skillCode 技能编码。
     * @param excludedId 更新场景需要排除的技能主键，可为空。
     * @return true 表示存在其他同编码技能。
     */
    @Override
    public boolean existsBySkillCode(String skillCode, Long excludedId) {
        // 步骤 1：空编码不做唯一性查询，由上层必填校验负责报错。
        if (StrUtil.isBlank(skillCode)) {
            return false;
        }
        // 步骤 2：只统计未删除记录，更新场景排除当前技能主键。
        LambdaQueryWrapper<ChatSkillDO> wrapper = new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getSkillCode, skillCode.trim())
            .eq(ChatSkillDO::getDeleted, 0)
            .ne(excludedId != null, ChatSkillDO::getId, excludedId);
        return chatSkillMapper.selectCount(wrapper) > 0;
    }

    /**
     * 保存技能配置。
     * @param skill 技能领域对象。
     */
    @Override
    public void save(ChatSkill skill) {
        // 步骤 1：把领域对象转换为数据库行对象，隔离表字段细节。
        ChatSkillDO dataObject = toDataObject(skill);
        // 步骤 2：按主键是否存在决定插入或更新。
        if (chatSkillMapper.selectById(skill.getId()) == null) {
            chatSkillMapper.insert(dataObject);
            return;
        }
        chatSkillMapper.updateById(dataObject);
    }

    /**
     * 逻辑删除技能配置。
     * @param id 技能主键。
     */
    @Override
    public void softDeleteById(Long id) {
        // 步骤 1：只更新 deleted 和 updatedAt，保留技能历史数据。
        chatSkillMapper.update(
            null,
            new LambdaUpdateWrapper<ChatSkillDO>()
                .set(ChatSkillDO::getDeleted, 1)
                .set(ChatSkillDO::getUpdatedAt, LocalDateTime.now())
                .eq(ChatSkillDO::getId, id)
        );
    }

    /**
     * 物理删除技能配置。
     * @param id 技能主键。
     */
    @Override
    public void deleteById(Long id) {
        // 步骤 1：上传技能删除时需要移除数据库记录，因此这里直接按主键物理删除。
        chatSkillMapper.deleteById(id);
    }

    @Override
    public List<ChatSkill> findByTaskId(Long taskId) {
        // task_skill 已迁移到 chat_execution_step；保留旧接口为空实现，避免误访问已删除表。
        return List.of();
    }

    @Override
    public void bindTaskSkills(Long taskId, List<String> skillCodes) {
        // task_skill 已删除，新运行上下文统一由 ChatRunContextStepSupport 写入 chat_execution_step。
    }

    private LambdaQueryWrapper<ChatSkillDO> baseListWrapper() {
        // 步骤 1：所有列表类查询统一过滤未删除记录并按 sortNo、skillCode 稳定排序。
        return new LambdaQueryWrapper<ChatSkillDO>()
            .eq(ChatSkillDO::getDeleted, 0)
            .orderByAsc(ChatSkillDO::getSortNo)
            .orderByAsc(ChatSkillDO::getSkillCode);
    }

    private ChatSkillDO toDataObject(ChatSkill skill) {
        // 步骤 1：领域对象字段逐一映射到 skill 表字段。
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
        dataObject.setPackageStorageFormat(skill.getPackageStorageFormat());
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
        // 步骤 1：持久化字段逐一映射到领域对象，避免服务层依赖 DO。
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
            .packageStorageFormat(dataObject.getPackageStorageFormat())
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

