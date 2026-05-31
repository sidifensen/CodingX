package com.codingx.chat.infrastructure.runtime;

/**
 * 队列门控申请结果，决定聊天请求是立即执行、进入排队还是被拒绝。
 * @param allowed true 表示已获得执行资格，false 表示需要排队或直接拒绝。
 * @param reason 未获得执行资格时的原因编码；queued 表示进入队列，其他值表示拒绝原因。
 * @param queuePosition 当前队列位置，从 1 开始；非排队场景为空。
 */
public record QueueAcquireResult(
    boolean allowed, // true 表示已获得执行资格，false 表示需要排队或直接拒绝。
    String reason, // 未获得执行资格时的原因编码；queued 表示进入队列，其他值表示拒绝原因。
    Integer queuePosition // 当前队列位置，从 1 开始；非排队场景为空。
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
