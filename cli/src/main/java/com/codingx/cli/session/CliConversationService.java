package com.codingx.cli.session;

import java.util.List;

/**
 * CLI 会话查询服务边界，供命令层读取 Web 后端会话列表。
 */
public interface CliConversationService {

    /**
     * 空实现用于测试或降级场景；命令层可在未注入生产客户端时保持可运行。
     */
    CliConversationService EMPTY = pageSize -> List.of();

    /**
     * 查询最近可恢复会话。
     *
     * @param pageSize 最大读取数量。
     * @return 最近会话摘要列表。
     */
    List<CliConversation> listRecentConversations(int pageSize);
}
