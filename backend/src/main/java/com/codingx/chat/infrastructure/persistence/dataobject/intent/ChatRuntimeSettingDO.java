package com.codingx.chat.infrastructure.persistence.dataobject.intent;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义系统运行时配置表的数据对象映射。
 */
@Data
@TableName("setting")
public class ChatRuntimeSettingDO {
    @TableId("id") private Long id;
    @TableField("setting_key") private String settingKey;
    @TableField("setting_value") private String settingValue;
    @TableField("encrypted_value") private String encryptedValue;
    @TableField("secret") private Boolean secret;
    @TableField("masked_value") private String maskedValue;
    @TableField("encryption_algorithm") private String encryptionAlgorithm;
    @TableField("encryption_key_version") private String encryptionKeyVersion;
    @TableField("value_type") private String valueType;
    @TableField("category_code") private String categoryCode;
    @TableField("description") private String description;
    @TableField("sort_no") private Integer sortNo;
    @TableField("restart_required") private Boolean restartRequired;
    @TableField("created_at") private LocalDateTime createdAt;
    @TableField("updated_at") private LocalDateTime updatedAt;
    @TableField("deleted") private Integer deleted;
}
