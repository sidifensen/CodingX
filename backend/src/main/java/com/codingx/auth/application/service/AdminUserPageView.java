package com.codingx.auth.application.service;

import com.codingx.auth.domain.model.User;
import java.util.List;
import lombok.Builder;

/**
 * 定义管理端用户分页结果视图，兼容前端 records/total/current/size/pages 契约。
 */
@Builder
public record AdminUserPageView(
    List<User> records,
    Long total,
    Long current,
    Long size,
    Long pages
) {
}
