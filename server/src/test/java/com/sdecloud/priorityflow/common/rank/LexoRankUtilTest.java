package com.sdecloud.priorityflow.common.rank;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LexoRankUtil 单元测试：中值、深度增长、重整边界全覆盖。
 *
 * @author priority-flow
 */
class LexoRankUtilTest {

    @Test
    void initialShouldBeMiddleOfRange() {
        assertEquals("n", LexoRankUtil.initial());
    }

    @Test
    void betweenNullNullShouldReturnInitial() {
        assertEquals("n", LexoRankUtil.between(null, null));
    }

    @Test
    void headInsertShouldBeLessThanFirst() {
        String first = LexoRankUtil.initial();
        String head = LexoRankUtil.before(first);
        assertTrue(head.compareTo(first) < 0, "头插键 " + head + " 应小于 " + first);
    }

    @Test
    void tailInsertShouldBeGreaterThanLast() {
        String last = LexoRankUtil.initial();
        String tail = LexoRankUtil.after(last);
        assertTrue(tail.compareTo(last) > 0, "尾插键 " + tail + " 应大于 " + last);
    }

    @Test
    void betweenAdjacentHeadsShouldGrowDepth() {
        // 相邻桶位无中间字符（m=12, n=13）→ 深度增长得 "mn"（数值 12.5）
        String mid = LexoRankUtil.between("m", "n");
        assertEquals("mn", mid);
        assertTrue(mid.compareTo("m") > 0 && mid.compareTo("n") < 0);
    }

    @Test
    void betweenNullAndSingleCharShouldUseSecondDigit() {
        // between(null, "b")：首位无空间 → 加深一位得 "an"
        String key = LexoRankUtil.between(null, "b");
        assertEquals("an", key);
        assertTrue(key.compareTo("a") > 0 && key.compareTo("b") < 0);
    }

    @Test
    void betweenShouldAlwaysLandBetween() {
        String a = "abz";
        String b = "ac";
        String key = LexoRankUtil.between(a, b);
        assertTrue(key.compareTo(a) > 0 && key.compareTo(b) < 0);
    }

    @Test
    void randomizedInsertsShouldKeepStrictOrder() {
        Random random = new Random(42);
        List<String> ranks = new ArrayList<>();
        ranks.add(LexoRankUtil.initial());
        // 模拟 2000 次随机位置插入，验证全序保持
        for (int round = 0; round < 2000; round++) {
            int index = random.nextInt(ranks.size() + 1);
            String prev = index == 0 ? null : ranks.get(index - 1);
            String next = index == ranks.size() ? null : ranks.get(index);
            try {
                String key = LexoRankUtil.between(prev, next);
                ranks.add(index, key);
            } catch (RankSpaceExhaustedException e) {
                // 空间耗尽触发重整：均匀重排后继续
                ranks = new ArrayList<>(LexoRankUtil.rebalance(ranks.size() + 1));
            }
            // 顺序必须严格递增
            for (int i = 1; i < ranks.size(); i++) {
                assertTrue(ranks.get(i - 1).compareTo(ranks.get(i)) < 0,
                        "第 " + round + " 轮后顺序被破坏 @" + i);
            }
        }
        assertTrue(ranks.size() >= 2000);
    }

    @Test
    void betweenInvalidOrderShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> LexoRankUtil.between("b", "a"));
        assertThrows(IllegalArgumentException.class, () -> LexoRankUtil.between("a", "a"));
    }

    @Test
    void maxDepthShouldExhaustAndThrow() {
        // 最大长度（12 位）下相邻键之间已无插入空间 → 加深一位即超限，确定性触发耗尽
        String prev = "n" + "aaaaaaaaaaa";   // 12 位
        String next = "n" + "aaaaaaaaaab";   // 12 位，末位差 1
        assertThrows(RankSpaceExhaustedException.class, () -> LexoRankUtil.between(prev, next));
    }

    @Test
    void rebalanceShouldProduceStrictlyIncreasingRanks() {
        for (int count : new int[]{1, 2, 3, 26, 27, 100, 700, 5000}) {
            List<String> ranks = LexoRankUtil.rebalance(count);
            assertEquals(count, ranks.size());
            assertNotNull(ranks.get(0));
            for (int i = 1; i < count; i++) {
                assertTrue(ranks.get(i - 1).compareTo(ranks.get(i)) < 0,
                        "count=" + count + " 时重整结果顺序错误 @" + i);
            }
        }
    }

    @Test
    void rebalanceZeroShouldReturnEmpty() {
        assertEquals(0, LexoRankUtil.rebalance(0).size());
    }

    @Test
    void normalizeShouldStripTrailingMinChar() {
        // 编码结果不以 'a' 结尾：验证中值不产生等值重复键
        String k1 = LexoRankUtil.between("a", "c");
        String k2 = LexoRankUtil.between("a", k1);
        String k3 = LexoRankUtil.between(k1, "c");
        assertTrue(k2.compareTo(k1) < 0 && k1.compareTo(k3) < 0);
    }
}
