package com.codingx.task.domain.model;
import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 定义 Task 的核心领域状态与行为。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Task {

    /**
     * 主键标识。
     */
    private Long id;

    /**
     * 展示标题。
     */
    private String title;

    /**
     * 详细描述。
     */
    private String description;

    /**
     * 当前状态值。
     */
    private TaskStatus status;

    /**
     * 运行时类型。
     */
    private RuntimeType runtimeType;

    /**
     * 关联工作区标识。
     */
    private Long workspaceId;

    /**
     * 创建人用户标识。
     */
    private Long createdBy;

    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间。
     */
    private LocalDateTime finishedAt;

    /**
     * 错误信息。
     */
    private String errorMessage;

    /**
     * 摘要内容。
     */
    private String summary;

    /**
     * 任务绑定的技能编码列表。
     */
    private List<String> skillCodes;

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param title 输入参数。
     * @param description 输入参数。
     * @param runtimeType 输入参数。
     * @param workspaceId 输入参数。
     * @param createdBy 输入参数。
     * @return 输入参数。
     */
    public static Task create(Long id, String title, String description, RuntimeType runtimeType, Long workspaceId, Long createdBy) {
        return create(id, title, description, runtimeType, workspaceId, createdBy, List.of());
    }

    /**
     * 创建 create 所需数据并返回结果。
     * @param id 输入参数。
     * @param title 输入参数。
     * @param description 输入参数。
     * @param runtimeType 输入参数。
     * @param workspaceId 输入参数。
     * @param createdBy 输入参数。
     * @param skillCodes 绑定技能编码列表。
     * @return 输入参数。
     */
    public static Task create(Long id, String title, String description, RuntimeType runtimeType, Long workspaceId, Long createdBy, List<String> skillCodes) {
        if (id == null || createdBy == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.TASK_ID_AND_CREATOR_REQUIRED);
        }
        if (StrUtil.isBlank(title) || runtimeType == null) {
            throw new IllegalArgumentException(ErrorMessageCatalog.TASK_TITLE_AND_RUNTIME_TYPE_REQUIRED);
        }
        return Task.builder()
            .id(id)
            .title(title)
            .description(description)
            .status(TaskStatus.CREATED)
            .runtimeType(runtimeType)
            .workspaceId(workspaceId)
            .createdBy(createdBy)
            .skillCodes(skillCodes == null ? List.of() : skillCodes)
            .build();
    }

    /**
     * 启动 start 处理的流程。
     */
    public void start() {
        ensureStatus(TaskStatus.CREATED, ErrorMessageCatalog.TASK_ONLY_CREATED_CAN_START);
        this.status = TaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 将 complete 处理的流程标记为完成。
     * @param summary 输入参数。
     */
    public void complete(String summary) {
        ensureStatus(TaskStatus.RUNNING, ErrorMessageCatalog.TASK_ONLY_RUNNING_CAN_COMPLETE);
        this.status = TaskStatus.SUCCEEDED;
        this.summary = summary;
        this.finishedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    /**
     * 将 fail 处理的流程标记为失败。
     * @param errorMessage 输入参数。
     */
    public void fail(String errorMessage) {
        ensureStatus(TaskStatus.RUNNING, ErrorMessageCatalog.TASK_ONLY_RUNNING_CAN_FAIL);
        this.status = TaskStatus.FAILED;
        this.errorMessage = StrUtil.blankToDefault(errorMessage, ErrorMessageCatalog.TASK_UNKNOWN_ERROR);
        this.finishedAt = LocalDateTime.now();
    }

    /**
     * 更新 updateSummary 处理的状态。
     * @param summary 输入参数。
     */
    public void updateSummary(String summary) {
        this.summary = summary;
    }

    /**
     * 校验 ensureStatus 需要的前置条件。
     * @param expected 输入参数。
     * @param message 输入参数。
     */
    private void ensureStatus(TaskStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }
}
