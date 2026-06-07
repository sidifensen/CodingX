package com.codingx.cli.slash;

/**
 * CLI 可展示和提交的 Slash Command 快照，数据来源于后端治理中心用户侧命令目录。
 *
 * @param id 后端命令主键文本，可为空；CLI 只用于调试和排序稳定性，不直接提交该字段。
 * @param commandCode 命令编码，不包含前导斜杠，例如 review。
 * @param displayName 命令展示名，通常包含前导斜杠，例如 /review。
 * @param description 命令说明，来自管理端治理配置，用于 TUI 候选列表展示。
 * @param commandType 命令类型，当前内置命令使用 BUILTIN，提交时会规整为 builtin。
 * @param sortNo 管理端排序号，数值越小越靠前。
 */
public record CliSlashCommand(
    String id,
    String commandCode,
    String displayName,
    String description,
    String commandType,
    Integer sortNo
) {
}
