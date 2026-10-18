package com.sdecloud.priorityflow.report.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sdecloud.priorityflow.report.persistence.po.WeeklyReportDo;

import java.time.LocalDate;
import java.util.List;

/**
 * 计划周报仓库。
 *
 * @author priority-flow
 */
public interface WeeklyReportRepository extends IService<WeeklyReportDo> {

    /**
     * 按项目 ID 集合查询全部周报（@TableLogic 自动过滤软删）
     */
    List<WeeklyReportDo> listByProjectIds(List<String> projectIds);

    /**
     * 同项目同周期是否已存在（del_flag='0' 范围内判重，不建数据库唯一索引以规避软删行阻塞重建）
     */
    boolean existsByProjectAndPeriod(String projectId, LocalDate periodStart, LocalDate periodEnd);
}
