package com.sdecloud.priorityflow.collab.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务评论实体。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_comment")
public class TaskCommentDo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String taskId;

    /** 作者 ID */
    private String authorId;

    /** 评论内容 */
    private String content;

    /** @ 提及的用户 ID 集合（JSON 数组文本） */
    private String mentions;
}
