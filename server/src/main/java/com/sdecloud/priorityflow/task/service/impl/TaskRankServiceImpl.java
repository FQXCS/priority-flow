package com.sdecloud.priorityflow.task.service.impl;

import com.sdecloud.priorityflow.common.rank.LexoRankUtil;
import com.sdecloud.priorityflow.common.rank.RankSpaceExhaustedException;
import com.sdecloud.priorityflow.task.enums.PriorityEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import com.sdecloud.priorityflow.task.service.TaskQueueOrder;
import com.sdecloud.priorityflow.task.service.TaskRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 排序键服务实现。
 *
 * @author priority-flow
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskRankServiceImpl implements TaskRankService {

    private final TaskInfoRepository taskInfoRepository;

    @Override
    public String rankForInsert(List<TaskInfoDo> orderedExisting, int insertIndex, TaskInfoDo taskToInsert) {
        String prev = insertIndex > 0 ? orderedExisting.get(insertIndex - 1).getSortRank() : null;
        String next = insertIndex < orderedExisting.size() ? orderedExisting.get(insertIndex).getSortRank() : null;
        try {
            return LexoRankUtil.between(prev, next);
        } catch (RankSpaceExhaustedException e) {
            // 中值插入达到最大深度：对当前作用域按显示顺序重新均匀分布（含新插入位）
            return rebalanceAndRank(orderedExisting, insertIndex);
        }
    }

    @Override
    public int tailIndexOfPriority(List<TaskInfoDo> orderedQueue, PriorityEnum priority) {
        return TaskQueueOrder.bucketEndIndex(orderedQueue, priority);
    }

    /**
     * 重整：现有任务 + 待插入位共 n+1 个位置均匀取键，现有任务批量落库新键，
     * 返回插入位的键。<100 人规模下重整极低频。
     */
    private String rebalanceAndRank(List<TaskInfoDo> orderedExisting, int insertIndex) {
        List<String> ranks = LexoRankUtil.rebalance(orderedExisting.size() + 1);
        List<TaskInfoDo> toUpdate = new ArrayList<>(orderedExisting.size());
        int rankIdx = 0;
        for (int i = 0; i <= orderedExisting.size(); i++) {
            if (i == insertIndex) {
                continue;
            }
            TaskInfoDo task = orderedExisting.get(rankIdx++);
            task.setSortRank(ranks.get(i));
            toUpdate.add(task);
        }
        if (!toUpdate.isEmpty()) {
            taskInfoRepository.updateBatchById(toUpdate);
            log.info("排序键重整完成：作用域任务数={}，插入位置={}", orderedExisting.size(), insertIndex);
        }
        return ranks.get(insertIndex);
    }
}
