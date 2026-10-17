-- =====================================================================
-- 项目规划（里程碑）：项目阶段目标，按预计完成日期在时间轴分组展示；
-- 无状态字段；expected_date 为空进入「未规划日期」；任务关联二期实现。
-- =====================================================================
CREATE TABLE milestone (
    id            varchar(128)  NOT NULL COMMENT '主键UUID',
    project_id    varchar(128)  NOT NULL COMMENT '所属项目ID',
    name          varchar(255)  NOT NULL COMMENT '目标名称',
    owner_id      varchar(128)  DEFAULT NULL COMMENT '负责人用户ID(项目成员)',
    expected_date date          DEFAULT NULL COMMENT '预计完成日期(空=未规划日期)',
    create_by     varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time   timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by     varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time   timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark        text          COMMENT '备注',
    del_flag      varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    KEY idx_milestone_project_date (project_id, expected_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目规划里程碑';
