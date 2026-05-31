package com.codingx.expert.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 定义聊天专家配置表的数据对象映射。
 */
@Data
@TableName("expert")
public class ChatExpertDO {

    /**
     * 专家主键。
     */
    @TableId("id")
    private Long id;

    /**
     * 专家编码，前端和聊天运行时通过该编码引用专家。
     */
    @TableField("expert_code")
    private String expertCode;

    /**
     * 专家展示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 专家能力描述，供管理端和用户侧展示。
     */
    @TableField("description")
    private String description;

    /**
     * 专家分类，用于管理端筛选和用户侧分组。
     */
    @TableField("category")
    private String category;

    /**
     * 标签 JSON 字符串，保存专家的展示标签集合。
     */
    @TableField("tags_json")
    private String tagsJson;

    /**
     * 专家头像地址，可为空。
     */
    @TableField("avatar_url")
    private String avatarUrl;

    /**
     * 专家预设问题，用户选择专家时可作为输入提示。
     */
    @TableField("preset_question")
    private String presetQuestion;

    /**
     * 专家系统提示词，运行时注入模型上下文。
     */
    @TableField("system_prompt")
    private String systemPrompt;

    /**
     * 启用状态，1 表示可在用户侧选择。
     */
    @TableField("enabled")
    private Integer enabled;

    /**
     * 排序号，数值越小越靠前。
     */
    @TableField("sort_no")
    private Integer sortNo;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最近更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记，1 表示已删除。
     */
    @TableField("deleted")
    private Integer deleted;
}
