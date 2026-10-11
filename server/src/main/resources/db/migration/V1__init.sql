-- =====================================================================
-- Priority Flow 初始化表结构（MySQL 8.0 / utf8mb4）
-- 规范：id 第一列；审计字段放最后（create_by → create_time → update_by →
--       update_time → remark → del_flag）；枚举一律 varchar 存字符串常量；
--       关联子表精简为业务字段 + create_by/create_time + del_flag
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统用户（自建账号体系）
-- ---------------------------------------------------------------------
CREATE TABLE sys_user (
    id            varchar(128)  NOT NULL COMMENT '主键UUID',
    username      varchar(64)   NOT NULL COMMENT '登录名（唯一）',
    password_hash varchar(128)  NOT NULL COMMENT 'BCrypt密码哈希',
    display_name  varchar(64)   NOT NULL COMMENT '显示名',
    email         varchar(128)  DEFAULT NULL COMMENT '邮箱',
    avatar_url    varchar(512)  DEFAULT NULL COMMENT '头像地址',
    settings      text          COMMENT '个人偏好设置(JSON：主题/密度等)',
    status        varchar(2)    NOT NULL DEFAULT '0' COMMENT '状态(0启用/1禁用)',
    is_admin      varchar(2)    NOT NULL DEFAULT '0' COMMENT '是否系统管理员(0否/1是)',
    create_by     varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time   timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by     varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time   timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark        text          COMMENT '备注',
    del_flag      varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户';

-- ---------------------------------------------------------------------
-- 用户刷新令牌（服务端存库，可吊销）
-- ---------------------------------------------------------------------
CREATE TABLE sys_user_token (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    user_id     varchar(128)  NOT NULL COMMENT '用户ID',
    token_hash  varchar(128)  NOT NULL COMMENT '刷新令牌SHA-256哈希',
    expires_at  datetime      NOT NULL COMMENT '过期时间',
    revoked     varchar(2)    NOT NULL DEFAULT '0' COMMENT '是否已吊销(0否/1是)',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by   varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark      text          COMMENT '备注',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_token_hash (token_hash),
    KEY idx_user_token_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户刷新令牌';

-- ---------------------------------------------------------------------
-- 项目信息（管理员创建，创建时指定第一任分发者）
-- ---------------------------------------------------------------------
CREATE TABLE project_info (
    id           varchar(128)  NOT NULL COMMENT '主键UUID',
    project_name varchar(128)  NOT NULL COMMENT '项目名称',
    project_key  varchar(16)   NOT NULL COMMENT '任务编号前缀(如PF)，唯一',
    status       varchar(2)    NOT NULL DEFAULT '0' COMMENT '状态(0正常/1归档)',
    create_by    varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time  timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by    varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time  timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark       text          COMMENT '备注',
    del_flag     varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_key (project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目信息';

-- ---------------------------------------------------------------------
-- 项目成员（子表：精简字段）
-- ---------------------------------------------------------------------
CREATE TABLE project_member (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    project_id  varchar(128)  NOT NULL COMMENT '项目ID',
    user_id     varchar(128)  NOT NULL COMMENT '用户ID',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_member (project_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目成员';

-- ---------------------------------------------------------------------
-- 项目角色（内置 DISTRIBUTOR/PM/EXECUTOR/OBSERVER + 自定义角色）
-- permissions 为 JSON 权限点数组文本
-- ---------------------------------------------------------------------
CREATE TABLE project_role (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    project_id  varchar(128)  NOT NULL COMMENT '项目ID',
    role_name   varchar(64)   NOT NULL COMMENT '角色名称',
    role_code   varchar(64)   NOT NULL COMMENT '角色编码(DISTRIBUTOR/PM/EXECUTOR/OBSERVER或自定义)',
    permissions text          COMMENT '权限点集合(JSON数组)',
    is_builtin  varchar(2)    NOT NULL DEFAULT '0' COMMENT '是否内置角色(0否/1是)',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by   varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark      text          COMMENT '备注',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    KEY idx_project_role_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目角色';

-- ---------------------------------------------------------------------
-- 成员-角色绑定（成员可绑多角色，权限取并集）
-- ---------------------------------------------------------------------
CREATE TABLE project_member_role (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    project_id  varchar(128)  NOT NULL COMMENT '项目ID',
    user_id     varchar(128)  NOT NULL COMMENT '用户ID',
    role_id     varchar(128)  NOT NULL COMMENT '角色ID',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_role (project_id, user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='成员角色绑定';

-- ---------------------------------------------------------------------
-- 任务信息（核心表，一期不含子任务）
-- ---------------------------------------------------------------------
CREATE TABLE task_info (
    id               varchar(128)   NOT NULL COMMENT '主键UUID',
    project_id       varchar(128)   NOT NULL COMMENT '所属项目ID',
    seq_no           int            NOT NULL COMMENT '项目内递增序号，任务编号={project_key}-{seq_no}',
    title            varchar(255)   NOT NULL COMMENT '标题',
    description      text           COMMENT '富文本描述(存储前已XSS清洗)',
    priority         varchar(16)    DEFAULT NULL COMMENT '优先级(URGENT/HIGH/MEDIUM/LOW)',
    task_type        varchar(16)    DEFAULT NULL COMMENT '任务类型(REQUIREMENT/DEVELOP/TEST/BUG/DESIGN/OPS/OTHER)',
    task_status      varchar(32)    NOT NULL COMMENT '状态(DRAFT/PENDING_DIST/WAITING/IN_PROGRESS/COMPLETED/CLOSED)',
    creator_id       varchar(128)   NOT NULL COMMENT '创建人=草稿归属人(业务字段，归属永不变更)',
    assignee_id      varchar(128)   DEFAULT NULL COMMENT '执行人(分发后必填)',
    distributor_id   varchar(128)   DEFAULT NULL COMMENT '分发操作人',
    labels           text           COMMENT '自由标签(JSON字符串数组)',
    estimated_hours  decimal(6,1)   DEFAULT NULL COMMENT '预计工时(小时)',
    remaining_hours  decimal(6,1)   DEFAULT NULL COMMENT '剩余工时(小时)',
    expected_finish_at datetime     DEFAULT NULL COMMENT '预计完成时间',
    deadline         datetime       DEFAULT NULL COMMENT '截止时间',
    sort_rank        varchar(50)    DEFAULT NULL COMMENT 'LexoRank排序键(草稿/已完成/已关闭为NULL；进行中不参与排序)',
    published_time   datetime       DEFAULT NULL COMMENT '发布时间',
    distributed_time datetime       DEFAULT NULL COMMENT '分发时间',
    started_time     datetime       DEFAULT NULL COMMENT '开始时间',
    completed_time   datetime       DEFAULT NULL COMMENT '完成时间',
    closed_time      datetime       DEFAULT NULL COMMENT '关闭时间',
    overdue_notified varchar(2)     NOT NULL DEFAULT '0' COMMENT '逾期通知已发标记(0未发/1已发)',
    version          int            NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_by        varchar(128)   DEFAULT NULL COMMENT '创建者',
    create_time      timestamp(6)   DEFAULT NULL COMMENT '创建时间',
    update_by        varchar(128)   DEFAULT NULL COMMENT '更新者',
    update_time      timestamp(6)   DEFAULT NULL COMMENT '更新时间',
    remark           text           COMMENT '备注',
    del_flag         varchar(2)     NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_project_seq (project_id, seq_no),
    KEY idx_task_project_status_rank (project_id, task_status, sort_rank),
    KEY idx_task_assignee_status_rank (assignee_id, task_status, sort_rank),
    KEY idx_task_creator_status (creator_id, task_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务信息';

-- ---------------------------------------------------------------------
-- 任务操作日志（一张表同时满足状态历史与操作日志）
-- ---------------------------------------------------------------------
CREATE TABLE task_operation_log (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    task_id     varchar(128)  NOT NULL COMMENT '任务ID',
    project_id  varchar(128)  NOT NULL COMMENT '项目ID',
    operator_id varchar(128)  NOT NULL COMMENT '操作人ID',
    action      varchar(32)   NOT NULL COMMENT '动作(发布/分发/开始等)',
    from_status varchar(32)   DEFAULT NULL COMMENT '原状态',
    to_status   varchar(32)   DEFAULT NULL COMMENT '新状态',
    reason      varchar(512)  DEFAULT NULL COMMENT '原因备注',
    detail      text          COMMENT '详情(JSON扩展信息)',
    ip          varchar(64)   DEFAULT NULL COMMENT '来源IP',
    device      varchar(255)  DEFAULT NULL COMMENT '设备(User-Agent摘要)',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by   varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark      text          COMMENT '备注',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    KEY idx_task_log_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务操作日志';

-- ---------------------------------------------------------------------
-- 任务评论
-- ---------------------------------------------------------------------
CREATE TABLE task_comment (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    task_id     varchar(128)  NOT NULL COMMENT '任务ID',
    author_id   varchar(128)  NOT NULL COMMENT '作者ID',
    content     text          NOT NULL COMMENT '评论内容',
    mentions    text          COMMENT '@提及的用户ID集合(JSON数组)',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by   varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark      text          COMMENT '备注',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    KEY idx_task_comment_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务评论';

-- ---------------------------------------------------------------------
-- 任务附件（MinIO object_key + 预签名 URL 直传）
-- ---------------------------------------------------------------------
CREATE TABLE task_attachment (
    id           varchar(128)  NOT NULL COMMENT '主键UUID',
    task_id      varchar(128)  NOT NULL COMMENT '任务ID',
    file_name    varchar(255)  NOT NULL COMMENT '文件名',
    object_key   varchar(512)  NOT NULL COMMENT 'MinIO对象键',
    file_size    bigint        NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
    content_type varchar(128)  DEFAULT NULL COMMENT '内容类型',
    uploader_id  varchar(128)  NOT NULL COMMENT '上传人ID',
    create_by    varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time  timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by    varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time  timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark       text          COMMENT '备注',
    del_flag     varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    KEY idx_task_attachment_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务附件';

-- ---------------------------------------------------------------------
-- 站内通知（桌面通知由客户端收 WS 推送后调 Electron API）
-- ---------------------------------------------------------------------
CREATE TABLE notification_info (
    id          varchar(128)  NOT NULL COMMENT '主键UUID',
    user_id     varchar(128)  NOT NULL COMMENT '接收人ID',
    notify_type varchar(32)   NOT NULL COMMENT '通知类型',
    title       varchar(255)  NOT NULL COMMENT '标题',
    content     varchar(1024) DEFAULT NULL COMMENT '内容',
    ref_type    varchar(32)   DEFAULT NULL COMMENT '关联对象类型',
    ref_id      varchar(128)  DEFAULT NULL COMMENT '关联对象ID',
    is_read     varchar(2)    NOT NULL DEFAULT '0' COMMENT '是否已读(0未读/1已读)',
    read_time   datetime      DEFAULT NULL COMMENT '已读时间',
    create_by   varchar(128)  DEFAULT NULL COMMENT '创建者',
    create_time timestamp(6)  DEFAULT NULL COMMENT '创建时间',
    update_by   varchar(128)  DEFAULT NULL COMMENT '更新者',
    update_time timestamp(6)  DEFAULT NULL COMMENT '更新时间',
    remark      text          COMMENT '备注',
    del_flag    varchar(2)    NOT NULL DEFAULT '0' COMMENT '删除标记(0正常/1删除)',
    PRIMARY KEY (id),
    KEY idx_notification_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内通知';
