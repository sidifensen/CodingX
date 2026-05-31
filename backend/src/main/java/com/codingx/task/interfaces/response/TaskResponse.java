package com.codingx.task.interfaces.response;

import com.codingx.task.domain.model.RuntimeType;
import com.codingx.task.domain.model.TaskStatus;

/**
 * 任务列表与任务详情接口的响应载体。
 *
 * @param id 任务主键，序列化为字符串避免前端 Long 精度丢失。
 * @param title 任务展示标题，来自创建请求。
 * @param description 任务说明，可为空；用于展示任务目标和执行背景。
 * @param status 任务当前状态，决定前端展示待启动、运行中、成功或失败。
 * @param runtimeType 任务执行运行时，用于区分本地、云端或模拟执行链路。
 * @param workspaceId 任务关联工作空间标识，可为空。
 * @param createdBy 任务创建人用户标识。
 * @param summary 任务执行成功后的摘要，未完成或失败时可为空。
 * @param errorMessage 任务执行失败时的中文错误文案，非失败状态可为空。
 */
public record TaskResponse(
    Long id, // 任务主键，序列化为字符串避免前端 Long 精度丢失。
    String title, // 任务展示标题。
    String description, // 任务说明，可为空。
    TaskStatus status, // 任务当前状态，决定前端展示待启动、运行中、成功或失败。
    RuntimeType runtimeType, // 任务执行运行时，用于区分本地、云端或模拟执行链路。
    Long workspaceId, // 任务关联工作空间标识，可为空。
    Long createdBy, // 任务创建人用户标识。
    String summary, // 任务执行成功后的摘要，未完成或失败时可为空。
    String errorMessage // 任务执行失败时的中文错误文案，非失败状态可为空。
) {
}
