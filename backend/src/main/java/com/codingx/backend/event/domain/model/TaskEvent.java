package com.codingx.backend.event.domain.model;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskEvent {

    private Long id;
    private Long taskId;
    private String eventType;
    private Long sequenceNo;
    private String title;
    private String content;
    private String metadataJson;
    private LocalDateTime createdAt;

    public static TaskEvent create(Long taskId, String eventType, Long sequenceNo, String title, String content, String metadataJson) {
        if (taskId == null || sequenceNo == null || StrUtil.hasBlank(eventType, title)) {
            throw new IllegalArgumentException("Task event fields are required");
        }
        return TaskEvent.builder()
            .id(IdUtil.getSnowflakeNextId())
            .taskId(taskId)
            .eventType(eventType)
            .sequenceNo(sequenceNo)
            .title(title)
            .content(content)
            .metadataJson(metadataJson)
            .createdAt(LocalDateTime.now())
            .build();
    }
}