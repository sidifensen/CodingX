package com.codingx.governance.infrastructure.persistence.repository;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.codingx.governance.domain.model.GovernanceSlashCommand;
import com.codingx.governance.domain.repository.GovernanceSlashCommandRepository;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceSlashCommandDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernanceSlashCommandMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Slash Command 仓储实现，负责用户端命令目录和管理端命令配置读写。
 */
@Repository
@RequiredArgsConstructor
public class GovernanceSlashCommandRepositoryImpl implements GovernanceSlashCommandRepository {

    /** Slash Command Mapper，用于读取启用命令和维护命令配置。 */
    private final GovernanceSlashCommandMapper mapper;

    @Override
    public List<GovernanceSlashCommand> findEnabledCommands() {
        return mapper.selectList(baseWrapper().eq(GovernanceSlashCommandDO::getEnabled, 1))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<GovernanceSlashCommand> findAll() {
        return mapper.selectList(baseWrapper()).stream().map(this::toDomain).toList();
    }

    @Override
    public GovernanceSlashCommand findByCommandCode(String commandCode) {
        String normalizedCommandCode = normalizeCommandCode(commandCode);
        if (StrUtil.isBlank(normalizedCommandCode)) {
            return null;
        }
        GovernanceSlashCommandDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernanceSlashCommandDO>()
            .eq(GovernanceSlashCommandDO::getCommandCode, normalizedCommandCode)
            .eq(GovernanceSlashCommandDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public GovernanceSlashCommand findById(Long id) {
        if (id == null) {
            return null;
        }
        GovernanceSlashCommandDO dataObject = mapper.selectOne(new LambdaQueryWrapper<GovernanceSlashCommandDO>()
            .eq(GovernanceSlashCommandDO::getId, id)
            .eq(GovernanceSlashCommandDO::getDeleted, 0)
            .last("limit 1"));
        return dataObject == null ? null : toDomain(dataObject);
    }

    @Override
    public void save(GovernanceSlashCommand command) {
        GovernanceSlashCommandDO dataObject = toDataObject(command);
        if (dataObject.getId() == null || mapper.selectById(dataObject.getId()) == null) {
            mapper.insert(dataObject);
            return;
        }
        mapper.updateById(dataObject);
    }

    @Override
    public void softDeleteById(Long id) {
        mapper.update(
            null,
            new LambdaUpdateWrapper<GovernanceSlashCommandDO>()
                .set(GovernanceSlashCommandDO::getDeleted, 1)
                .set(GovernanceSlashCommandDO::getUpdatedAt, LocalDateTime.now())
                .eq(GovernanceSlashCommandDO::getId, id)
        );
    }

    private LambdaQueryWrapper<GovernanceSlashCommandDO> baseWrapper() {
        return new LambdaQueryWrapper<GovernanceSlashCommandDO>()
            .eq(GovernanceSlashCommandDO::getDeleted, 0)
            .orderByAsc(GovernanceSlashCommandDO::getSortNo)
            .orderByAsc(GovernanceSlashCommandDO::getCommandCode);
    }

    private String normalizeCommandCode(String commandCode) {
        return StrUtil.removePrefix(StrUtil.trimToEmpty(commandCode), "/");
    }

    private GovernanceSlashCommandDO toDataObject(GovernanceSlashCommand command) {
        GovernanceSlashCommandDO dataObject = new GovernanceSlashCommandDO();
        dataObject.setId(command.getId());
        dataObject.setCommandCode(normalizeCommandCode(command.getCommandCode()));
        dataObject.setDisplayName(command.getDisplayName());
        dataObject.setDescription(command.getDescription());
        dataObject.setCommandType(command.getCommandType());
        dataObject.setPromptTemplate(command.getPromptTemplate());
        dataObject.setEnabled(command.getEnabled());
        dataObject.setSortNo(command.getSortNo());
        dataObject.setCreatedAt(command.getCreatedAt());
        dataObject.setUpdatedAt(command.getUpdatedAt());
        dataObject.setDeleted(command.getDeleted());
        return dataObject;
    }

    private GovernanceSlashCommand toDomain(GovernanceSlashCommandDO dataObject) {
        return GovernanceSlashCommand.builder()
            .id(dataObject.getId())
            .commandCode(dataObject.getCommandCode())
            .displayName(dataObject.getDisplayName())
            .description(dataObject.getDescription())
            .commandType(dataObject.getCommandType())
            .promptTemplate(dataObject.getPromptTemplate())
            .enabled(dataObject.getEnabled())
            .sortNo(dataObject.getSortNo())
            .createdAt(dataObject.getCreatedAt())
            .updatedAt(dataObject.getUpdatedAt())
            .deleted(dataObject.getDeleted())
            .build();
    }
}
