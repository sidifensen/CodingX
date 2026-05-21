package com.codingx.chat.application.service;

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
    List<AdminTraceRunListItemView> records,
    long total,
    long size,
    long current,
    long pages
) {
}
