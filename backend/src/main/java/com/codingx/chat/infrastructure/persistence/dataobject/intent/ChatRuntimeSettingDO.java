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
    @TableId("id") private Long id; // 配置主键。
    @TableField("setting_key") private String settingKey; // 配置键，运行时读取时使用的唯一标识。
    @TableField("setting_value") private String settingValue; // 配置明文值，敏感配置保存后会置空。
    @TableField("encrypted_value") private String encryptedValue; // 敏感配置密文值，仅后端运行时解密使用。
    @TableField("secret") private Boolean secret; // 是否为敏感配置，true 表示需要加密存储。
    @TableField("masked_value") private String maskedValue; // 敏感配置脱敏展示值。
    @TableField("encryption_algorithm") private String encryptionAlgorithm; // 加密算法标识。
    @TableField("encryption_key_version") private String encryptionKeyVersion; // 加密密钥版本。
    @TableField("value_type") private String valueType; // 配置值类型，例如 STRING、INTEGER、BOOLEAN。
    @TableField("category_code") private String categoryCode; // 配置分类编码。
    @TableField("description") private String description; // 配置说明文案。
    @TableField("sort_no") private Integer sortNo; // 排序号，数值越小越靠前。
    @TableField("restart_required") private Boolean restartRequired; // 是否需要重启后生效。
    @TableField("created_at") private LocalDateTime createdAt; // 创建时间。
    @TableField("updated_at") private LocalDateTime updatedAt; // 最近更新时间。
    @TableField("deleted") private Integer deleted; // 逻辑删除标记，1 表示已删除。
}
