-- =====================================================================
-- 存量项目内置角色补齐里程碑权限点（代码默认集更新不会回写历史角色快照）：
--   DISTRIBUTOR（权限全集语义）补 view + manage；
--   PM / EXECUTOR / OBSERVER 仅补 view（查看默认对成员开放，管理权由分发者按需授予）；
--   自定义角色不动。LIKE 判重保证幂等。
-- =====================================================================
UPDATE project_role
SET permissions = JSON_MERGE_PRESERVE(permissions, '["pf:milestone:view","pf:milestone:manage"]')
WHERE role_code = 'DISTRIBUTOR'
  AND del_flag = '0'
  AND permissions IS NOT NULL
  AND permissions NOT LIKE '%pf:milestone:view%';

UPDATE project_role
SET permissions = JSON_MERGE_PRESERVE(permissions, '["pf:milestone:view"]')
WHERE role_code IN ('PM', 'EXECUTOR', 'OBSERVER')
  AND del_flag = '0'
  AND permissions IS NOT NULL
  AND permissions NOT LIKE '%pf:milestone:view%';
