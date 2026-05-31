package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示首页欢迎区使用的示例问题记录。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatSampleQuestion {

    /** 示例问题主键。 */
    private Long id;

    /** 欢迎区展示的问题文案。 */
    private String questionText;

    /** 问题分类标签，用于前端分组或展示。 */
    private String category;

    /** 启用状态，1 表示展示到欢迎区。 */
    private Integer enabled;

    /** 排序号，数值越小越靠前。 */
    private Integer sortNo;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记，1 表示已删除。 */
    private Integer deleted;
}
