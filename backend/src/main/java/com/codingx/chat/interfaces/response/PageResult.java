package com.codingx.chat.interfaces.response;

import java.util.List;
import lombok.Builder;

/**
 * 定义管理端分页查询统一返回结构，兼容前端 records/total/current/size/pages 契约。
 * @param records 当前页数据。
 * @param total 总记录数。
 * @param size 每页条数。
 * @param current 当前页码。
 * @param pages 总页数。
 * @param <T> 数据元素类型。
 */
@Builder
public record PageResult<T>(
    List<T> records,
    Long total,
    Long size,
    Long current,
    Long pages
) {
}

