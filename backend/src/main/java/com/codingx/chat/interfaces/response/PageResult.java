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
    List<T> records, // 当前页数据列表，空页返回空列表而不是 null。
    Long total, // 符合查询条件的总记录数。
    Long size, // 当前请求每页条数。
    Long current, // 当前页码，沿用前端分页组件从 1 开始的约定。
    Long pages // 按 total 和 size 计算出的总页数。
) {
}

