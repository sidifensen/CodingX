package com.codingx.expert.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条可被聊天链路选择的专家角色配置。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatExpert {

    /**
     * 专家配置主键。
     */
    private Long id;

    /**
     * 专家编码，前端选择和聊天运行时都通过该编码引用专家。
     */
    private String expertCode;

    /**
     * 专家展示名称。
     */
    private String displayName;

    /**
     * 专家能力描述，供管理端和用户侧展示。
     */
    private String description;

    /**
     * 专家分类，用于管理端筛选和用户侧分组。
     */
    private String category;

    /**
     * 专家标签 JSON 字符串，保存展示标签集合。
     */
    private String tagsJson;

    /**
     * 专家头像地址，可为空。
     */
    private String avatarUrl;

    /**
     * 专家预设问题，用户选择专家时可作为输入提示。
     */
    private String presetQuestion;

    /**
     * 专家系统提示词，聊天运行时注入模型上下文。
     */
    private String systemPrompt;

    /**
     * 启用状态，1 表示用户侧可选择。
     */
    private Integer enabled;

    /**
     * 排序号，数值越小越靠前。
     */
    private Integer sortNo;

    /**
     * 配置创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 配置最近更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    private Integer deleted;
}
