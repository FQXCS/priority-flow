package com.sdecloud.priorityflow.common.persistence;

/**
 * 自建 Mapper 基类：对 MyBatis-Plus BaseMapper 薄封装，预留扩展点，不依赖 EFP。
 *
 * @param <T> 实体类型
 * @author priority-flow
 */
public interface BaseMapper<T> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {
}
