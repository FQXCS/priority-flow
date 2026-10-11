package com.sdecloud.priorityflow.auth.bean.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 项目简要信息 VO（me/permissions 下发用）。
 *
 * @author priority-flow
 */
@Data
public class ProjectSimpleVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 项目 ID */
    private String projectId;

    /** 项目名称 */
    private String projectName;

    /** 项目编号前缀 */
    private String projectKey;
}
