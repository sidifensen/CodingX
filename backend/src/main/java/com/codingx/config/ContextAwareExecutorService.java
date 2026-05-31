package com.codingx.config;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 为线程池增加聊天上下文透传能力，避免异步线程丢失 runId/trace/工具目录。
 */
public final class ContextAwareExecutorService extends AbstractExecutorService {

    /**
     * 被包装的真实线程池，负责实际任务调度；本类只在提交任务前后补充聊天上下文处理。
     */
    private final ExecutorService delegate;

    /**
     * 创建具备聊天上下文透传能力的执行器。
     * @param delegate 底层真实执行器，不负责上下文捕获与清理。
     */
    ContextAwareExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    /**
     * 返回底层原始执行器，供运行时指标读取。
     * @return 底层执行器。
     */
    public ExecutorService delegate() {
        return delegate;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        // 步骤 1：在调用线程捕获 runId、Trace 节点栈和工具目录，避免异步线程丢失上下文。
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        // 步骤 2：提交包装后的任务，任务真正运行时会先恢复快照再执行用户逻辑。
        delegate.execute(wrapRunnable(snapshot, command));
    }

    @Override
    public Future<?> submit(Runnable task) {
        // 步骤 1：Runnable 提交入口同样捕获当前聊天上下文，保证 submit 与 execute 行为一致。
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        // 步骤 2：包装任务后交给底层线程池，返回值语义仍由原始 ExecutorService 保持。
        return delegate.submit(wrapRunnable(snapshot, task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        // 步骤 1：捕获提交线程上下文，result 只作为 Future 成功结果透传，不参与上下文逻辑。
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        // 步骤 2：底层执行器负责调度和 Future 生命周期，本类只包装 Runnable。
        return delegate.submit(wrapRunnable(snapshot, task), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        // 步骤 1：Callable 可能返回模型或工具执行结果，运行前必须恢复同一聊天上下文。
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        // 步骤 2：异常和返回值由底层 Future 保持原语义，本类只负责 finally 清理上下文。
        return delegate.submit(wrapCallable(snapshot, task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        // 步骤 1：批量任务共享提交时刻的上下文快照，避免每个任务在线程池内读到空上下文。
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        // 步骤 2：逐个包装 Callable 后再批量提交，保持 invokeAll 的等待与中断语义不变。
        return delegate.invokeAll(tasks.stream().map(task -> wrapCallable(snapshot, task)).toList());
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks,
        long timeout,
        TimeUnit unit
    ) throws InterruptedException {
        // 步骤 1：带超时的批量提交也捕获同一份快照，超时控制仍交给底层执行器。
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        // 步骤 2：只替换任务包装，不改变 invokeAll(timeout) 对未完成任务的取消语义。
        return delegate.invokeAll(tasks.stream().map(task -> wrapCallable(snapshot, task)).toList(), timeout, unit);
    }

    /**
     * 包装 Runnable，在异步线程恢复上下文后执行。
     * @param snapshot 上下文快照。
     * @param task 原始任务。
     * @return 包装后的任务。
     */
    private Runnable wrapRunnable(ChatExecutorContextSnapshot snapshot, Runnable task) {
        return () -> {
            // 步骤 1：任务执行前恢复提交线程的聊天上下文，供下游日志、Trace 和工具目录读取。
            snapshot.apply();
            try {
                // 步骤 2：执行原始业务任务，异常保持原样交给线程池异常机制处理。
                task.run();
            } finally {
                // 步骤 3：任务结束后清理 ThreadLocal，避免线程池复用导致不同会话串线。
                ChatExecutorContextSnapshot.clearCurrent();
            }
        };
    }

    /**
     * 包装 Callable，在异步线程恢复上下文后执行。
     * @param snapshot 上下文快照。
     * @param task 原始任务。
     * @return 包装后的任务。
     */
    private <T> Callable<T> wrapCallable(ChatExecutorContextSnapshot snapshot, Callable<T> task) {
        return () -> {
            // 步骤 1：Callable 执行前恢复上下文，保证异步返回值计算仍绑定当前聊天链路。
            snapshot.apply();
            try {
                // 步骤 2：返回原始任务结果，不额外包裹成功值。
                return task.call();
            } finally {
                // 步骤 3：无论成功或异常都清理上下文，保护后续复用线程。
                ChatExecutorContextSnapshot.clearCurrent();
            }
        };
    }
}
