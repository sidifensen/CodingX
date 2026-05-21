package com.codingx.chat.infrastructure.runtime;

/**
 * 表示一次队列门控申请的结果。
 */
public record QueueAcquireResult(
    boolean allowed,
    String reason,
    Integer queuePosition
) {

    /**
     * 创建成功获取执行资格的结果。
     * @return 成功结果。
     */
    public static QueueAcquireResult granted() {
        return new QueueAcquireResult(true, null, null);
    }

    /**
     * 创建拒绝结果。
     * @param reason 拒绝原因。
     * @return 拒绝结果。
     */
    public static QueueAcquireResult rejected(String reason) {
        return new QueueAcquireResult(false, reason, null);
    }

    /**
     * 创建排队中结果。
     * @param position 当前队列位置（从1开始）。
     * @return 排队结果。
     */
    public static QueueAcquireResult queued(int position) {
        return new QueueAcquireResult(false, "queued", Math.max(1, position));
    }
}
