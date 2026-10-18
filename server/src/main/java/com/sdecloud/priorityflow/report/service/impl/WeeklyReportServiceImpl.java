package com.sdecloud.priorityflow.report.service.impl;

import com.alibaba.fastjson2.JSON;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.common.permission.PermissionConstants;
import com.sdecloud.priorityflow.common.permission.ProjectPermissionChecker;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import com.sdecloud.priorityflow.report.bean.WeeklyReportEntry;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportGenerateDto;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportSaveDto;
import com.sdecloud.priorityflow.report.bean.dto.WeeklyReportUpdateDto;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportDraftVo;
import com.sdecloud.priorityflow.report.bean.vo.WeeklyReportVo;
import com.sdecloud.priorityflow.report.persistence.po.WeeklyReportDo;
import com.sdecloud.priorityflow.report.persistence.repository.WeeklyReportRepository;
import com.sdecloud.priorityflow.report.service.WeeklyReportAggregator;
import com.sdecloud.priorityflow.report.service.WeeklyReportService;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计划周报服务实现：发布为快照记录，编辑改快照；权限在 service 内手工校验
 * （projectId 在 body/路径 id 中，项目权限切面无法定位项目上下文）。
 *
 * @author priority-flow
 */
@Service
@RequiredArgsConstructor
public class WeeklyReportServiceImpl implements WeeklyReportService {

    /** 统计周期最大跨度（天） */
    private static final int MAX_PERIOD_SPAN = 31;

    /** 单段条目数量上限 */
    private static final int MAX_ENTRY_COUNT = 100;

    /** 单条条目任务说明字符上限 */
    private static final int MAX_ENTRY_LENGTH = 500;

    /** 单条条目人员显示名字符上限 */
    private static final int MAX_ENTRY_PERSON_LENGTH = 100;

    private final WeeklyReportRepository weeklyReportRepository;
    private final TaskInfoRepository taskInfoRepository;
    private final ProjectInfoRepository projectInfoRepository;
    private final SysUserRepository sysUserRepository;
    private final ProjectPermissionChecker permissionChecker;

    @Override
    public List<WeeklyReportVo> listVisible() {
        String userId = UserContext.require().getUserId();
        // 可见范围 = 我参与的项目中具备查看项目任务权限的项目（与跨项目看板口径一致）
        List<ProjectInfoDo> visible = projectInfoRepository.listByMemberUserId(userId).stream()
                .filter(p -> permissionChecker.hasPermission(userId, p.getId(), PermissionConstants.TASK_VIEW_PROJECT))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<String, String> projectNames = visible.stream()
                .collect(Collectors.toMap(ProjectInfoDo::getId, ProjectInfoDo::getProjectName));
        return weeklyReportRepository.listByProjectIds(List.copyOf(projectNames.keySet())).stream()
                .map(entity -> toVo(entity, projectNames.get(entity.getProjectId())))
                // 全部周报按周期倒序平铺，同周期按项目名排序
                .sorted(Comparator.comparing(WeeklyReportVo::getPeriodStart).reversed()
                        .thenComparing(WeeklyReportVo::getProjectName))
                .toList();
    }

