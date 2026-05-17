package com.codingx.auth.interfaces.response;

import java.util.List;
import lombok.Builder;

/**
 * 定义管理端用户分页响应结构。
 */
@Builder
public record AdminUserPageResponse(
    List<AdminUserSummaryResponse> records,
    Long total,
    Long current,
    Long size,
    Long pages
) {
}
