package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条聊天运行时配置。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRuntimeSetting {

    /** 配置主键。 */
    private Long id;

    /** 配置键，运行时读取时使用的唯一标识。 */
    private String settingKey;

    /** 配置明文值，敏感配置保存后会置空。 */
    private String settingValue;

    /** 敏感配置密文值，仅后端运行时解密使用。 */
    private String encryptedValue;

    /** 是否为敏感配置，true 表示需要加密存储且接口返回时脱敏。 */
    private Boolean secret;

    /** 敏感配置脱敏展示值，供管理端列表展示。 */
    private String maskedValue;

    /** 加密算法标识，用于后续密钥或算法升级兼容。 */
    private String encryptionAlgorithm;

    /** 加密密钥版本，用于轮换密钥时定位解密策略。 */
    private String encryptionKeyVersion;

    /** 配置值类型，例如 STRING、INTEGER、BOOLEAN。 */
    private String valueType;

    /** 配置分类编码，管理端按分类展示。 */
    private String categoryCode;

    /** 配置说明文案，供管理端提示配置用途。 */
    private String description;

    /** 排序号，数值越小越靠前。 */
    private Integer sortNo;

    /** 是否需要重启后生效。 */
    private Boolean restartRequired;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
