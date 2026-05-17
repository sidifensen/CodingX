package com.codingx.chat.domain.model;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 表示一条可被聊天链路选择的技能配置。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatSkill {

    private Long id;
    private String skillCode;
    private String displayName;
    private String description;
    private String category;
    private String sourceType;
    private Integer enabled;
    private Integer sortNo;
    private String storageKey;
    private String packageFileName;
    private Long packageSize;
    private String packageChecksum;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
