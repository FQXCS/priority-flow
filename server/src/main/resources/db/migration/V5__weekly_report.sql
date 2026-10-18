-- =====================================================================
-- 计划周报：weekly_report_info 建表 + 存量项目内置角色补齐周报权限点。
--   DISTRIBUTOR / PM 补 view + manage（PM 可编辑周报）；
--   EXECUTOR / OBSERVER 仅补 view；自定义角色不动。LIKE 判重保证幂等。
-- 同项目同周期唯一由业务层校验（仅查 del_flag='0'），不建数据库唯一索引，
-- 规避软删行阻塞重建（先例：task_info 唯一索引与软删除的已知坑）。
-- =====================================================================
CREATE TABLE weekly_report_info (
    id            VARCHAR(128) NOT NULL COMMENT '主键 UUID',
    project_id    VARCHAR(128) NOT NULL COMMENT '所属项目 ID',
    period_start  DATE         NOT NULL COMMENT '统计周期起始日（含）',
    period_end    DATE         NOT NULL COMMENT '统计周期结束日（含）',
    content       TEXT         NULL COMMENT '本周进展条目（JSON 字符串数组，fastjson）',
    plan_content  TEXT         NULL COMMENT '下周计划条目（JSON 字符串数组）',
    create_by     VARCHAR(128) NULL COMMENT '创建者',
    create_time   TIMESTAMP(6) NULL COMMENT '创建时间',
    update_by     VARCHAR(128) NULL COMMENT '更新者',
    update_time   TIMESTAMP(6) NULL COMMENT '更新时间',
    remark        TEXT         NULL COMMENT '备注',
    del_flag      VARCHAR(2)   DEFAULT '0' COMMENT '删除标记（0 正常 1 删除）',
    PRIMARY KEY (id),
    KEY idx_project_period (project_id, period_start)
) COMMENT '计划周报';

UPDATE project_role
SET permissions = JSON_MERGE_PRESERVE(permissions, '["pf:report:view","pf:report:manage"]')
WHERE role_code IN ('DISTRIBUTOR', 'PM')
  AND del_flag = '0'
  AND permissions IS NOT NULL
  AND permissions NOT LIKE '%pf:report:view%';

UPDATE project_role
SET permissions = JSON_MERGE_PRESERVE(permissions, '["pf:report:view"]')
WHERE role_code IN ('EXECUTOR', 'OBSERVER')
  AND del_flag = '0'
  AND permissions IS NOT NULL
  AND permissions NOT LIKE '%pf:report:view%';
