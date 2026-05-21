package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatRuntimeSettingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天运行时配置表 Mapper。
 */
@Mapper
public interface ChatRuntimeSettingMapper extends BaseMapper<ChatRuntimeSettingDO> {
}
