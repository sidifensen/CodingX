package com.codingx.auth.application.service;

import com.codingx.auth.domain.model.User;
import java.util.List;
import lombok.Builder;

/**
 * 管理端用户分页结果视图，兼容前端 records/total/current/size/pages 契约。
 *
 * @param records 当前页用户领域对象列表，空页返回空列表。
 * @param total 符合筛选条件的用户总数。
 * @param current 当前页码，从 1 开始。
 * @param size 每页条数。
 * @param pages 按 total 和 size 计算出的总页数。
 */
@Builder
public record AdminUserPageView(
    List<User> records, // 当前页用户领域对象列表，空页返回空列表。
    Long total, // 符合筛选条件的用户总数。
    Long current, // 当前页码。
    Long size, // 每页条数。
    Long pages // 按 total 和 size 计算出的总页数。
) {
}
