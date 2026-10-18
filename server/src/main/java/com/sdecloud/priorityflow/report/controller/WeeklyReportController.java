package com.sdecloud.priorityflow.report.controller;

import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportGenerateDto;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportSaveDto;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportUpdateDto;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportDraftVo;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportVo;
import com.sdecloud.priorityflow.report.service.WeeklyReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计划周报接口：跨项目列表 + 按项目生成/发布/编辑/删除。
 *
 * <p>不挂项目权限切面：projectId 在请求体或 {id} 路径中，切面无法定位项目上下文，
 * service 内按 pf:report:manage 手工校验（列表按可见项目过滤，先例 GET /api/board）。</p>
 *
 * @author priority-flow
 */
@Tag(name = "计划周报", description = "周报列表/生成草稿/发布/编辑/删除")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    @Operation(summary = "我可见项目的全部周报", description = "按周期起始日倒序、同周期按项目名排序；列表不分页")
    @GetMapping("/weekly-reports")
    public R<List<WeeklyReportVo>> list() {
        return R.ok(weeklyReportService.listVisible());
    }

    @Operation(summary = "生成周报草稿", description = "按任务看板实时聚合，不入库；需 pf:report:manage")
    @PostMapping("/weekly-reports/generate")
    public R<WeeklyReportDraftVo> generate(@Valid @RequestBody WeeklyReportGenerateDto dto) {
        return R.ok(weeklyReportService.generate(dto));
    }

    @Operation(summary = "发布周报", description = "快照入库；同项目同周期已存在返回 4002；需 pf:report:manage")
    @PostMapping("/weekly-reports")
    public R<WeeklyReportVo> create(@Valid @RequestBody WeeklyReportSaveDto dto) {
        return R.ok(weeklyReportService.create(dto));
    }

    @Operation(summary = "编辑周报", description = "仅改两段条目，周期不可改；不存在返回 3010；需 pf:report:manage")
    @PutMapping("/weekly-reports/{id}")
    public R<WeeklyReportVo> update(@PathVariable String id, @Valid @RequestBody WeeklyReportUpdateDto dto) {
        return R.ok(weeklyReportService.update(id, dto));
    }

    @Operation(summary = "删除周报", description = "软删；不存在返回 3010；需 pf:report:manage")
    @DeleteMapping("/weekly-reports/{id}")
    public R<Void> delete(@PathVariable String id) {
        weeklyReportService.delete(id);
        return R.ok();
    }
}
