package com.sdecloud.priorityflow.collab.bean.vo;

import com.sdecloud.priorityflow.collab.persistence.po.TaskCommentDo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 评论 VO（继承 PO，附加作者姓名与提及人姓名）。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentVo extends TaskCommentDo {

    private static final long serialVersionUID = 1L;

    /** 作者姓名 */
    private String authorName;

    /** 提及用户 ID 列表 */
    private List<String> mentionList;

    /** 提及用户姓名列表（与 mentionList 同序） */
    private List<String> mentionNameList;
}
