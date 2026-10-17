package com.sdecloud.priorityflow.milestone.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.milestone.persistence.mapper.MilestoneMapper;
import com.sdecloud.priorityflow.milestone.persistence.po.MilestoneDo;
import com.sdecloud.priorityflow.milestone.persistence.repository.MilestoneRepository;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;

/**
 * 里程碑仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class MilestoneRepositoryImpl extends ServiceImpl<MilestoneMapper, MilestoneDo>
        implements MilestoneRepository {

    @Override
    public List<MilestoneDo> listByProject(String projectId) {
        List<MilestoneDo> list = this.list(new LambdaQueryWrapper<MilestoneDo>()
                .eq(MilestoneDo::getProjectId, projectId)
                .orderByAsc(MilestoneDo::getCreateTime));
        // MySQL 升序 NULL 在前，这里统一把未规划日期排到最后（前端单独分组展示）
        list.sort(Comparator
                .comparing(MilestoneDo::getExpectedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MilestoneDo::getCreateTime));
        return list;
    }
}
