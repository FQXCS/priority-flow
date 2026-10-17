package com.sdecloud.priorityflow.milestone.persistence.po;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sdecloud.priorityflow.common.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 项目规划里程碑实体。
 *
 * @author priority-flow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("milestone")
public class MilestoneDo extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 所属项目 ID */
    private String projectId;

    /** 目标名称 */
    private String name;

    /** 负责人用户 ID（项目成员，可为空） */
    private String ownerId;

    /** 预计完成日期（空 = 未规划日期；序列化固定 yyyy-MM-dd 供前端 date 输入框直接使用） */
    @JSONField(format = "yyyy-MM-dd")
    private LocalDate expectedDate;
}
