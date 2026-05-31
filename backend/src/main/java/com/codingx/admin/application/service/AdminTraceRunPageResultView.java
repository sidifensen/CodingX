package com.codingx.admin.application.service;

import java.util.List;

/**
 * 定义管理端 Trace 列表分页响应载体，记录项已补齐 username 信息。
 *
 * @param records 当前页记录。
 * @param total 总记录数。
 * @param size 每页大小。
 * @param current 当前页码（从 1 开始）。
 * @param pages 总页数。
 */
public record AdminTraceRunPageResultView(
    List<AdminTraceRunListItemView> records, // 当前页 Trace 列表项，已补齐 username 展示字段。
    long total, // 符合查询条件的总记录数。
    long size, // 当前分页大小。
    long current, // 当前页码，从 1 开始。
    long pages // 按 total 和 size 计算出的总页数。
) {
}
