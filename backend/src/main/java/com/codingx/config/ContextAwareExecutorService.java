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

    private final ExecutorService delegate;

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
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        delegate.execute(wrapRunnable(snapshot, command));
    }

    @Override
    public Future<?> submit(Runnable task) {
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        return delegate.submit(wrapRunnable(snapshot, task));
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        return delegate.submit(wrapRunnable(snapshot, task), result);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        return delegate.submit(wrapCallable(snapshot, task));
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
        return delegate.invokeAll(tasks.stream().map(task -> wrapCallable(snapshot, task)).toList());
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks,
        long timeout,
        TimeUnit unit
    ) throws InterruptedException {
        ChatExecutorContextSnapshot snapshot = ChatExecutorContextSnapshot.capture();
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
            snapshot.apply();
            try {
                task.run();
            } finally {
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
            snapshot.apply();
            try {
                return task.call();
            } finally {
                ChatExecutorContextSnapshot.clearCurrent();
            }
        };
    }
}
