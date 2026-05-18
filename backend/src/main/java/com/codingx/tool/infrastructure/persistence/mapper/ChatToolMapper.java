package com.codingx.tool.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.tool.infrastructure.persistence.dataobject.ChatToolDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天工具配置表 Mapper。
 */
@Mapper
public interface ChatToolMapper extends BaseMapper<ChatToolDO> {
}
