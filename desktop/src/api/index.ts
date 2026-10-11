import { http, unwrap } from './client';
import type {
  AttachmentVo,
  BoardVo,
  CommentVo,
  CrossProjectWorkloadVo,
  LoginVo,
  MePermissionsVo,
  NotificationVo,
  PageResult,
  PermissionCatalogVo,
  PresignVo,
  ProjectMemberVo,
  ProjectVo,
  RoleVo,
  TaskLogVo,
  TaskVo,
  UserVo,
  WorkloadVo,
} from './types';

// ---------------- 认证 ----------------
export const authApi = {
  login: (username: string, password: string) => unwrap<LoginVo>(http.post('/api/auth/login', { username, password })),
  logout: (refreshToken?: string) => http.post('/api/auth/logout', refreshToken ? { refreshToken } : {}),
  profile: () => unwrap<UserVo>(http.get('/api/me/profile')),
  permissions: (projectId?: string) =>
    unwrap<MePermissionsVo>(http.get(`/api/me/permissions${projectId ? `?projectId=${projectId}` : ''}`)),
  permissionCatalog: () => unwrap<PermissionCatalogVo>(http.get('/api/me/permission-catalog')),
  changePassword: (oldPassword: string, newPassword: string) =>
    http.put('/api/me/password', { oldPassword, newPassword }),
  updateSettings: (settings: Record<string, unknown>) => http.put('/api/me/settings', { settings }),
};

// ---------------- 项目 / 成员 / 角色 ----------------
export const projectApi = {
  listMy: () => unwrap<ProjectVo[]>(http.get('/api/projects')),
  create: (body: { projectName: string; projectKey: string; distributorUserId: string; remark?: string }) =>
    unwrap<ProjectVo>(http.post('/api/projects', body)),
  detail: (projectId: string) => unwrap<ProjectVo>(http.get(`/api/projects/${projectId}`)),
  listAll: () => unwrap<ProjectVo[]>(http.get('/api/projects/all')),
  listMembers: (projectId: string) => unwrap<ProjectMemberVo[]>(http.get(`/api/projects/${projectId}/members`)),
  // 可添加为成员的用户候选列表（后端排除已有成员与禁用用户）
  listMemberCandidates: (projectId: string) =>
    unwrap<UserVo[]>(http.get(`/api/projects/${projectId}/member-candidates`)),
  addMember: (projectId: string, userId: string) =>
    http.post(`/api/projects/${projectId}/members`, { userId }),
  removeMember: (projectId: string, userId: string) =>
    http.delete(`/api/projects/${projectId}/members/${userId}`),
  updateMemberRoles: (projectId: string, userId: string, roleIds: string[]) =>
    http.put(`/api/projects/${projectId}/members/${userId}/roles`, { roleIds }),
  listRoles: (projectId: string) => unwrap<RoleVo[]>(http.get(`/api/projects/${projectId}/roles`)),
  createRole: (projectId: string, body: { roleName: string; permissions: string[] }) =>
    unwrap<RoleVo>(http.post(`/api/projects/${projectId}/roles`, body)),
  updateRole: (projectId: string, roleId: string, body: { roleName: string; permissions: string[] }) =>
    unwrap<RoleVo>(http.put(`/api/projects/${projectId}/roles/${roleId}`, body)),
  deleteRole: (projectId: string, roleId: string) => http.delete(`/api/projects/${projectId}/roles/${roleId}`),
};

// ---------------- 任务 ----------------
export interface BoardQuery {
  assigneeId?: string;
  priority?: string;
  taskType?: string;
  overdueOnly?: boolean;
  keyword?: string;
}

