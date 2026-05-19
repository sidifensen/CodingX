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

    private Long id;
    private String settingKey;
    private String settingValue;
    private String valueType;
    private String categoryCode;
    private String description;
    private Integer sortNo;
    private Boolean restartRequired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
