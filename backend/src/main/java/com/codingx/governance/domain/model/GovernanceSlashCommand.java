package com.codingx.governance.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一个可由用户端聊天输入区选择的 slash command。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GovernanceSlashCommand {

    /** Slash 命令主键。 */
    private Long id;
    /** 命令编码，不包含前导斜杠。 */
    private String commandCode;
    /** 命令展示名，通常包含前导斜杠。 */
    private String displayName;
    /** 命令说明，用于输入区和管理端展示。 */
    private String description;
    /** 命令类型，MVP 使用 BUILTIN。 */
    private String commandType;
    /** 命令提示模板，后端注入模型上下文时使用。 */
    private String promptTemplate;
    /** 启用状态，1 表示用户端可见。 */
    private Integer enabled;
    /** 排序号，数值越小越靠前。 */
    private Integer sortNo;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
