package com.codingx.cli.slash;

import java.util.List;

/**
 * Slash Command 目录读取边界，TUI 和后端流请求都通过该接口消费同一份治理命令。
 */
@FunctionalInterface
public interface SlashCommandCatalog {

    /**
     * 空目录实现，用于测试和旧构造链路，保证后端不可用时本地控制命令仍可展示。
     */
    SlashCommandCatalog EMPTY = List::of;

    /**
     * 读取当前用户可见的启用命令。
     * @return 后端治理中心启用命令列表；读取失败时实现应返回空列表。
     */
    List<CliSlashCommand> listCommands();
}
