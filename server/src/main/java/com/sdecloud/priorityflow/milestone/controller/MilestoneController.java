package com.sdecloud.priorityflow.milestone.controller;

import com.sdecloud.priorityflow.common.api.R;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.RequireProjectPermission;
import com.sdecloud.priorityflow.milestone.bean.dto.MilestoneCreateDto;
import com.sdecloud.priorityflow.milestone.bean.dto.MilestoneUpdateDto;
import com.sdecloud.priorityflow.milestone.bean.vo.MilestoneVo;
import com.sdecloud.priorityflow.milestone.service.MilestoneService;
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
 * 项目规划里程碑接口：项目内阶段目标增删改查。
 *
 * @author priority-flow
 */
@Tag(name = "项目规划", description = "里程碑列表/创建/编辑/删除")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}")
public class MilestoneController {

    private final MilestoneService milestoneService;

    @Operation(summary = "里程碑列表", description = "预计完成日期升序，未规划日期随后")
    @RequireProjectPermission(PermissionConstants.MILESTONE_VIEW)
    @GetMapping("/milestones")
    public R<List<MilestoneVo>> list(@PathVariable String projectId) {
        return R.ok(milestoneService.listByProject(projectId));
    }

    @Operation(summary = "创建里程碑", description = "负责人须为项目成员；预计完成日期留空放入未规划日期")
    @RequireProjectPermission(PermissionConstants.MILESTONE_MANAGE)
    @PostMapping("/milestones")
    public R<MilestoneVo> create(@PathVariable String projectId, @Valid @RequestBody MilestoneCreateDto dto) {
        return R.ok(milestoneService.create(projectId, dto));
    }

    @Operation(summary = "编辑里程碑", description = "全量覆盖；预计完成日期传空即移入未规划日期")
    @RequireProjectPermission(PermissionConstants.MILESTONE_MANAGE)
    @PutMapping("/milestones/{milestoneId}")
    public R<MilestoneVo> update(@PathVariable String projectId, @PathVariable String milestoneId,
                                 @Valid @RequestBody MilestoneUpdateDto dto) {
        return R.ok(milestoneService.update(projectId, milestoneId, dto));
    }

    @Operation(summary = "删除里程碑", description = "逻辑删，不影响任务")
    @RequireProjectPermission(PermissionConstants.MILESTONE_MANAGE)
    @DeleteMapping("/milestones/{milestoneId}")
    public R<Void> delete(@PathVariable String projectId, @PathVariable String milestoneId) {
        milestoneService.delete(projectId, milestoneId);
        return R.ok();
    }
}
