package com.codingx.backend.event.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Models the core domain state and behavior for TaskEvent.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskEvent {

    /**
     * Primary identifier.
     */
    private Long id;
    /**
     * Related task identifier.
     */
    private Long taskId;
    /**
     * eventType value.
     */
    private String eventType;
    /**
     * sequenceNo value.
     */
    private Long sequenceNo;
    /**
     * Display title.
     */
    private String title;
    /**
     * Primary payload content.
     */
    private String content;
    /**
     * Metadata payload in JSON format.
     */
    private String metadataJson;
    /**
     * Creation timestamp.
     */
    private LocalDateTime createdAt;

    /**
     * Creates the data required by create and returns the result.
     * @param taskId input argument.
     * @param eventType input argument.
     * @param sequenceNo input argument.
     * @param title input argument.
     * @param content input argument.
     * @param metadataJson input argument.
     * @return processing result.
     */
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
