package com.sdecloud.priorityflow.report.persistence.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sdecloud.priorityflow.report.persistence.mapper.WeeklyReportMapper;
import com.sdecloud.priorityflow.report.persistence.po.WeeklyReportDo;
import com.sdecloud.priorityflow.report.persistence.repository.WeeklyReportRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 计划周报仓库实现。
 *
 * @author priority-flow
 */
@Repository
public class WeeklyReportRepositoryImpl extends ServiceImpl<WeeklyReportMapper, WeeklyReportDo>
        implements WeeklyReportRepository {

    @Override
    public List<WeeklyReportDo> listByProjectIds(List<String> projectIds) {
        return this.list(new LambdaQueryWrapper<WeeklyReportDo>()
                .in(WeeklyReportDo::getProjectId, projectIds));
    }

    @Override
    public boolean existsByProjectAndPeriod(String projectId, LocalDate periodStart, LocalDate periodEnd) {
        return this.exists(new LambdaQueryWrapper<WeeklyReportDo>()
                .eq(WeeklyReportDo::getProjectId, projectId)
                .eq(WeeklyReportDo::getPeriodStart, periodStart)
                .eq(WeeklyReportDo::getPeriodEnd, periodEnd));
    }
}
