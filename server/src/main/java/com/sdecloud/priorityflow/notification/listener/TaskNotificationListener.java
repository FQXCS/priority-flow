package com.sdecloud.priorityflow.notification.listener;

import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.common.event.TaskChangedEvent;
import com.sdecloud.priorityflow.notification.service.NotificationService;
import com.sdecloud.priorityflow.project.constant.ProjectConstants;
import com.sdecloud.priorityflow.project.persistence.po.ProjectInfoDo;
import com.sdecloud.priorityflow.project.persistence.po.ProjectRoleDo;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectInfoRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectMemberRoleRepository;
import com.sdecloud.priorityflow.project.persistence.repository.ProjectRoleRepository;
import com.sdecloud.priorityflow.task.enums.TaskActionEnum;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 站内通知监听器：按 PRD 通知矩阵查收件人（创建者/执行人/项目经理们/分发者们，
 * 去重、排除操作人），写 notification_info 并向 /user/queue/notify 推送。
 *
 * @author priority-flow
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskNotificationListener {

    private final TaskInfoRepository taskInfoRepository;

    private final SysUserRepository sysUserRepository;

    private final ProjectRoleRepository projectRoleRepository;

    private final ProjectMemberRoleRepository projectMemberRoleRepository;

    private final ProjectInfoRepository projectInfoRepository;

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskChanged(TaskChangedEvent event) {
        try {
            TaskInfoDo task = taskInfoRepository.getById(event.getTaskId());
            if (task == null) {
                // 任务删除事件等场景：仅评论提及需要通知
                if ("COMMENT_CREATED".equals(event.getChangeType())) {
                    notifyComment(event, null);
                }
                return;
            }
            String taskNo = buildTaskNo(task);
            String operatorName = displayName(event.getOperatorId());
            switch (event.getChangeType()) {
                case "PUBLISH" -> notifyExcludingOperator(event, recipients(event, task, List.of("CREATOR", "DISTRIBUTORS")),
                        TaskActionEnum.PUBLISH.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已由 "
                                + operatorName + " 发布，等待分发");
                case "WITHDRAW" -> notifyExcludingOperator(event, recipients(event, task, List.of("DISTRIBUTORS")),
                        TaskActionEnum.WITHDRAW.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已被 "
                                + operatorName + " 撤回待分发");
                case "DISTRIBUTE" -> notifyExcludingOperator(event, recipients(event, task, List.of("ASSIGNEE")),
                        TaskActionEnum.DISTRIBUTE.getLabel(), "你有新任务：「" + taskNo + " " + task.getTitle()
                                + "」（由 " + operatorName + " 分发）");
                case "START" -> notifyExcludingOperator(event, recipients(event, task, List.of("CREATOR", "DISTRIBUTORS")),
                        TaskActionEnum.START.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已由 "
                                + operatorName + " 开始执行");
                case "SWITCH_BACK" -> notifyExcludingOperator(event, recipients(event, task, List.of("CREATOR", "DISTRIBUTORS")),
                        TaskActionEnum.SWITCH_BACK.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已由 "
                                + operatorName + " 切换回等待中");
                case "COMPLETE" -> notifyExcludingOperator(event,
                        recipients(event, task, List.of("CREATOR", "DISTRIBUTORS", "PMS")),
                        TaskActionEnum.COMPLETE.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已由 "
                                + operatorName + " 完成");
                case "CLOSE" -> notifyExcludingOperator(event, recipients(event, task, List.of("ASSIGNEE", "CREATOR")),
                        TaskActionEnum.CLOSE.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已由 "
                                + operatorName + " 关闭" + (event.getReason() == null ? "" : "，原因：" + event.getReason()));
                case "REOPEN" -> notifyExcludingOperator(event, recipients(event, task, List.of("ASSIGNEE")),
                        TaskActionEnum.REOPEN.getLabel(), "「" + taskNo + " " + task.getTitle() + "」已由 "
                                + operatorName + " 重新打开" + (event.getReason() == null ? "" : "，原因：" + event.getReason()));
                case "PRIORITY_CHANGED" -> notifyExcludingOperator(event,
                        task.getAssigneeId() == null ? List.of() : List.of(task.getAssigneeId()),
                        "优先级变更", "「" + taskNo + " " + task.getTitle() + "」优先级已变更为 "
                                + (task.getPriority() == null ? "-" : task.getPriority().getLabel()));
                case "COMMENT_CREATED" -> notifyComment(event, task);
                default -> {
                    // TASK_CREATED/TASK_UPDATED/TASK_SORTED/TASK_DELETED 等不发通知，仅 WS 看板推送
                }
            }
        } catch (Exception e) {
            // 通知失败不影响主流程
            log.error("任务通知处理失败：taskId={}, changeType={}", event.getTaskId(), event.getChangeType(), e);
        }
    }

    /**
     * 评论通知：@ 的人 + 创建者/执行人（去重、排除作者）
     */
    private void notifyComment(TaskChangedEvent event, TaskInfoDo task) {
        Set<String> recipients = new LinkedHashSet<>();
        if (event.getMentions() != null) {
            recipients.addAll(event.getMentions());
        }
        if (task != null) {
            if (task.getCreatorId() != null) {
                recipients.add(task.getCreatorId());
            }
            if (task.getAssigneeId() != null) {
                recipients.add(task.getAssigneeId());
            }
        }
        recipients.remove(event.getOperatorId());
        if (recipients.isEmpty()) {
            return;
        }
        String title = "新评论";
        String content = displayName(event.getOperatorId()) + " 在任务「"
                + (task == null ? event.getTaskId() : buildTaskNo(task) + " " + task.getTitle())
                + "」中发表了评论" + (event.getMentions() != null && !event.getMentions().isEmpty() ? "并 @ 了你" : "");
        notificationService.notifyUsers(List.copyOf(recipients), "COMMENT_CREATED", title, content, "TASK", event.getTaskId());
    }

    /**
     * 收件人解析：CREATOR/ASSIGNEE/DISTRIBUTORS（绑定分发者角色的成员）/PMS（绑定项目经理角色的成员）
     */
    private List<String> recipients(TaskChangedEvent event, TaskInfoDo task, List<String> targets) {
        Set<String> recipients = new LinkedHashSet<>();
        for (String target : targets) {
            switch (target) {
                case "CREATOR" -> {
                    if (task.getCreatorId() != null) {
                        recipients.add(task.getCreatorId());
                    }
                }
                case "ASSIGNEE" -> {
                    if (task.getAssigneeId() != null) {
                        recipients.add(task.getAssigneeId());
                    }
                }
                case "DISTRIBUTORS", "PMS" -> recipients.addAll(
                        usersWithRoleCode(task.getProjectId(),
                                "DISTRIBUTORS".equals(target) ? ProjectConstants.ROLE_DISTRIBUTOR : ProjectConstants.ROLE_PM));
                default -> {
                    // 未知目标忽略
                }
            }
        }
        recipients.remove(event.getOperatorId());
        return List.copyOf(recipients);
    }

    private void notifyExcludingOperator(TaskChangedEvent event, List<String> userIds, String title, String content) {
        if (userIds.isEmpty()) {
            return;
        }
        notificationService.notifyUsers(userIds, event.getChangeType(), title, content, "TASK", event.getTaskId());
    }

    /**
     * 项目内绑定指定角色编码的全部用户
     */
    private List<String> usersWithRoleCode(String projectId, String roleCode) {
        ProjectRoleDo role = projectRoleRepository.findByCode(projectId, roleCode);
        if (role == null) {
            return List.of();
        }
        List<String> userIds = projectMemberRoleRepository.listUserIdsByRole(projectId, role.getId());
        if (userIds.isEmpty()) {
            return List.of();
        }
        // 过滤已删除/禁用用户
        List<String> valid = new ArrayList<>();
        sysUserRepository.listByIds(userIds).forEach(user -> {
            if ("0".equals(user.getStatus())) {
                valid.add(user.getId());
            }
        });
        return valid;
    }

    private String displayName(String userId) {
        if (userId == null) {
            return "未知用户";
        }
        SysUserDo user = sysUserRepository.getById(userId);
        return user == null ? userId : user.getDisplayName();
    }

    private String buildTaskNo(TaskInfoDo task) {
        // 任务编号 = {project_key}-{seq_no}
        ProjectInfoDo project = projectInfoRepository.getById(task.getProjectId());
        String key = project == null ? "" : project.getProjectKey();
        return key + "-" + task.getSeqNo();
    }
}
