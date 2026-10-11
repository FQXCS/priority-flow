package com.sdecloud.priorityflow.collab.service;

import com.sdecloud.priorityflow.collab.bean.dto.CommentCreateDto;
import com.sdecloud.priorityflow.collab.bean.vo.CommentVo;

import java.util.List;

/**
 * 评论服务。
 *
 * @author priority-flow
 */
public interface CommentService {

    /**
     * 评论列表（时间正序，附作者/提及人姓名）
     */
    List<CommentVo> listByTask(String taskId);

    /**
     * 发表评论：XSS 清洗、mentions 存 user_id 数组、发布评论事件驱动通知
     */
    CommentVo create(String taskId, CommentCreateDto dto);
}
