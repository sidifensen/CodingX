package com.codingx.chat.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义聊天运行时配置表的数据对象映射。
 */
@Data
@TableName("chat_runtime_setting")
public class ChatRuntimeSettingDO {
    @TableId("id") private Long id;
    @TableField("setting_key") private String settingKey;
    @TableField("setting_value") private String settingValue;
    @TableField("value_type") private String valueType;
    @TableField("description") private String description;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
