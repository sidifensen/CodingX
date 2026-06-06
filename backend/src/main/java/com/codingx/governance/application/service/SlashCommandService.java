package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernanceSlashCommand;
import com.codingx.governance.domain.repository.GovernanceSlashCommandRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Slash Command 应用服务，负责用户端命令目录查询和内置命令上下文生成。
 */
@Service
@RequiredArgsConstructor
public class SlashCommandService {

    /** Slash Command 仓储，用于读取启用命令并维护管理端命令配置。 */
    private final GovernanceSlashCommandRepository commandRepository;

    /**
     * 查询用户端可见的启用命令。
     * @return 启用命令列表。
     */
    public List<GovernanceSlashCommand> listEnabledCommands() {
        return commandRepository.findEnabledCommands();
    }

    /**
     * 查询全部命令配置。
     * @return 未删除命令列表。
     */
    public List<GovernanceSlashCommand> listAllCommands() {
        return commandRepository.findAll();
    }

    /**
     * 根据内置命令生成模型可消费的执行上下文。
     * @param commandCode 命令编码，可带或不带前导斜杠。
     * @param userContent 用户补充正文。
     * @return 注入后的正文。
     */
    public String buildBuiltinCommandContent(String commandCode, String userContent) {
        // 步骤 1：命令必须存在且启用，否则通过统一异常返回中文 ApiResponse.message。
        GovernanceSlashCommand command = commandRepository.findByCommandCode(commandCode);
        if (command == null || command.getEnabled() == null || command.getEnabled() != 1) {
            throw new BusinessException("GOVERNANCE_SLASH_COMMAND_UNAVAILABLE", "命令不可用");
        }
        if (!"BUILTIN".equalsIgnoreCase(command.getCommandType())) {
            throw new BusinessException("GOVERNANCE_SLASH_COMMAND_UNAVAILABLE", "命令不可用");
        }
        // 步骤 2：模板作为系统化意图前缀，用户正文保留在后面，避免前端自行改写语义。
        String content = StrUtil.trimToEmpty(userContent);
        if (StrUtil.isBlank(content)) {
            return command.getPromptTemplate();
        }
        return command.getPromptTemplate() + "\n\n用户补充要求：\n" + content;
    }

    /**
     * 新增 Slash Command 配置。
     */
    public GovernanceSlashCommand createCommand(GovernanceSlashCommand command) {
        LocalDateTime now = LocalDateTime.now();
        GovernanceSlashCommand saved = command.toBuilder()
            .id(IdUtil.getSnowflakeNextId())
            .commandCode(normalizeCommandCode(required(command.getCommandCode(), "命令编码不能为空")))
            .displayName(StrUtil.blankToDefault(command.getDisplayName(), "/" + normalizeCommandCode(command.getCommandCode())))
            .description(command.getDescription())
            .commandType(StrUtil.blankToDefault(command.getCommandType(), "BUILTIN").toUpperCase(Locale.ROOT))
            .promptTemplate(required(command.getPromptTemplate(), "命令提示模板不能为空"))
            .enabled(command.getEnabled() == null ? 1 : command.getEnabled())
            .sortNo(command.getSortNo() == null ? 0 : command.getSortNo())
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        commandRepository.save(saved);
        return saved;
    }

    /**
     * 更新 Slash Command 配置。
     */
    public GovernanceSlashCommand updateCommand(Long id, GovernanceSlashCommand command) {
        GovernanceSlashCommand existing = commandRepository.findById(id);
        if (existing == null) {
            throw new BusinessException("GOVERNANCE_SLASH_COMMAND_NOT_FOUND", "命令不存在");
        }
        GovernanceSlashCommand saved = command.toBuilder()
            .id(id)
            .commandCode(normalizeCommandCode(required(command.getCommandCode(), "命令编码不能为空")))
            .displayName(StrUtil.blankToDefault(command.getDisplayName(), "/" + normalizeCommandCode(command.getCommandCode())))
            .description(command.getDescription())
            .commandType(StrUtil.blankToDefault(command.getCommandType(), existing.getCommandType()).toUpperCase(Locale.ROOT))
            .promptTemplate(required(command.getPromptTemplate(), "命令提示模板不能为空"))
            .enabled(command.getEnabled() == null ? existing.getEnabled() : command.getEnabled())
            .sortNo(command.getSortNo() == null ? existing.getSortNo() : command.getSortNo())
            .createdAt(existing.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .deleted(0)
            .build();
        commandRepository.save(saved);
        return saved;
    }

    /**
     * 逻辑删除 Slash Command。
     * @param id 命令主键。
     */
    public void deleteCommand(Long id) {
        commandRepository.softDeleteById(id);
    }

    private String normalizeCommandCode(String commandCode) {
        return StrUtil.removePrefix(StrUtil.trimToEmpty(commandCode), "/");
    }

    private String required(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException("GOVERNANCE_REQUIRED_FIELD", message);
        }
        return value.trim();
    }
}
