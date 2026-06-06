package com.codingx.governance.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Slash Command 表数据对象，保存用户端可选择的内置命令目录。
 */
@Data
@TableName("governance_slash_command")
public class GovernanceSlashCommandDO {

    @TableId("id") private Long id; // Slash 命令主键ID。
    @TableField("command_code") private String commandCode; // 命令编码，不含前导斜杠。
    @TableField("display_name") private String displayName; // 命令展示名，通常含前导斜杠。
    @TableField("description") private String description; // 命令说明，供输入区和管理端展示。
    @TableField("command_type") private String commandType; // 命令类型，MVP 使用 BUILTIN。
    @TableField("prompt_template") private String promptTemplate; // 命令提示模板，用于后端生成上下文。
    @TableField("enabled") private Integer enabled; // 启用状态，1 表示用户端可见。
    @TableField("sort_no") private Integer sortNo; // 排序号，数值越小越靠前。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
