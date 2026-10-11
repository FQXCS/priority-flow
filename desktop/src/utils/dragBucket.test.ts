import { describe, expect, it } from 'vitest';
import { bucketRange, canDropWithinBucket } from './dragBucket';

/** 拖拽桶约束前端镜像逻辑单测（与后端 TaskQueueOrderTest 场景对齐） */
describe('dragBucket 桶约束校验', () => {
  it('bucketRange 定位已存在的桶', () => {
    expect(bucketRange(['URGENT', 'URGENT', 'MEDIUM', 'LOW'], 'URGENT')).toEqual({ start: 0, end: 2 });
    expect(bucketRange(['URGENT', 'URGENT', 'MEDIUM', 'LOW'], 'MEDIUM')).toEqual({ start: 2, end: 3 });
    expect(bucketRange(['URGENT', 'URGENT', 'MEDIUM', 'LOW'], 'LOW')).toEqual({ start: 3, end: 4 });
  });

  it('bucketRange 桶缺失时落在理论桶位', () => {
    // 队列中无 HIGH：理论桶位在 URGENT 与 MEDIUM 之间
    expect(bucketRange(['URGENT', 'MEDIUM', 'LOW'], 'HIGH')).toEqual({ start: 1, end: 1 });
    // 空队列
    expect(bucketRange([], 'LOW')).toEqual({ start: 0, end: 0 });
  });

  it('同优先级桶内拖拽放行（含桶边界）', () => {
    const queue = ['URGENT', 'MEDIUM', 'MEDIUM', 'LOW'];
    // MEDIUM 桶范围 [1, 3]：桶首前插、桶尾后插均合法
    expect(canDropWithinBucket(queue, 'MEDIUM', 1)).toBe(true);
    expect(canDropWithinBucket(queue, 'MEDIUM', 3)).toBe(true);
  });

  it('跨优先级拖拽拦截', () => {
    const queue = ['URGENT', 'MEDIUM', 'LOW'];
    // LOW 拖到队首（URGENT 前）→ 拦截
    expect(canDropWithinBucket(queue, 'LOW', 0)).toBe(false);
    // URGENT 拖到队尾（LOW 后）→ 拦截
    expect(canDropWithinBucket(queue, 'URGENT', 3)).toBe(false);
  });
});
