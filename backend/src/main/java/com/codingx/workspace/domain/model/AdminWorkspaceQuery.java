package com.codingx.workspace.domain.model;

/**
 * 管理端工作空间列表查询条件，统一承载分页、关键字和运行目标筛选。
 * @param current 当前页码，从 1 开始。
 * @param size 每页条数。
 * @param keyword 可选关键字，匹配名称、仓库、分支、目录或 ID。
 * @param runtimeTarget 可选运行目标，支持 cloud、local 或 ALL。
 */
public record AdminWorkspaceQuery(
    int current,
    int size,
    String keyword,
    String runtimeTarget
) {
}
