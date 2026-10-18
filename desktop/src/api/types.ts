/** 后端统一响应结构 */
export interface R<T> {
  code: number;
  msg: string;
  data?: T;
}

/** 分页结果 */
export interface PageResult<T> {
  total: number;
  pageNum: number;
  pageSize: number;
  list: T[];
}

/** 用户信息（password_hash 已由后端隐藏） */
export interface UserVo {
  id: string;
  username: string;
  displayName: string;
  email?: string;
  avatarUrl?: string;
  settings?: string;
  status: string;
  isAdmin: string;
  createTime?: string;
}

/** 登录结果 */
export interface LoginVo {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: UserVo;
}

/** 当前用户权限 */
export interface MePermissionsVo {
  admin: boolean;
  projectList: ProjectSimpleVo[];
  permissions: string[];
  /** 当前可见菜单编码列表（侧边栏与路由守卫使用） */
  menuList: string[];
}

/** 权限点条目（权限目录树叶子节点） */
export interface PermissionItemVo {
  code: string;
  name: string;
}

/** 菜单节点（权限目录树一级节点） */
export interface MenuNodeVo {
  menuCode: string;
  menuName: string;
  sortNo: number;
  /** 准入权限：命中任一即显示菜单；为空 = 登录即可见 */
  accessPermissionList: string[];
  /** 菜单下可配置的权限点 */
  permissionList: PermissionItemVo[];
}

/** 权限目录：「菜单 → 权限点」树（角色编辑用） */
export interface PermissionCatalogVo {
  menuList: MenuNodeVo[];
}

export interface ProjectSimpleVo {
  projectId: string;
  projectName: string;
  projectKey: string;
}

/** 项目 */
export interface ProjectVo {
  id: string;
  projectName: string;
  projectKey: string;
  status: string;
  remark?: string;
  memberCount?: number;
}

/** 角色 */
export interface RoleVo {
  id: string;
  projectId: string;
  roleName: string;
  roleCode: string;
  permissions: string;
  isBuiltin: string;
  permissionList: string[];
}

/** 项目成员 */
export interface ProjectMemberVo {
  id: string;
  projectId: string;
  userId: string;
  user?: UserVo;
  roleList: RoleVo[];
}

/** 任务（继承 PO 的翻译字段） */
export interface TaskVo {
  id: string;
  projectId: string;
  seqNo: number;
  taskNo: string;
  projectKey: string;
  /** 所属项目名称（翻译字段） */
  projectName?: string;
  title: string;
  description?: string;
  priority?: 'URGENT' | 'HIGH' | 'MEDIUM' | 'LOW';
  taskType?: string;
  taskStatus: 'DRAFT' | 'PENDING_DIST' | 'WAITING' | 'IN_PROGRESS' | 'COMPLETED' | 'CLOSED';
  creatorId: string;
  creatorName?: string;
  assigneeId?: string;
  assigneeName?: string;
  distributorId?: string;
  distributorName?: string;
  labels?: string;
  estimatedHours?: number;
  remainingHours?: number;
  /** 进度百分比（0-100，100 仅由完成动作产生） */
  progress?: number;
  expectedFinishAt?: string;
  deadline?: string;
  sortRank?: string;
  publishedTime?: string;
  distributedTime?: string;
  startedTime?: string;
  completedTime?: string;
  closedTime?: string;
  version: number;
  waitingAheadCount?: number;
  remark?: string;
}

/** 看板（六列结构化数据；等待中/进行中为扁平列表，人员筛选走查询参数；跨项目视图时项目元信息为空） */
export interface BoardVo {
  projectId?: string;
  projectName?: string;
  projectKey?: string;
  draftList: TaskVo[];
  pendingDistList: TaskVo[];
  waitingList: TaskVo[];
  inProgressList: TaskVo[];
  completedList: TaskVo[];
  closedList: TaskVo[];
}

/** 人员负载 */
export interface WorkloadVo {
  userId: string;
  displayName: string;
  currentTask: TaskVo | null;
  waitingCount: number;
}

/** 评论 */
export interface CommentVo {
  id: string;
  taskId: string;
  authorId: string;
  authorName: string;
  content: string;
  mentions: string;
  mentionList: string[];
  mentionNameList: string[];
  createTime: string;
}

/** 附件 */
export interface AttachmentVo {
  id: string;
  taskId: string;
  fileName: string;
  objectKey: string;
  fileSize: number;
  contentType?: string;
  uploaderId: string;
  uploaderName: string;
  downloadUrl?: string;
  createTime: string;
}

/** 预签名直传结果 */
export interface PresignVo {
  objectKey: string;
  uploadUrl: string;
  expireSeconds: number;
}

/** 操作日志 */
export interface TaskLogVo {
  id: string;
  taskId: string;
  operatorId: string;
  operatorName: string;
  action: string;
  fromStatus?: string;
  toStatus?: string;
  reason?: string;
  detail?: string;
  ip?: string;
  device?: string;
  createTime: string;
}

/** 站内通知 */
export interface NotificationVo {
  id: string;
  userId: string;
  notifyType: string;
  title: string;
  content?: string;
  refType?: string;
  refId?: string;
  isRead: string;
  readTime?: string;
  createTime: string;
}

/** WS 推送：看板任务变更 */
export interface BoardChangeMessage {
  taskId: string;
  changeType: string;
  operatorId: string;
}

/** 项目规划里程碑 */
export interface MilestoneVo {
  id: string;
  projectId: string;
  name: string;
  ownerId?: string;
  /** 预计完成日期（yyyy-MM-dd；空 = 未规划日期） */
  expectedDate?: string;
  /** 负责人显示名（翻译字段） */
  ownerName?: string;
  createTime?: string;
}

/** WS 推送：里程碑变更（与看板任务消息同主题，按 milestoneId 字段区分） */
export interface MilestoneChangeMessage {
  milestoneId: string;
  changeType: string;
  operatorId: string;
}

/** WS 推送：个人通知 */
export interface NotifyMessage {
  id: string;
  notifyType: string;
  title: string;
  content?: string;
  refType?: string;
  refId?: string;
  channel?: string;
}

/** 权限点常量（与后端 PermissionConstants 一致） */
export const PERMISSIONS = {
  TASK_CREATE_DRAFT: 'pf:task:createDraft',
  TASK_PUBLISH: 'pf:task:publish',
  TASK_VIEW_PROJECT: 'pf:task:viewProject',
  TASK_VIEW_WORKLOAD: 'pf:task:viewWorkload',
  TASK_DISTRIBUTE: 'pf:task:distribute',
  TASK_CHANGE_PRIORITY: 'pf:task:changePriority',
  TASK_DRAG_SORT: 'pf:task:dragSort',
  TASK_DRAG_CROSS: 'pf:task:dragCross',
  TASK_START: 'pf:task:start',
  TASK_SWITCH_BACK: 'pf:task:switchBack',
  TASK_COMPLETE: 'pf:task:complete',
  TASK_CLOSE: 'pf:task:close',
  TASK_REOPEN: 'pf:task:reopen',
  COMMENT_CREATE: 'pf:comment:create',
  ATTACHMENT_UPLOAD: 'pf:attachment:upload',
  MEMBER_MANAGE: 'pf:member:manage',
  ROLE_MANAGE: 'pf:role:manage',
  LOG_VIEW: 'pf:log:view',
  MILESTONE_VIEW: 'pf:milestone:view',
  MILESTONE_MANAGE: 'pf:milestone:manage',
} as const;
