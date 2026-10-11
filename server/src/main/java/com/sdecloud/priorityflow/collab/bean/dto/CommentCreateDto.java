package com.sdecloud.priorityflow.collab.bean.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建评论入参。
 *
 * @author priority-flow
 */
@Data
public class CommentCreateDto {

    /** 评论内容 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论最长 2000 字符")
    private String content;

    /** @ 提及的用户 ID 列表（前端选择器解析生成） */
    private List<String> mentions;
}
