package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.Notification;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一消息通知 Mapper
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {
}
