import { describe, expect, it } from 'vitest';
import { applyBoardSort, applySortRef } from './boardOptimisticSort';
import type { BoardVo, TaskVo } from '../api/types';

/** 构造最小可用 TaskVo（仅 id/priority 参与排序逻辑，其余为必填占位） */
function task(id: string, priority?: TaskVo['priority']): TaskVo {
  return {
    id,
    projectId: 'p1',
    seqNo: 1,
    taskNo: `P1-${id}`,
    projectKey: 'P1',
    title: `任务${id}`,
    priority,
    taskStatus: 'PENDING_DIST',
    creatorId: 'u1',
    version: 0,
  };
}

/** 乐观重排单测（落点引用契约与 dragSortRef 一致：beforeId=插到它前面 / afterId=插到它后面 / 均空=桶尾插） */
describe('boardOptimisticSort 乐观重排', () => {
  describe('applySortRef', () => {
    it('向上拖：插到目标卡片之前（中间插入）', () => {
      const list = [task('A'), task('B'), task('C')];
      expect(applySortRef(list, 'C', { beforeId: 'B' }).map((t) => t.id)).toEqual(['A', 'C', 'B']);
    });

    it('向上拖到队首：插到第一张卡片之前', () => {
      const list = [task('A'), task('B'), task('C')];
      expect(applySortRef(list, 'C', { beforeId: 'A' }).map((t) => t.id)).toEqual(['C', 'A', 'B']);
    });

    it('向下拖：插到目标卡片之后', () => {
      const list = [task('A'), task('B'), task('C')];
      expect(applySortRef(list, 'A', { afterId: 'C' }).map((t) => t.id)).toEqual(['B', 'C', 'A']);
    });

    it('落回原位（ref=null）：返回原列表引用', () => {
      const list = [task('A'), task('B')];
      expect(applySortRef(list, 'A', null)).toBe(list);
    });

    it('桶尾插（均空）非空桶：插到同优先级最后一个任务之后', () => {
      const list = [task('U1', 'URGENT'), task('M1', 'MEDIUM'), task('M2', 'MEDIUM'), task('L1', 'LOW')];
      expect(applySortRef(list, 'M1', {}).map((t) => t.id)).toEqual(['U1', 'M2', 'M1', 'L1']);
    });

    it('桶尾插（均空）空桶：插到更高优先级任务之后', () => {
      const list = [task('U1', 'URGENT'), task('M', 'MEDIUM'), task('L1', 'LOW')];
      expect(applySortRef(list, 'M', {}).map((t) => t.id)).toEqual(['U1', 'M', 'L1']);
    });

    it('不可变：返回新数组，未移动任务与被拖任务的引用保持不变', () => {
      const a = task('A');
      const b = task('B');
      const result = applySortRef([a, b], 'B', { beforeId: 'A' });
      expect(result).not.toEqual([a, b]);
      expect(result[0]).toBe(b);
      expect(result[1]).toBe(a);
    });

    it('被拖任务不在列表（缓存已变）：原样返回', () => {
      const list = [task('A'), task('B')];
      expect(applySortRef(list, 'X', { beforeId: 'A' })).toBe(list);
    });

    it('目标卡片不在列表（缓存已变）：原样返回', () => {
      const list = [task('A'), task('B')];
      expect(applySortRef(list, 'A', { beforeId: 'X' })).toBe(list);
      expect(applySortRef(list, 'A', { afterId: 'X' })).toBe(list);
    });
  });

  describe('applyBoardSort', () => {
    const base: BoardVo = {
      draftList: [task('D1')],
      pendingDistList: [task('A'), task('B'), task('C')],
      waitingList: [task('W1'), task('W2'), task('W3')],
      inProgressList: [],
      completedList: [],
      closedList: [],
    };

    it('pendingDist：替换 pendingDistList，其余列引用不变', () => {
      const next = applyBoardSort(base, 'pendingDist', 'C', { beforeId: 'B' });
      expect(next).not.toBe(base);
      expect(next.pendingDistList.map((t) => t.id)).toEqual(['A', 'C', 'B']);
      expect(next.draftList).toBe(base.draftList);
      expect(next.waitingList).toBe(base.waitingList);
    });

    it('waiting：仅 waitingList 被替换', () => {
      const next = applyBoardSort(base, 'waiting', 'W3', { beforeId: 'W2' });
      expect(next).not.toBe(base);
      expect(next.waitingList.map((t) => t.id)).toEqual(['W1', 'W3', 'W2']);
      expect(next.pendingDistList).toBe(base.pendingDistList);
    });

    it('其他 scope：返回原 board 引用（乐观更新静默跳过）', () => {
      expect(applyBoardSort(base, 'inProgress', 'A', { beforeId: 'B' })).toBe(base);
    });
  });
});
