package com.codingx.expert.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.expert.infrastructure.persistence.dataobject.ChatExpertDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义聊天专家配置表 Mapper。
 */
@Mapper
public interface ChatExpertMapper extends BaseMapper<ChatExpertDO> {
}
