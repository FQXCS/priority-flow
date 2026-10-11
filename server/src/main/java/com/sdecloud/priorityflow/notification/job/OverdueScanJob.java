package com.sdecloud.priorityflow.notification.job;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.notification.service.NotificationService;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import com.sdecloud.priorityflow.task.enums.TaskStatusEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 逾期扫描任务：每小时扫 deadline < now 且状态为等待中/进行中且未发过通知的任务，
 * 通知执行人与创建者，并置 overdue_notified='1'（防重复轰炸）。
 *
 * @author priority-flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueScanJob {

    private final TaskInfoRepository taskInfoRepository;

    private final ProjectInfoRepository projectInfoRepository;

    private final SysUserRepository sysUserRepository;

    private final NotificationService notificationService;

    /**
     * 每小时第 0 分触发
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scanOverdue() {
        LocalDateTime now = LocalDateTime.now();
        List<TaskInfoDo> overdueTasks = taskInfoRepository.listOverdueUnnotified(now);
        if (overdueTasks.isEmpty()) {
            return;
        }
        log.info("逾期扫描：发现 {} 个逾期任务", overdueTasks.size());
        for (TaskInfoDo task : overdueTasks) {
            try {
                String taskNo = buildTaskNo(task);
                String title = "任务逾期提醒";
                String content = "任务「" + taskNo + " " + task.getTitle() + "」已超过截止时间，请尽快处理";
                // 通知执行人与创建者（去重）
                List<String> recipients = java.util.stream.Stream
                        .of(task.getAssigneeId(), task.getCreatorId())
                        .filter(id -> id != null && !"1".equals(userDisabled(id)))
                        .distinct()
                        .toList();
                notificationService.notifyUsers(recipients, "TASK_OVERDUE", title, content, "TASK", task.getId());
                // 置逾期通知已发标记
                task.setOverdueNotified("1");
                taskInfoRepository.updateById(task);
            } catch (Exception e) {
                log.error("逾期通知处理失败：taskId={}", task.getId(), e);
            }
        }
    }

    private String userDisabled(String userId) {
        SysUserDo user = sysUserRepository.getById(userId);
        return user == null ? "1" : user.getStatus();
    }

    private String buildTaskNo(TaskInfoDo task) {
        ProjectInfoDo project = projectInfoRepository.getById(task.getProjectId());
        String key = project == null ? "" : project.getProjectKey();
        return key + "-" + task.getSeqNo();
    }
}
