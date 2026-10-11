package com.sdecloud.priorityflow.common.rank;

/**
 * 排序键空间耗尽异常：中值插入达到最大深度时抛出，调用方应触发当前作用域重整。
 *
 * @author priority-flow
 */
public class RankSpaceExhaustedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RankSpaceExhaustedException(String message) {
        super(message);
    }
}