export const taskApi = {
  create: (body: Record<string, unknown>) => unwrap<TaskVo>(http.post('/api/tasks', body)),
  update: (taskId: string, body: Record<string, unknown>) => unwrap<TaskVo>(http.put(`/api/tasks/${taskId}`, body)),
  deleteDraft: (taskId: string) => http.delete(`/api/tasks/${taskId}`),
  detail: (taskId: string) => unwrap<TaskVo>(http.get(`/api/tasks/${taskId}`)),
  publish: (taskId: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/publish`)),
  withdraw: (taskId: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/withdraw`)),
  distribute: (taskId: string, assigneeId: string) =>
    unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/distribute`, { assigneeId })),
  start: (taskId: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/start`)),
  switchBack: (taskId: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/switch-back`)),
  complete: (taskId: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/complete`)),
  close: (taskId: string, reason: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/close`, { reason })),
  reopen: (taskId: string, reason: string) => unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/reopen`, { reason })),
  changePriority: (taskId: string, priority: string) =>
    unwrap<TaskVo>(http.post(`/api/tasks/${taskId}/priority`, { priority })),
  sortRank: (taskId: string, beforeId?: string, afterId?: string) =>
    unwrap<TaskVo>(http.put(`/api/tasks/${taskId}/sort-rank`, { beforeId, afterId })),
  board: (projectId: string, query: BoardQuery) =>
    unwrap<BoardVo>(http.get(`/api/projects/${projectId}/board`, { params: query })),
  mine: (scope: string) => unwrap<TaskVo[]>(http.get('/api/tasks/mine', { params: { scope } })),
  myDrafts: (pageNum = 1, pageSize = 50) =>
    unwrap<PageResult<TaskVo>>(http.get('/api/tasks/my-drafts', { params: { pageNum, pageSize } })),
  workload: (projectId: string) => unwrap<WorkloadVo[]>(http.get(`/api/projects/${projectId}/workload`)),
  crossProjectWorkload: () => unwrap<CrossProjectWorkloadVo[]>(http.get('/api/workload/cross-project')),
  logs: (taskId: string) => unwrap<TaskLogVo[]>(http.get(`/api/tasks/${taskId}/logs`)),
};

// ---------------- 评论 / 附件 ----------------
export const collabApi = {
  listComments: (taskId: string) => unwrap<CommentVo[]>(http.get(`/api/tasks/${taskId}/comments`)),
  createComment: (taskId: string, content: string, mentions: string[]) =>
    unwrap<CommentVo>(http.post(`/api/tasks/${taskId}/comments`, { content, mentions })),
  presign: (taskId: string, fileName: string, contentType?: string) =>
    unwrap<PresignVo>(http.post(`/api/tasks/${taskId}/attachments/presign`, { fileName, contentType })),
  registerAttachment: (taskId: string, body: { fileName: string; objectKey: string; fileSize: number; contentType?: string }) =>
    unwrap<AttachmentVo>(http.post(`/api/tasks/${taskId}/attachments`, body)),
  listAttachments: (taskId: string) => unwrap<AttachmentVo[]>(http.get(`/api/tasks/${taskId}/attachments`)),
};

// ---------------- 通知 ----------------
export const notificationApi = {
  list: (pageNum = 1, pageSize = 20) =>
    unwrap<NotificationVo[]>(http.get('/api/notifications', { params: { pageNum, pageSize } })),
  unreadCount: () => unwrap<number>(http.get('/api/notifications/unread-count')),
  markRead: (id: string) => http.put(`/api/notifications/${id}/read`),
  markAllRead: () => http.put('/api/notifications/read-all'),
};

// ---------------- 系统管理 ----------------
export const systemApi = {
  listUsers: (keyword: string, pageNum = 1, pageSize = 50) =>
    unwrap<PageResult<UserVo>>(http.get('/api/system/users', { params: { keyword, pageNum, pageSize } })),
  createUser: (body: { username: string; displayName: string; email?: string; initialPassword: string; remark?: string }) =>
    unwrap<UserVo>(http.post('/api/system/users', body)),
  updateUser: (userId: string, body: { displayName?: string; email?: string; status?: string; remark?: string }) =>
    unwrap<UserVo>(http.put(`/api/system/users/${userId}`, body)),
  resetPassword: (userId: string, newPassword: string) =>
    http.put(`/api/system/users/${userId}/reset-password`, { newPassword }),
  listProjects: () => unwrap<ProjectVo[]>(http.get('/api/system/projects')),
};
