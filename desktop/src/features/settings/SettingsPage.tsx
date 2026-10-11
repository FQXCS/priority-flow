import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi, projectApi, systemApi } from '@/api';
import { useAuthStore } from '@/stores/auth';
import { toast } from '@/stores/toast';
import { useUiStore } from '@/stores/ui';
import { Button } from '@/components/ui/Button';
import { Dialog } from '@/components/ui/Dialog';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Avatar } from '@/components/ui/Avatar';
import { Badge } from '@/components/ui/Badge';
import type { ProjectMemberVo, RoleVo, UserVo } from '@/api/types';

/** 设置页：个人设置 / 项目成员与权限（分发者，页内选项目） / 系统管理（管理员） */
export function SettingsPage() {
  const { admin, hasPermission, projectList } = useAuthStore();
  const [tab, setTab] = useState<'profile' | 'members' | 'system'>('profile');
  // 成员与权限页签的项目上下文页内自选，默认第一个项目
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const memberProjectId = selectedProjectId || projectList[0]?.projectId || '';

  const tabs = [
    { key: 'profile' as const, label: '个人设置' },
    ...(hasPermission('pf:member:manage') || hasPermission('pf:role:manage')
      ? [{ key: 'members' as const, label: '成员与权限' }]
      : []),
    ...(admin ? [{ key: 'system' as const, label: '系统管理' }] : []),
  ];

  return (
    <div className="h-full overflow-y-auto p-5">
      <div className="mx-auto max-w-4xl">
        <div className="mb-4 flex gap-1 rounded-lg bg-zinc-100 p-1 dark:bg-zinc-800">
          {tabs.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={`flex-1 rounded-md px-3 py-1.5 text-sm transition-colors ${
                tab === t.key
                  ? 'bg-white font-medium text-blue-600 shadow-sm dark:bg-zinc-900 dark:text-blue-400'
                  : 'text-zinc-500 hover:text-zinc-700 dark:text-zinc-400'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        {tab === 'profile' && <ProfilePanel />}
        {tab === 'members' && (
          <>
            {/* 项目选择：成员与角色均为项目作用域 */}
            <div className="mb-4 w-72">
              <Select
                value={memberProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                options={projectList.map((p) => ({ value: p.projectId, label: `${p.projectName}（${p.projectKey}）` }))}
              />
            </div>
            {memberProjectId && <MembersPanel projectId={memberProjectId} />}
          </>
        )}
        {tab === 'system' && admin && <SystemPanel />}
      </div>
    </div>
  );
}

// ---------------- 个人设置 ----------------

function ProfilePanel() {
  const { user } = useAuthStore();
  const { theme, setTheme, density, setDensity } = useUiStore();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');

  async function changePassword() {
    try {
      await authApi.changePassword(oldPassword, newPassword);
      toast('密码已修改，请重新登录', 'success');
      setOldPassword('');
      setNewPassword('');
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }

  return (
    <div className="space-y-5">
      <section className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="flex items-center gap-3">
          <Avatar name={user?.displayName} size={44} />
          <div>
            <div className="text-sm font-semibold">{user?.displayName}</div>
            <div className="text-xs text-zinc-400">
              {user?.username} · {user?.isAdmin === '1' ? '系统管理员' : '普通用户'}
            </div>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-2 gap-3">
          <div>
            <label className="mb-1 block text-xs text-zinc-500">主题</label>
            <Select
              value={theme}
              onChange={(e) => setTheme(e.target.value as 'light' | 'dark' | 'system')}
              options={[
                { value: 'light', label: '亮色' },
                { value: 'dark', label: '暗色' },
                { value: 'system', label: '跟随系统' },
              ]}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-zinc-500">视图密度</label>
            <Select
              value={density}
              onChange={(e) => setDensity(e.target.value as 'compact' | 'comfortable')}
              options={[
                { value: 'comfortable', label: '舒适' },
                { value: 'compact', label: '紧凑' },
              ]}
            />
          </div>
        </div>
      </section>

      <section className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <h4 className="mb-3 text-sm font-semibold">修改密码</h4>
        <div className="grid grid-cols-2 gap-3">
          <Input type="password" placeholder="原密码" value={oldPassword} onChange={(e) => setOldPassword(e.target.value)} />
          <Input type="password" placeholder="新密码（6~64 位）" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
        </div>
        <Button variant="primary" size="sm" className="mt-3" disabled={!oldPassword || !newPassword} onClick={() => void changePassword()}>
          修改密码
        </Button>
      </section>
    </div>
  );
}

// ---------------- 项目成员与权限 ----------------

function MembersPanel({ projectId }: { projectId: string }) {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuthStore();
  const [addUserId, setAddUserId] = useState('');
  const [roleEdit, setRoleEdit] = useState<{ member: ProjectMemberVo; roleIds: string[] } | null>(null);
  const [roleForm, setRoleForm] = useState<{ role?: RoleVo; roleName: string; permissions: string[] } | null>(null);
  const [systemUsers, setSystemUsers] = useState<UserVo[]>([]);
  // 添加成员弹窗显式开关：候选列表为空时弹窗也要能打开（展示空态与手动粘贴入口）
  const [addOpen, setAddOpen] = useState(false);

  const { data: members = [] } = useQuery({
    queryKey: ['members', projectId],
    queryFn: () => projectApi.listMembers(projectId),
  });
  const { data: roles = [] } = useQuery({
    queryKey: ['roles', projectId],
    queryFn: () => projectApi.listRoles(projectId),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['members', projectId] });
    void queryClient.invalidateQueries({ queryKey: ['roles', projectId] });
  };

  async function openAddMember() {
    setAddUserId('');
    try {
      // 走项目域候选列表接口（pf:member:manage 可调），不能用 /system/users（管理员专用）
      setSystemUsers(await projectApi.listMemberCandidates(projectId));
      setAddOpen(true);
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }

  return (
    <div className="space-y-5">
      {/* 成员列表 */}
      <section className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="mb-3 flex items-center justify-between">
          <h4 className="text-sm font-semibold">成员（{members.length}）</h4>
          {hasPermission('pf:member:manage') && (
            <Button size="sm" variant="primary" onClick={() => void openAddMember()}>
              ＋ 添加成员
            </Button>
          )}
        </div>
        <div className="space-y-2">
          {members.map((member) => (
            <div key={member.id} className="flex items-center gap-3 rounded-lg border border-zinc-100 px-3 py-2 dark:border-zinc-800">
              <Avatar name={member.user?.displayName} size={28} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium">{member.user?.displayName ?? member.userId}</div>
                <div className="flex flex-wrap gap-1">
                  {member.roleList.map((r) => (
                    <Badge key={r.id} className="bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-300">
                      {r.roleName}
                    </Badge>
                  ))}
                  {member.roleList.length === 0 && <span className="text-[11px] text-zinc-400">未绑定角色</span>}
                </div>
              </div>
              {hasPermission('pf:member:manage') && (
                <div className="flex gap-1">
                  <Button size="sm" variant="outline" onClick={() => setRoleEdit({ member, roleIds: member.roleList.map((r) => r.id) })}>
                    角色
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={async () => {
                      if (!window.confirm(`确认移除成员「${member.user?.displayName}」？`)) {
                        return;
                      }
                      try {
                        await projectApi.removeMember(projectId, member.userId);
                        toast('已移除', 'success');
                        refresh();
                      } catch (e) {
                        toast((e as Error).message, 'error');
                      }
                    }}
                  >
                    移除
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      {/* 角色管理 */}
      {hasPermission('pf:role:manage') && (
        <section className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
          <div className="mb-3 flex items-center justify-between">
            <h4 className="text-sm font-semibold">角色权限</h4>
            <Button
              size="sm"
              variant="primary"
              onClick={() => setRoleForm({ roleName: '', permissions: [] })}
            >
              ＋ 新建角色
            </Button>
          </div>
          <div className="space-y-2">
            {roles.map((role) => (
              <div key={role.id} className="flex items-center gap-3 rounded-lg border border-zinc-100 px-3 py-2 dark:border-zinc-800">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">
                    {role.roleName}
                    {role.isBuiltin === '1' && (
                      <Badge className="ml-2 bg-blue-50 text-blue-600 dark:bg-blue-900/40 dark:text-blue-300">内置</Badge>
                    )}
                  </div>
                  <div className="text-[11px] text-zinc-400">{role.permissionList.length} 项权限</div>
                </div>
                {role.roleCode !== 'DISTRIBUTOR' && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setRoleForm({ role, roleName: role.roleName, permissions: [...role.permissionList] })}
                  >
                    编辑
                  </Button>
                )}
                {role.isBuiltin === '0' && (
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={async () => {
                      if (!window.confirm(`确认删除角色「${role.roleName}」？`)) {
                        return;
                      }
                      try {
                        await projectApi.deleteRole(projectId, role.id);
                        toast('已删除', 'success');
                        refresh();
                      } catch (e) {
                        toast((e as Error).message, 'error');
                      }
                    }}
                  >
                    删除
                  </Button>
                )}
              </div>
            ))}
          </div>
        </section>
      )}

      {/* 添加成员弹窗 */}
      <Dialog open={addOpen} title="添加成员" onClose={() => setAddOpen(false)} width="max-w-md">
        <div className="space-y-2">
          <p className="text-xs text-zinc-400">从系统用户中选择：</p>
          <div className="max-h-64 space-y-1 overflow-y-auto">
            {systemUsers.map((u) => (
              <div key={u.id} className="flex items-center justify-between rounded-lg border border-zinc-100 px-3 py-1.5 dark:border-zinc-800">
                <span className="text-sm">
                  {u.displayName} <span className="text-xs text-zinc-400">@{u.username}</span>
                </span>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={async () => {
                    try {
                      await projectApi.addMember(projectId, u.id);
                      toast('已添加', 'success');
                      setAddOpen(false);
                      refresh();
                    } catch (e) {
                      toast((e as Error).message, 'error');
                    }
                  }}
                >
                  添加
                </Button>
              </div>
            ))}
            {systemUsers.length === 0 && <p className="py-2 text-xs text-zinc-400">项目外暂无可用用户，可按 ID 添加</p>}
          </div>
          <div className="flex gap-2">
            <Input className="flex-1" placeholder="或直接粘贴用户 ID" value={addUserId} onChange={(e) => setAddUserId(e.target.value)} />
            <Button
              variant="primary"
              disabled={!addUserId.trim()}
              onClick={async () => {
                try {
                  await projectApi.addMember(projectId, addUserId.trim());
                  toast('已添加', 'success');
                  setAddOpen(false);
                  setAddUserId('');
                  refresh();
                } catch (e) {
                  toast((e as Error).message, 'error');
                }
              }}
            >
              按 ID 添加
            </Button>
          </div>
        </div>
      </Dialog>

      {/* 成员角色绑定弹窗 */}
      <Dialog
        open={!!roleEdit}
        title={`绑定角色 · ${roleEdit?.member.user?.displayName ?? ''}`}
        onClose={() => setRoleEdit(null)}
        footer={
          <>
            <Button variant="outline" onClick={() => setRoleEdit(null)}>
              取消
            </Button>
            <Button
              variant="primary"
              onClick={async () => {
                if (!roleEdit) {
                  return;
                }
                try {
                  await projectApi.updateMemberRoles(projectId, roleEdit.member.userId, roleEdit.roleIds);
                  toast('已保存角色绑定', 'success');
                  setRoleEdit(null);
                  refresh();
                } catch (e) {
                  toast((e as Error).message, 'error');
                }
              }}
            >
              保存
            </Button>
          </>
        }
      >
        <div className="space-y-1.5">
          {roles.map((role) => (
            <label key={role.id} className="flex items-center gap-2 rounded-lg border border-zinc-100 px-3 py-2 text-sm dark:border-zinc-800">
              <input
                type="checkbox"
                checked={roleEdit?.roleIds.includes(role.id) ?? false}
                onChange={(e) =>
                  setRoleEdit((prev) =>
                    prev
                      ? {
                          ...prev,
                          roleIds: e.target.checked
                            ? [...prev.roleIds, role.id]
                            : prev.roleIds.filter((id) => id !== role.id),
                        }
                      : prev,
                  )
                }
              />
              {role.roleName}
              <span className="text-[11px] text-zinc-400">{role.permissionList.length} 项权限</span>
            </label>
          ))}
        </div>
      </Dialog>

      {/* 角色编辑弹窗 */}
      {roleForm && (
        <RoleEditDialog
          projectId={projectId}
          form={roleForm}
          onClose={() => setRoleForm(null)}
          onSaved={() => {
            setRoleForm(null);
            refresh();
          }}
        />
      )}
    </div>
  );
}

function RoleEditDialog({
  projectId,
  form,
  onClose,
  onSaved,
}: {
  projectId: string;
  form: { role?: RoleVo; roleName: string; permissions: string[] };
  onClose: () => void;
  onSaved: () => void;
}) {
  const [state, setState] = useState(form);
  // 权限目录（菜单 → 权限点树）：后端下发，前端不再硬编码权限清单
  const { data: catalog } = useQuery({
    queryKey: ['permission-catalog'],
    queryFn: authApi.permissionCatalog,
    staleTime: Infinity,
  });
  // 无权限点的菜单（通知中心）不出现在配置树中
  const menuList = (catalog?.menuList ?? []).filter((menu) => menu.permissionList.length > 0);

  /** 切换某菜单下全部权限点 */
  function toggleMenu(codes: string[], checked: boolean) {
    setState((s) => ({
      ...s,
      permissions: checked
        ? [...new Set([...s.permissions, ...codes])]
        : s.permissions.filter((code) => !codes.includes(code)),
    }));
  }

  return (
    <Dialog
      open
      title={state.role ? `编辑角色 · ${state.role.roleName}` : '新建角色'}
      onClose={onClose}
      width="max-w-xl"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button
            variant="primary"
            disabled={!state.roleName.trim()}
            onClick={async () => {
              try {
                const body = { roleName: state.roleName.trim(), permissions: state.permissions };
                if (state.role) {
                  await projectApi.updateRole(projectId, state.role.id, body);
                } else {
                  await projectApi.createRole(projectId, body);
                }
                toast('已保存', 'success');
                onSaved();
              } catch (e) {
                toast((e as Error).message, 'error');
              }
            }}
          >
            保存
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Input
          placeholder="角色名称"
          value={state.roleName}
          onChange={(e) => setState((s) => ({ ...s, roleName: e.target.value }))}
        />
        <div className="max-h-80 space-y-4 overflow-y-auto rounded-md border border-zinc-100 p-3 dark:border-zinc-800">
          {menuList.map((menu) => {
            const codes = menu.permissionList.map((p) => p.code);
            const selectedCount = codes.filter((code) => state.permissions.includes(code)).length;
            const allChecked = selectedCount === codes.length;
            return (
              <div key={menu.menuCode}>
                {/* 菜单级全选：勾选整组权限点；半选态表示部分授权 */}
                <label className="flex items-center gap-2 text-xs font-semibold text-zinc-600 dark:text-zinc-300">
                  <input
                    type="checkbox"
                    ref={(el) => {
                      if (el) {
                        el.indeterminate = selectedCount > 0 && !allChecked;
                      }
                    }}
                    checked={allChecked}
                    onChange={(e) => toggleMenu(codes, e.target.checked)}
                  />
                  {menu.menuName}
                  <span className="font-normal text-zinc-400">
                    {menu.accessPermissionList.length === 0
                      ? '（菜单始终可见）'
                      : '（授权任一权限点后菜单可见）'}
                  </span>
                </label>
                <div className="mt-1.5 grid grid-cols-2 gap-1.5">
                  {menu.permissionList.map((p) => (
                    <label
                      key={p.code}
                      className="flex items-center gap-2 rounded-md border border-zinc-100 px-2.5 py-1.5 text-xs dark:border-zinc-800"
                    >
                      <input
                        type="checkbox"
                        checked={state.permissions.includes(p.code)}
                        onChange={(e) => toggleMenu([p.code], e.target.checked)}
                      />
                      {p.name}
                    </label>
                  ))}
                </div>
              </div>
            );
          })}
          {menuList.length === 0 && <div className="py-4 text-center text-xs text-zinc-400">权限目录加载中…</div>}
        </div>
      </div>
    </Dialog>
  );
}

// ---------------- 系统管理 ----------------

function SystemPanel() {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);

  const { data: page } = useQuery({
    queryKey: ['system-users', keyword],
    queryFn: () => systemApi.listUsers(keyword, 1, 100),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['system-users'] });
  };

  return (
    <div className="space-y-4">
      <section className="rounded-xl border border-zinc-200 bg-white p-4 dark:border-zinc-700 dark:bg-zinc-900">
        <div className="mb-3 flex items-center gap-2">
          <h4 className="flex-1 whitespace-nowrap text-sm font-semibold">用户管理</h4>
          {/* 定宽包裹：Input 基础样式自带 w-full，直接传宽度类会被覆盖；shrink-0 防止标题/按钮被挤压 */}
          <div className="w-48 shrink-0">
            <Input placeholder="搜索登录名/姓名" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          </div>
          <Button size="sm" variant="primary" className="shrink-0 whitespace-nowrap" onClick={() => setCreateOpen(true)}>
            ＋ 创建用户
          </Button>
        </div>
        <div className="space-y-2">
          {(page?.list ?? []).map((u) => (
            <div key={u.id} className="flex items-center gap-3 rounded-lg border border-zinc-100 px-3 py-2 dark:border-zinc-800">
              <Avatar name={u.displayName} size={28} />
              <div className="min-w-0 flex-1">
                <div className="text-sm font-medium">
                  {u.displayName}
                  {u.isAdmin === '1' && (
                    <Badge className="ml-2 bg-violet-100 text-violet-600 dark:bg-violet-900/40 dark:text-violet-300">管理员</Badge>
                  )}
                  {u.status === '1' && (
                    <Badge className="ml-2 bg-red-100 text-red-600 dark:bg-red-900/40 dark:text-red-300">已禁用</Badge>
                  )}
                </div>
                <div className="text-[11px] text-zinc-400">@{u.username} · {u.email || '未设置邮箱'}</div>
              </div>
              <Button
                size="sm"
                variant="outline"
                onClick={async () => {
                  const next = u.status === '1' ? '0' : '1';
                  try {
                    await systemApi.updateUser(u.id, { status: next });
                    toast(next === '1' ? '已禁用' : '已启用', 'success');
                    refresh();
                  } catch (e) {
                    toast((e as Error).message, 'error');
                  }
                }}
              >
                {u.status === '1' ? '启用' : '禁用'}
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={async () => {
                  const pwd = window.prompt(`为「${u.displayName}」设置新密码（6~64 位）：`);
                  if (!pwd) {
                    return;
                  }
                  try {
                    await systemApi.resetPassword(u.id, pwd);
                    toast('密码已重置', 'success');
                  } catch (e) {
                    toast((e as Error).message, 'error');
                  }
                }}
              >
                重置密码
              </Button>
            </div>
          ))}
        </div>
      </section>

      {createOpen && (
        <CreateUserDialog
          onClose={() => setCreateOpen(false)}
          onSaved={() => {
            setCreateOpen(false);
            refresh();
          }}
        />
      )}
    </div>
  );
}

function CreateUserDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [initialPassword, setInitialPassword] = useState('');

  const create = useMutation({
    mutationFn: () => systemApi.createUser({ username, displayName, email: email || undefined, initialPassword }),
    onSuccess: () => {
      toast('用户已创建', 'success');
      onSaved();
    },
    onError: (e: Error) => toast(e.message, 'error'),
  });

  return (
    <Dialog
      open
      title="创建用户（初始化密码）"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            取消
          </Button>
          <Button
            variant="primary"
            disabled={!username.trim() || !displayName.trim() || !initialPassword}
            onClick={() => create.mutate()}
          >
            创建
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <Input placeholder="登录名（字母开头 3~32 位）" value={username} onChange={(e) => setUsername(e.target.value)} />
        <Input placeholder="显示名" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        <Input placeholder="邮箱（可选）" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input type="password" placeholder="初始密码（6~64 位）" value={initialPassword} onChange={(e) => setInitialPassword(e.target.value)} />
      </div>
    </Dialog>
  );
}
