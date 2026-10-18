package com.sdecloud.priorityflow.report.service;

import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportGenerateDto;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportSaveDto;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportUpdateDto;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportDraftVo;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportVo;

import java.util.List;

/**
 * 计划周报服务：跨项目列表、草稿生成（不入库）、发布、编辑、软删。
 *
 * <p>权限说明：projectId 在请求体或 {id} 路径中，无法用项目权限切面定位项目上下文，
 * 全部接口在 service 内按记录/入参 projectId 手工校验 pf:report:manage。</p>
 *
 * @author priority-flow
 */
public interface WeeklyReportService {

    /**
     * 我可见项目（pf:task:viewProject 集合）的全部周报，按周期起始日倒序、同周期按项目名排序
     */
    List<WeeklyReportVo> listVisible();

    /**
     * 实时聚合生成周报草稿（不入库）
     */
    WeeklyReportDraftVo generate(WeeklyReportGenerateDto dto);

    /**
     * 发布周报（同项目同周期唯一，重复返回 4002）
     */
    WeeklyReportVo create(WeeklyReportSaveDto dto);

    /**
     * 编辑周报条目（周期不可改；不存在返回 3010）
     */
    WeeklyReportVo update(String id, WeeklyReportUpdateDto dto);

    /**
     * 删除周报（软删；不存在返回 3010）
     */
    void delete(String id);
}
