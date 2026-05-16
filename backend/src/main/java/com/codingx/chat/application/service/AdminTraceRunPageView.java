package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatTraceRun;
import java.util.List;

/**
 * 定义管理端 Trace 列表分页视图，统一分页字段契约并承载运行记录。
 *
 * @param records 当前页记录。
 * @param total 总记录数。
 * @param size 每页大小。
 * @param current 当前页码（从 1 开始）。
 * @param pages 总页数。
 */
public record AdminTraceRunPageView(
    List<ChatTraceRun> records,
    long total,
    long size,
    long current,
    long pages
) {
}
