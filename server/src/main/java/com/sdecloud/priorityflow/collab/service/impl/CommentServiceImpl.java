package com.sdecloud.priorityflow.collab.service.impl;

import com.alibaba.fastjson2.JSON;
import com.sdecloud.priorityflow.auth.persistence.po.SysUserDo;
import com.sdecloud.priorityflow.auth.persistence.repository.SysUserRepository;
import com.sdecloud.priorityflow.collab.bean.dto.CommentCreateDto;
import com.sdecloud.priorityflow.common.api.ResultCode;
import com.sdecloud.priorityflow.common.exception.BusinessException;
import com.sdecloud.priorityflow.collab.bean.vo.CommentVo;
import com.sdecloud.priorityflow.collab.persistence.po.TaskCommentDo;
import com.sdecloud.priorityflow.collab.persistence.repository.TaskCommentRepository;
import com.sdecloud.priorityflow.collab.service.CommentService;
import com.sdecloud.priorityflow.common.event.TaskChangedEvent;
import com.sdecloud.priorityflow.common.security.UserContext;
import com.sdecloud.priorityflow.common.util.XssUtil;
import com.sdecloud.priorityflow.task.persistence.po.TaskInfoDo;
import com.sdecloud.priorityflow.task.persistence.repository.TaskInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论服务实现。
 *
 * @author priority-flow
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final TaskCommentRepository taskCommentRepository;

    private final TaskInfoRepository taskInfoRepository;

    private final SysUserRepository sysUserRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<CommentVo> listByTask(String taskId) {
        List<TaskCommentDo> comments = taskCommentRepository.listByTaskId(taskId);
        // 作者 + 提及人批量翻译
        Set<String> userIds = new HashSet<>();
        comments.forEach(c -> {
            userIds.add(c.getAuthorId());
            userIds.addAll(JSON.parseArray(c.getMentions() == null ? "[]" : c.getMentions(), String.class));
        });
        Map<String, SysUserDo> userMap = userIds.isEmpty() ? Map.of()
                : sysUserRepository.listByIds(userIds).stream()
                        .collect(Collectors.toMap(SysUserDo::getId, Function.identity()));
        return comments.stream().map(comment -> {
            CommentVo vo = new CommentVo();
            BeanUtils.copyProperties(comment, vo);
            SysUserDo author = userMap.get(comment.getAuthorId());
            vo.setAuthorName(author == null ? comment.getAuthorId() : author.getDisplayName());
            List<String> mentions = JSON.parseArray(comment.getMentions() == null ? "[]" : comment.getMentions(), String.class);
            vo.setMentionList(mentions);
            vo.setMentionNameList(mentions.stream()
                    .map(id -> userMap.containsKey(id) ? userMap.get(id).getDisplayName() : id)
                    .toList());
            return vo;
        }).toList();
    }

    @Override
    public CommentVo create(String taskId, CommentCreateDto dto) {
        String userId = UserContext.require().getUserId();
        TaskInfoDo task = taskInfoRepository.getById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.TASK_NOT_FOUND);
        }
        // mentions 去重（前端选择器插入 user id 标记）
        List<String> mentions = dto.getMentions() == null ? List.of()
                : dto.getMentions().stream().distinct().toList();

        TaskCommentDo comment = new TaskCommentDo();
        comment.setTaskId(taskId);
        comment.setAuthorId(userId);
        comment.setContent(XssUtil.cleanText(dto.getContent()));
        comment.setMentions(JSON.toJSONString(mentions));
        taskCommentRepository.save(comment);

        // 发布评论事件：@ 的人额外通知（监听器统一查收件人列表）
        eventPublisher.publishEvent(new TaskChangedEvent(this, taskId, task.getProjectId(),
                "COMMENT_CREATED", userId, mentions, null));

        CommentVo vo = new CommentVo();
        BeanUtils.copyProperties(comment, vo);
        vo.setAuthorName(UserContext.require().getDisplayName());
        vo.setMentionList(mentions);
        vo.setMentionNameList(mentions);
        return vo;
    }
}
