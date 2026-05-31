package com.codingx.skill.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 聊天技能领域对象，描述技能元信息、启用状态和技能包存储信息。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatSkill {

    /**
     * 技能主键。
     */
    private Long id;

    /**
     * 技能编码，供聊天链路、运行上下文和上传去重使用。
     */
    private String skillCode;

    /**
     * 技能展示名称，管理端和模型上下文都会展示。
     */
    private String displayName;

    /**
     * 技能说明，可来自手工维护或 SKILL.md 描述。
     */
    private String description;

    /**
     * 技能分类，用于管理端筛选和展示。
     */
    private String category;

    /**
     * 技能来源，区分 built-in 与 uploaded 等来源。
     */
    private String sourceType;

    /**
     * 启用标记，1 表示可被用户侧聊天链路读取。
     */
    private Integer enabled;

    /**
     * 排序值，越小越靠前。
     */
    private Integer sortNo;

    /**
     * 技能包对象存储键，目录化存储时表示目录前缀。
     */
    private String storageKey;

    /**
     * 技能包存储格式，directory 表示目录化，zip 表示历史压缩包。
     */
    private String packageStorageFormat;

    /**
     * 技能包展示名称，目录化后不保留 zip/skill 后缀。
     */
    private String packageFileName;

    /**
     * 技能包总字节数，用于管理端展示。
     */
    private Long packageSize;

    /**
     * 目录化技能包内容摘要，用于重复上传检测。
     */
    private String packageChecksum;

    /**
     * 上传人用户标识，可为空。
     */
    private Long uploadedBy;

    /**
     * 上传时间，可为空。
     */
    private LocalDateTime uploadedAt;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 删除标记，0 表示有效。
     */
    private Integer deleted;
}

