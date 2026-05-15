package com.codingx.chat.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.codingx.chat.infrastructure.persistence.dataobject.ChatSampleQuestionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义示例问题表的 MyBatis Mapper。
 */
@Mapper
public interface ChatSampleQuestionMapper extends BaseMapper<ChatSampleQuestionDO> {
}
