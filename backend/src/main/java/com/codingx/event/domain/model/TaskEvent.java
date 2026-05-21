package com.codingx.event.domain.model;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 TaskEvent 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskEvent {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 关联任务标识。
     */
    private Long taskId;

    /**
     * eventType 字段。
     */
    private String eventType;

    /**
     * sequenceNo 字段。
     */
    private Long sequenceNo;

    /**
     * 展示标题。
     */
    private String title;

    /**
     * 主体内容。
     */
    private String content;

    /**
     * JSON 格式元数据。
     */
    private String metadataJson;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建 create 所需数据并返回结果。
     * @param taskId 输入参数。
     * @param eventType 输入参数。
     * @param sequenceNo 输入参数。
     * @param title 输入参数。
     * @param content 输入参数。
     * @param metadataJson 输入参数。
     * @return 输入参数。
     */
    public static TaskEvent create(Long taskId, String eventType, Long sequenceNo, String title, String content, String metadataJson) {
        if (taskId == null || sequenceNo == null || StrUtil.hasBlank(eventType, title)) {
            throw new IllegalArgumentException(ErrorMessageCatalog.TASK_EVENT_FIELDS_REQUIRED);
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
