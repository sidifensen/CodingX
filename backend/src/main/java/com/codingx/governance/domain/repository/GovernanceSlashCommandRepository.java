package com.codingx.governance.domain.repository;

import com.codingx.governance.domain.model.GovernanceSlashCommand;
import java.util.List;

/**
 * 定义 slash command 持久化能力。
 */
public interface GovernanceSlashCommandRepository {

    /**
     * 查询启用且未删除的命令。
     * @return 命令列表。
     */
    List<GovernanceSlashCommand> findEnabledCommands();

    /**
     * 查询全部未删除命令。
     * @return 命令列表。
     */
    List<GovernanceSlashCommand> findAll();

    /**
     * 按命令编码查询命令。
     * @param commandCode 命令编码，可带或不带前导斜杠。
     * @return 命令对象，未命中返回 null。
     */
    GovernanceSlashCommand findByCommandCode(String commandCode);

    /**
     * 按主键查询命令。
     * @param id 命令主键。
     * @return 命令对象，未命中返回 null。
     */
    GovernanceSlashCommand findById(Long id);

    /**
     * 保存命令配置。
     * @param command 命令配置。
     */
    void save(GovernanceSlashCommand command);

    /**
     * 逻辑删除命令。
     * @param id 命令主键。
     */
    void softDeleteById(Long id);
}
