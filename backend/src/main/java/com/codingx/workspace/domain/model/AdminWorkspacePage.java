package com.codingx.workspace.domain.model;

import java.util.List;

/**
 * 管理端工作空间分页结果，保持与前端 PageResult 字段语义一致。
 * @param records 当前页记录。
 * @param total 总记录数。
 * @param size 每页条数。
 * @param current 当前页码。
 * @param pages 总页数。
 */
public record AdminWorkspacePage(
    List<AdminWorkspaceRecord> records,
    Long total,
    Long size,
    Long current,
    Long pages
) {
}
