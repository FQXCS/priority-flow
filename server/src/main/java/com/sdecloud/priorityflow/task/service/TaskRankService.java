package com.sdecloud.priorityflow.task.service;

import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;

import java.util.List;

/**
 * 排序键服务：LexoRank 作用域队列管理（服务端计算 rank，不信任前端提交值）。
 *
 * <p>排序方案（2026-10 重构）：显示顺序 = 优先级降序 + 同优先级桶内 sort_rank 升序；
 * 传入本服务的方法的队列必须已按显示顺序排列（仓储队列方法已排序）。</p>
 *
 * @author priority-flow
 */
public interface TaskRankService {

    /**
     * 在目标作用域队列的指定位置为新任务计算 rank。
     * 中值插入达到最大深度时，对当前作用域按显示顺序重新均匀分布并落库（重整）。
     *
     * @param orderedExisting 作用域内现有任务（已按显示顺序排列，不含待插入任务）
     * @param insertIndex     目标插入位置（0..orderedExisting.size()）
     * @param taskToInsert    待插入任务（仅用于重整时计数，不落库）
     * @return 新任务的排序键
     */
    String rankForInsert(List<TaskInfoDo> orderedExisting, int insertIndex, TaskInfoDo taskToInsert);

    /**
     * 优先级桶尾插入位置：紧急度不低于该优先级的任务数之后
     * （同优先级桶存在时即桶内最后一个任务之后；不存在时为理论桶位置，
     * 对应入队规则：新任务插入同优先级桶尾）
     */
    int tailIndexOfPriority(List<TaskInfoDo> orderedQueue, PriorityEnum priority);
}
