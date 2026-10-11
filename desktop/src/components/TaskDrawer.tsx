import { useCallback, useEffect, useRef, useState } from 'react';
import { Drawer } from '@/components/ui/Drawer';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { Dialog } from '@/components/ui/Dialog';
import { Badge, PRIORITY_META, STATUS_META, TYPE_LABELS } from '@/components/ui/Badge';
import { Avatar } from '@/components/ui/Avatar';
import { collabApi, projectApi, taskApi } from '@/api';
import { toast } from '@/stores/toast';
import { useAuthStore } from '@/stores/auth';
import type { AttachmentVo, CommentVo, ProjectMemberVo, TaskLogVo, TaskVo } from '@/api/types';

export interface TaskDrawerProps {
  task: TaskVo | null;
  onClose: () => void;
  /** 任意变更后回调（刷新看板/列表） */
  onChanged: () => void;
}

/** 任务详情抽屉（外层做空值守卫，内层持有非空 task） */
export function TaskDrawer(props: TaskDrawerProps) {
  if (!props.task) {
    return null;
  }
  return <TaskDrawerInner {...props} task={props.task} />;
}

/** 任务详情抽屉内层：元信息 + 状态动作 + 评论（@ 提及）+ 附件（预签名直传）+ 操作日志 */
function TaskDrawerInner({ task: initialTask, onClose, onChanged }: TaskDrawerProps & { task: TaskVo }) {
  const { user, hasPermission } = useAuthStore();
  // 抽屉内维护最新任务快照：打开时拉取详情兜底，操作后用响应刷新
  // （修复：抽屉数据不随操作/WS 变化，导致关闭后「重新打开」按钮不出现）
  const [current, setCurrent] = useState<TaskVo>(initialTask);
  // 渲染与动作统一引用最新快照
  const task = current;
  const [comments, setComments] = useState<CommentVo[]>([]);
  const [attachments, setAttachments] = useState<AttachmentVo[]>([]);
  const [logs, setLogs] = useState<TaskLogVo[]>([]);
  const [members, setMembers] = useState<ProjectMemberVo[]>([]);
  const [commentText, setCommentText] = useState('');
  const [mentions, setMentions] = useState<string[]>([]);
  const [reasonText, setReasonText] = useState('');
  const [reasonOpen, setReasonOpen] = useState<'close' | 'reopen' | null>(null);
  const [distributeTo, setDistributeTo] = useState('');
  const [uploading, setUploading] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const isCreator = !!current && user?.id === current.creatorId;
  const isAssignee = !!current && user?.id === current.assigneeId;

  useEffect(() => {
    setCurrent(initialTask);
    let alive = true;
    taskApi
      .detail(initialTask.id)
      .then((vo) => {
        if (alive) {
          setCurrent(vo);
        }
      })
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, [initialTask.id]);

  const reloadAll = useCallback(async () => {
    if (!current) {
      return;
    }
    try {
      const [commentList, attachmentList, logList, memberList] = await Promise.all([
        collabApi.listComments(current.id),
        collabApi.listAttachments(current.id).catch(() => [] as AttachmentVo[]),
        hasPermission('pf:log:view') ? taskApi.logs(current.id).catch(() => [] as TaskLogVo[]) : Promise.resolve([]),
        // @ 提及候选 = 任务所属项目的成员（跨项目视图下不能依赖全局当前项目）
        current.projectId ? projectApi.listMembers(current.projectId).catch(() => []) : Promise.resolve([]),
      ]);
      setComments(commentList);
      setAttachments(attachmentList);
      setLogs(logList);
      setMembers(memberList);
    } catch {
      // 详情子资源加载失败不打断主视图
    }
  }, [current, hasPermission]);

  useEffect(() => {
    void reloadAll();
  }, [reloadAll]);

  async function runAction(fn: () => Promise<unknown>, successText: string) {
    try {
      const result = await fn();
      // 状态流转类接口返回最新 TaskVo，直接刷新抽屉
      if (result && typeof result === 'object' && 'taskStatus' in (result as Record<string, unknown>)) {
        setCurrent(result as TaskVo);
      }
      toast(successText, 'success');
      onChanged();
      await reloadAll();
    } catch (e) {
      toast((e as Error).message, 'error');
    }
  }

  async function submitComment() {
    if (!commentText.trim()) {
      return;
    }
    await runAction(() => collabApi.createComment(task.id, commentText.trim(), mentions), '评论已发表');
    setCommentText('');
    setMentions([]);
  }

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      // 预签名直传：PUT 到 MinIO，再登记元数据
      const presign = await collabApi.presign(task.id, file.name, file.type || undefined);
      await fetch(presign.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': file.type || 'application/octet-stream' },
        body: file,
      });
      await collabApi.registerAttachment(task.id, {
        fileName: file.name,
        objectKey: presign.objectKey,
        fileSize: file.size,
        contentType: file.type || undefined,
      });
      toast('附件上传成功', 'success');
      await reloadAll();
    } catch (e) {
      toast(`上传失败：${(e as Error).message}`, 'error');
    } finally {
      setUploading(false);
      if (fileRef.current) {
        fileRef.current.value = '';
      }
    }
  }

  const statusMeta = STATUS_META[task.taskStatus];
  const executorOptions = members
    .filter((m) => m.roleList.some((r) => r.permissionList?.includes('pf:task:start')))
    .map((m) => ({ value: m.userId, label: m.user?.displayName ?? m.userId }));

  return (
    <Drawer open title={`${task.taskNo} ${task.title}`} onClose={onClose}>
      <div className="space-y-5 text-sm">
        {/* 状态与元信息 */}
        <div className="flex flex-wrap items-center gap-2">
          <Badge className={statusMeta?.badge}>{statusMeta?.label}</Badge>
          {task.priority && <Badge className={PRIORITY_META[task.priority].badge}>{PRIORITY_META[task.priority].label}</Badge>}
          {task.taskType && <Badge className="bg-zinc-100 text-zinc-600 dark:bg-zinc-700 dark:text-zinc-300">{TYPE_LABELS[task.taskType] ?? task.taskType}</Badge>}
          {task.version != null && <span className="text-[11px] text-zinc-400">v{task.version}</span>}
        </div>

        <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-xs text-zinc-500">
          <div>所属项目：<span className="text-zinc-800 dark:text-zinc-200">{task.projectName ?? '-'}</span></div>
          <div>创建人：<span className="text-zinc-800 dark:text-zinc-200">{task.creatorName}</span></div>
          <div>执行人：<span className="text-zinc-800 dark:text-zinc-200">{task.assigneeName ?? '未分配'}</span></div>
          <div>分发人：<span className="text-zinc-800 dark:text-zinc-200">{task.distributorName ?? '-'}</span></div>
          <div>截止时间：<span className="text-zinc-800 dark:text-zinc-200">{task.deadline?.slice(0, 16) ?? '-'}</span></div>
          <div>预计工时：<span className="text-zinc-800 dark:text-zinc-200">{task.estimatedHours ?? '-'} h</span></div>
          <div>剩余工时：<span className="text-zinc-800 dark:text-zinc-200">{task.remainingHours ?? '-'} h</span></div>
          {task.startedTime && <div>开始于：{task.startedTime.slice(0, 16)}</div>}
          {task.completedTime && <div>完成于：{task.completedTime.slice(0, 16)}</div>}
        </div>

        {task.description && (
          <div
            className="rounded-md bg-zinc-50 p-3 text-xs leading-5 text-zinc-600 dark:bg-zinc-800 dark:text-zinc-300"
            dangerouslySetInnerHTML={{ __html: task.description }}
          />
        )}

        {/* 状态动作区 */}
        <div className="flex flex-wrap gap-2 rounded-md border border-zinc-200 p-3 dark:border-zinc-700">
          {task.taskStatus === 'DRAFT' && isCreator && (
            <>
              <Button variant="primary" size="sm" onClick={() => void runAction(() => taskApi.publish(task.id), '已发布到待分发')}>
                发布
              </Button>
              <Button
                variant="danger"
                size="sm"
                onClick={() => void runAction(() => taskApi.deleteDraft(task.id), '草稿已删除').then(onClose)}
              >
                删除草稿
              </Button>
            </>
          )}
          {task.taskStatus === 'PENDING_DIST' && (
            <>
              {isCreator && (
                <Button variant="outline" size="sm" onClick={() => void runAction(() => taskApi.withdraw(task.id), '已撤回为草稿')}>
                  撤回
                </Button>
              )}
              {hasPermission('pf:task:distribute') && (
                <div className="flex items-center gap-1">
                  {/* 定宽包裹：Select 基础样式自带 w-full，直接传宽度类会被覆盖，导致相邻按钮被挤压折行 */}
                  <div className="w-36">
                    <Select
                      className="h-7 text-xs"
                      value={distributeTo}
                      onChange={(e) => setDistributeTo(e.target.value)}
                      options={executorOptions}
                      placeholder="选择执行人"
                    />
                  </div>
                  <Button
                    variant="primary"
                    size="sm"
                    disabled={!distributeTo}
                    onClick={() =>
                      void runAction(
                        () => taskApi.distribute(task.id, distributeTo),
                        '已分发，执行人已收到通知',
                      )
                    }
                  >
                    分发
                  </Button>
                </div>
              )}
            </>
          )}
          {task.taskStatus === 'WAITING' && isAssignee && (
            <Button variant="primary" size="sm" onClick={() => void runAction(() => taskApi.start(task.id), '任务已开始')}>
              开始任务
            </Button>
          )}
          {task.taskStatus === 'IN_PROGRESS' && isAssignee && (
            <>
              <Button variant="primary" size="sm" onClick={() => void runAction(() => taskApi.complete(task.id), '任务已完成')}>
                完成任务
              </Button>
              <Button variant="outline" size="sm" onClick={() => void runAction(() => taskApi.switchBack(task.id), '已切换回等待中')}>
                切换回等待中
              </Button>
            </>
          )}
          {(task.taskStatus === 'COMPLETED' || task.taskStatus === 'WAITING') && hasPermission('pf:task:close') && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setReasonText('');
                setReasonOpen('close');
              }}
            >
              关闭任务
            </Button>
          )}
          {task.taskStatus === 'CLOSED' && hasPermission('pf:task:reopen') && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setReasonText('');
                setReasonOpen('reopen');
              }}
            >
              重新打开
            </Button>
          )}
        </div>

        {/* 评论 */}
        <section>
          <h4 className="mb-2 text-xs font-semibold text-zinc-500">评论（{comments.length}）</h4>
          <div className="space-y-2">
            {comments.map((comment) => (
              <div key={comment.id} className="flex gap-2">
                <Avatar name={comment.authorName} size={24} />
                <div className="min-w-0 flex-1 rounded-md bg-zinc-50 px-3 py-2 dark:bg-zinc-800">
                  <div className="text-[11px] text-zinc-400">
                    {comment.authorName} · {comment.createTime?.slice(0, 16)}
                    {comment.mentionNameList.length > 0 && ` · @ ${comment.mentionNameList.join('、')}`}
                  </div>
                  <div className="whitespace-pre-wrap break-words text-xs text-zinc-700 dark:text-zinc-200">{comment.content}</div>
                </div>
              </div>
            ))}
          </div>
          {hasPermission('pf:comment:create') && (
            <div className="mt-2 space-y-2">
              <Textarea
                rows={2}
                value={commentText}
                onChange={(e) => setCommentText(e.target.value)}
                placeholder="发表评论…"
              />
              {members.length > 0 && (
                <div className="flex flex-wrap items-center gap-1">
                  <span className="text-[11px] text-zinc-400">@ 提及：</span>
                  {members.map((m) => {
                    const active = mentions.includes(m.userId);
                    return (
                      <button
                        key={m.userId}
                        className={`rounded px-1.5 py-0.5 text-[11px] ${
                          active
                            ? 'bg-blue-600 text-white'
                            : 'bg-zinc-100 text-zinc-500 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300'
                        }`}
                        onClick={() =>
                          setMentions((prev) =>
                            prev.includes(m.userId) ? prev.filter((id) => id !== m.userId) : [...prev, m.userId],
                          )
                        }
                      >
                        {m.user?.displayName ?? m.userId}
                      </button>
                    );
                  })}
                </div>
              )}
              <Button variant="primary" size="sm" onClick={() => void submitComment()}>
                发表评论
              </Button>
            </div>
          )}
        </section>

        {/* 附件 */}
        <section>
          <h4 className="mb-2 text-xs font-semibold text-zinc-500">附件（{attachments.length}）</h4>
          <div className="space-y-1.5">
            {attachments.map((a) => (
              <div key={a.id} className="flex items-center gap-2 rounded-md border border-zinc-200 px-3 py-1.5 text-xs dark:border-zinc-700">
                <span className="min-w-0 flex-1 truncate">{a.fileName}</span>
                <span className="text-zinc-400">{(a.fileSize / 1024).toFixed(1)} KB · {a.uploaderName}</span>
                {a.downloadUrl && (
                  <a className="text-blue-600 hover:underline dark:text-blue-400" href={a.downloadUrl} target="_blank" rel="noreferrer">
                    下载
                  </a>
                )}
              </div>
            ))}
          </div>
          {hasPermission('pf:attachment:upload') && (
            <div className="mt-2">
              <input
                ref={fileRef}
                type="file"
                className="hidden"
                onChange={(e) => e.target.files?.[0] && void handleUpload(e.target.files[0])}
              />
              <Button variant="outline" size="sm" disabled={uploading} onClick={() => fileRef.current?.click()}>
                {uploading ? '上传中…' : '上传附件（预签名直传）'}
              </Button>
            </div>
          )}
        </section>

        {/* 操作日志 */}
        {hasPermission('pf:log:view') && logs.length > 0 && (
          <section>
            <h4 className="mb-2 text-xs font-semibold text-zinc-500">操作日志</h4>
            <div className="space-y-1">
              {logs.map((log) => (
                <div key={log.id} className="flex items-baseline gap-2 text-[11px] text-zinc-400">
                  <span className="w-28 shrink-0">{log.createTime?.slice(0, 16)}</span>
                  <span className="w-16 shrink-0 font-medium text-zinc-600 dark:text-zinc-300">{log.operatorName}</span>
                  <span>
                    {log.action}
                    {log.fromStatus !== log.toStatus && `：${log.fromStatus ?? '-'} → ${log.toStatus ?? '-'}`}
                    {log.reason && `（${log.reason}）`}
                  </span>
                </div>
              ))}
            </div>
          </section>
        )}
      </div>

      {/* 关闭/重开原因 */}
      <Dialog
        open={reasonOpen !== null}
        title={reasonOpen === 'close' ? '关闭任务' : '重新打开任务'}
        onClose={() => setReasonOpen(null)}
        footer={
          <>
            <Button variant="outline" onClick={() => setReasonOpen(null)}>
              取消
            </Button>
            <Button
              variant="primary"
              disabled={!reasonText.trim()}
              onClick={() => {
                const action = reasonOpen;
                setReasonOpen(null);
                if (action === 'close') {
                  void runAction(() => taskApi.close(task.id, reasonText.trim()), '任务已关闭');
                } else if (action === 'reopen') {
                  void runAction(() => taskApi.reopen(task.id, reasonText.trim()), '任务已重新打开');
                }
              }}
            >
              确认
            </Button>
          </>
        }
      >
        <Input value={reasonText} onChange={(e) => setReasonText(e.target.value)} placeholder="必填原因备注" />
      </Dialog>
    </Drawer>
  );
}
