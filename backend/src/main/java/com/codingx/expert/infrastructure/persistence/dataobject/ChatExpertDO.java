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

    @TableId("id")
    private Long id;

    @TableField("expert_code")
    private String expertCode;

    @TableField("display_name")
    private String displayName;

    @TableField("description")
    private String description;

    @TableField("category")
    private String category;

    @TableField("tags_json")
    private String tagsJson;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("preset_question")
    private String presetQuestion;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("enabled")
    private Integer enabled;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted")
    private Integer deleted;
}