    @Override
    public WeeklyReportDraftVo generate(WeeklyReportGenerateDto dto) {
        String userId = UserContext.require().getUserId();
        checkManagePermission(userId, dto.getProjectId());
        validatePeriod(dto.getPeriodStart(), dto.getPeriodEnd());
        List<TaskInfoDo> tasks = taskInfoRepository.listByProject(dto.getProjectId());
        Map<String, String> assigneeNames = loadAssigneeNames(tasks);
        return WeeklyReportAggregator.aggregate(tasks, dto.getPeriodStart(), dto.getPeriodEnd(), assigneeNames);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVo create(WeeklyReportSaveDto dto) {
        String userId = UserContext.require().getUserId();
        checkManagePermission(userId, dto.getProjectId());
        validatePeriod(dto.getPeriodStart(), dto.getPeriodEnd());
        List<WeeklyReportEntry> content = validateEntries(dto.getContent(), "本周进展");
        List<WeeklyReportEntry> planContent = validateEntries(dto.getPlanContent(), "下周计划");
        if (weeklyReportRepository.existsByProjectAndPeriod(dto.getProjectId(), dto.getPeriodStart(), dto.getPeriodEnd())) {
            throw new BusinessException(ResultCode.DUPLICATE, "该周期周报已存在，请直接编辑");
        }
        WeeklyReportDo entity = new WeeklyReportDo();
        entity.setProjectId(dto.getProjectId());
        entity.setPeriodStart(dto.getPeriodStart());
        entity.setPeriodEnd(dto.getPeriodEnd());
        // 结构化条目快照，发布后与任务后续变化无关
        entity.setContent(JSON.toJSONString(content));
        entity.setPlanContent(JSON.toJSONString(planContent));
        weeklyReportRepository.save(entity);
        return toVo(entity, projectDisplayName(dto.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyReportVo update(String id, WeeklyReportUpdateDto dto) {
        WeeklyReportDo entity = requireReport(id);
        checkManagePermission(UserContext.require().getUserId(), entity.getProjectId());
        List<WeeklyReportEntry> content = validateEntries(dto.getContent(), "本周进展");
        List<WeeklyReportEntry> planContent = validateEntries(dto.getPlanContent(), "下周计划");
        entity.setContent(JSON.toJSONString(content));
        entity.setPlanContent(JSON.toJSONString(planContent));
        weeklyReportRepository.updateById(entity);
        return toVo(entity, projectDisplayName(entity.getProjectId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        WeeklyReportDo entity = requireReport(id);
        checkManagePermission(UserContext.require().getUserId(), entity.getProjectId());
        weeklyReportRepository.removeById(id);
    }

    /**
     * 管理周报权限校验（含非项目成员：权限并集为空必不通过）
     */
    private void checkManagePermission(String userId, String projectId) {
        if (!permissionChecker.hasPermission(userId, projectId, PermissionConstants.REPORT_MANAGE)) {
            throw new BusinessException(ResultCode.NO_PERMISSION);
        }
    }

    /**
     * 查询周报（@TableLogic 自动过滤软删，软删/不存在同视为 3010）
     */
    private WeeklyReportDo requireReport(String id) {
        WeeklyReportDo entity = weeklyReportRepository.getById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.REPORT_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 周期校验：起止倒置或跨度超过 31 天均为参数错误
     */
    private void validatePeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessException(ResultCode.BAD_PARAM, "统计周期起止倒置");
        }
        if (ChronoUnit.DAYS.between(periodStart, periodEnd) + 1 > MAX_PERIOD_SPAN) {
            throw new BusinessException(ResultCode.BAD_PARAM, "统计周期跨度不能超过 31 天");
        }
    }

    /**
     * 条目校验并归一：null 视为空段；任务说明去首尾空格后必填且不超过 500 字符，
     * 进度可空但须在 0-100 之间，人员可空且不超过 100 字符；条数不超过 100
     */
    private List<WeeklyReportEntry> validateEntries(List<WeeklyReportEntry> entries, String sectionName) {
        if (entries == null) {
            return List.of();
        }
        if (entries.size() > MAX_ENTRY_COUNT) {
            throw new BusinessException(ResultCode.BAD_PARAM, sectionName + "条目不能超过 " + MAX_ENTRY_COUNT + " 条");
        }
        List<WeeklyReportEntry> normalized = new ArrayList<>();
        for (WeeklyReportEntry entry : entries) {
            String text = entry.getText() == null ? "" : entry.getText().trim();
            if (text.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_PARAM, sectionName + "任务说明不能为空");
            }
            if (text.length() > MAX_ENTRY_LENGTH) {
                throw new BusinessException(ResultCode.BAD_PARAM,
                        sectionName + "任务说明不能超过 " + MAX_ENTRY_LENGTH + " 字符");
            }
            Integer progress = entry.getProgress();
            if (progress != null && (progress < 0 || progress > 100)) {
                throw new BusinessException(ResultCode.BAD_PARAM, sectionName + "进度需在 0-100 之间");
            }
            String person = entry.getPerson() == null ? null : entry.getPerson().trim();
            if (person != null && person.isEmpty()) {
                person = null;
            }
            if (person != null && person.length() > MAX_ENTRY_PERSON_LENGTH) {
                throw new BusinessException(ResultCode.BAD_PARAM,
                        sectionName + "人员最长 " + MAX_ENTRY_PERSON_LENGTH + " 字符");
            }
            normalized.add(new WeeklyReportEntry(text, progress, person));
        }
        return normalized;
    }

    /**
     * 批量翻译执行人显示名
     */
    private Map<String, String> loadAssigneeNames(List<TaskInfoDo> tasks) {
        Set<String> assigneeIds = tasks.stream()
                .map(TaskInfoDo::getAssigneeId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (assigneeIds.isEmpty()) {
            return Map.of();
        }
        return sysUserRepository.listByIds(assigneeIds).stream()
                .collect(Collectors.toMap(SysUserDo::getId, SysUserDo::getDisplayName));
    }

    /**
     * 项目显示名（项目可能已被删除，取不到时回退空串由排序归一）
     */
    private String projectDisplayName(String projectId) {
        ProjectInfoDo project = projectInfoRepository.getById(projectId);
        return project == null ? "" : project.getProjectName();
    }

    private WeeklyReportVo toVo(WeeklyReportDo entity, String projectName) {
        WeeklyReportVo vo = new WeeklyReportVo();
        BeanUtils.copyProperties(entity, vo);
        vo.setProjectName(projectName);
        return vo;
    }
}
