package com.codingx.chat.interfaces.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游标分页响应结构，用于聊天会话与消息历史按需加载，避免前端一次性接收全量列表。
 *
 * @param items 当前页数据，按接口约定顺序返回；无数据时返回空列表而不是 null。
 * @param hasMore 是否仍有下一页数据。
 * @param nextCursor 下一页游标；没有下一页时为空。
 * @param <T> 当前页元素类型。
 */
public record CursorPageResponse<T>(
    List<T> items, // 当前页数据，空页返回空列表。
    boolean hasMore, // true 表示前端可以继续请求下一页。
    Cursor nextCursor // 下一页起点游标，无下一页时为 null。
) {

    /**
     * 描述下一页起点。会话分页使用 cursorPinned 与 cursorUpdatedAt，消息分页使用 cursorCreatedAt，两者都配合 cursorId 打破同时间排序并发歧义。
     *
     * @param cursorPinned 会话分页游标置顶状态。
     * @param cursorUpdatedAt 会话列表下一页起点的更新时间。
     * @param cursorCreatedAt 消息列表下一页起点的创建时间。
     * @param cursorId 下一页起点记录主键。
     */
    public record Cursor(
        Boolean cursorPinned, // 会话分页置顶游标，保证 pinned desc 排序下翻页不跳项。
        LocalDateTime cursorUpdatedAt, // 会话分页游标时间。
        LocalDateTime cursorCreatedAt, // 消息分页游标时间。
        Long cursorId // 游标记录主键，配合时间字段保证稳定翻页。
    ) {
    }
}
