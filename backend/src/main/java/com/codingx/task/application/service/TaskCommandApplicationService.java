package com.codingx.task.application.service;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.runtime.domain.service.TaskRuntimeExecutor;
import com.codingx.task.application.command.CreateTaskCommand;
import com.codingx.task.application.command.StartTaskCommand;
import com.codingx.task.domain.model.Task;
import com.codingx.task.domain.repository.TaskRepository;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 任务命令应用服务，负责创建任务和启动任务等会改变任务状态的用例。
 */
@Service
@RequiredArgsConstructor
public class TaskCommandApplicationService {

    /**
     * 任务仓储，用于保存任务聚合和读取待启动任务。
     */
    private final TaskRepository taskRepository;

    /**
     * 工作空间仓储，用于创建任务时校验关联工作空间是否存在。
     */
    private final WorkspaceRepository workspaceRepository;

    /**
     * 任务运行时执行器，用于把已启动任务交给具体运行环境执行。
     */
    private final TaskRuntimeExecutor taskRuntimeExecutor;

    /**
     * 创建任务并持久化初始状态。
     * @param command 创建任务命令。
     * @param createdBy 当前创建人用户标识。
     * @return 新建任务聚合。
     */
    public Task createTask(CreateTaskCommand command, Long createdBy) {
        // 步骤 1：应用层命令不能为空，避免 Controller 或测试绕过请求校验后传入空命令。
        Assert.notNull(command, ErrorMessageCatalog.TASK_COMMAND_REQUIRED);
        // 步骤 2：任务绑定工作空间时必须先确认工作空间存在，防止产生悬挂引用。
        if (command.workspaceId() != null) {
            workspaceRepository.ensureExists(command.workspaceId());
        }
        // 步骤 3：由领域对象创建初始任务并统一设置 CREATED 状态、创建人和技能列表。
        Task task = Task.create(
            IdUtil.getSnowflakeNextId(),
            command.title(),
            command.description(),
            command.runtimeType(),
            command.workspaceId(),
            createdBy,
            command.skillCodes()
        );
        // 步骤 4：保存任务后返回领域对象，Controller 再通过视图服务投影为响应。
        taskRepository.save(task);
        return task;
    }

    /**
     * 启动任务并交给运行时执行器处理。
     * @param command 启动任务命令。
     */
    public void startTask(StartTaskCommand command) {
        // 步骤 1：读取任务聚合，缺失时由仓储抛出统一未找到异常。
        Task task = taskRepository.requireById(command.taskId());
        // 步骤 2：只有任务创建人可以启动任务，避免越权执行他人工作空间内容。
        if (!task.getCreatedBy().equals(command.operatorId())) {
            throw new ForbiddenException(ErrorMessageCatalog.TASK_FORBIDDEN_OPERATE);
        }
        // 步骤 3：领域对象完成状态流转并持久化 RUNNING 状态。
        task.start();
        taskRepository.save(task);
        // 步骤 4：运行时执行器异步或同步承接实际任务执行，后续结果再回写任务状态。
        taskRuntimeExecutor.execute(task);
    }
}
