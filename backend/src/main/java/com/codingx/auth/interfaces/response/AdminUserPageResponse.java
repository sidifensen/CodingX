package com.codingx.auth.interfaces.response;

import java.util.List;
import lombok.Builder;

/**
 * 管理端用户分页响应体。
 */
@Builder
public record AdminUserPageResponse(
    List<AdminUserSummaryResponse> records, // 当前页用户列表。
    Long total, // 符合筛选条件的用户总数。
    Long current, // 当前页码。
    Long size, // 每页条数。
    Long pages // 总页数。
) {
}
