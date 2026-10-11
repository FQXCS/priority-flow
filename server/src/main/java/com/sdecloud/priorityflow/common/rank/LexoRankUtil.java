package com.sdecloud.priorityflow.common.rank;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * LexoRank 排序键工具：base 26（a-z）字符串排序键，中值插入 + 深度增长 + 重整。
 *
 * <p>键结构：[桶位][小数位...]。首位为桶（整数值 0~25），其余各位为 base-26 小数位（每位 0~25）。
 * 键的全序 = 字符串字典序 = 数值序：
 * <ul>
 *   <li>非前缀情形：首位不同字典序即决定；首位相同则比较小数部分，首差异位小者数值必小
 *       （后续剩余小数 < 26^-i，不足以翻转首位差）；</li>
 *   <li>前缀情形：短串是长串前缀时字典序短者在前，数值上长串多出的位 > 0（规范化保证非全 0）。</li>
 * </ul></p>
 *
 * <p>规范化约束：键不以 'a' 结尾（等价于去掉小数末尾的 0），保证同一数值只有唯一编码。</p>
 *
 * <p>服务端只信任本类计算的 rank，不信任前端提交值；接口仅接收目标位置（beforeId/afterId）。</p>
 *
 * @author priority-flow
 */
public final class LexoRankUtil {

    /** 字符集下界 'a'（数值 0） */
    public static final char MIN_CHAR = 'a';

    /** 字符集上界 'z'（数值 25） */
    public static final char MAX_CHAR = 'z';

    /** 键最大长度（含桶位），达到即视为空间耗尽，调用方应触发重整 */
    public static final int MAX_RANK_LENGTH = 12;

    private static final int BASE = 26;

    /** 全区间中值，作为首个元素的键 */
    private static final String INITIAL = "n";

    private LexoRankUtil() {
    }

    /**
     * 初始键（空列表插入第一个元素）
     */
    public static String initial() {
        return INITIAL;
    }

    /**
     * 生成严格介于 prev 与 next 之间的键；二者均可为 null（null 表示对应方向无界）。
     *
     * @param prev 前邻键（须小于 next，可为 null）
     * @param next 后邻键（可为 null）
     * @return 新键
     * @throws RankSpaceExhaustedException 达到最大深度无插入空间时抛出，调用方应触发重整
     */
    public static String between(String prev, String next) {
        String a = normalize(prev);
        String b = normalize(next);
        if (a == null && b == null) {
            return INITIAL;
        }
        if (a != null && b != null && a.compareTo(b) >= 0) {
            throw new IllegalArgumentException("prev 必须小于 next: " + a + " >= " + b);
        }
        // 从现有键的最大位数起算；当前精度无空间则加密一位重试
        int k = Math.max(a == null ? 1 : a.length(), b == null ? 1 : b.length());
        while (k <= MAX_RANK_LENGTH + 1) {
            BigInteger lower = lowerBound(a, k);
            BigInteger upper = upperBound(a, b, k);
            // 中值向下取整
            BigInteger mid = lower.add(upper).shiftRight(1);
            if (mid.compareTo(lower) > 0) {
                String rank = encode(mid, k);
                if (rank.length() > MAX_RANK_LENGTH) {
                    break;
                }
                return rank;
            }
            k++;
        }
        throw new RankSpaceExhaustedException("排序键空间耗尽，需触发重整");
    }

    /**
     * 尾插：生成严格大于 last 的键（last 可为 null，表示空列表）
     */
    public static String after(String last) {
        return between(last, null);
    }

    /**
     * 头插：生成严格小于 first 的键（first 可为 null，表示空列表）
     */
    public static String before(String first) {
        return between(null, first);
    }

    /**
     * 均匀重整：为 count 个元素（按当前显示顺序）生成覆盖全区间、严格递增的键。
     *
     * @param count 元素数量
     * @return count 个键，按顺序对应第 1..count 个元素
     */
    public static List<String> rebalance(int count) {
        if (count <= 0) {
            return List.of();
        }
        if (count == 1) {
            return List.of(INITIAL);
        }
        // 位数选择：保证相邻键间距（step）≥ 1
        int k = 1;
        BigInteger space = BigInteger.valueOf(BASE).pow(k);
        while (space.divide(BigInteger.valueOf(count + 1L)).compareTo(BigInteger.ONE) < 0) {
            k++;
            space = BigInteger.valueOf(BASE).pow(k);
            if (k > MAX_RANK_LENGTH) {
                throw new RankSpaceExhaustedException("重整元素数量超出排序键容量");
            }
        }
        BigInteger step = space.divide(BigInteger.valueOf(count + 1L));
        List<String> ranks = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            ranks.add(encode(step.multiply(BigInteger.valueOf(i)), k));
        }
        return ranks;
    }

    /**
     * 比较两个键的大小（全序即字典序）
     */
    public static int compare(String a, String b) {
        return a.compareTo(b);
    }

    // ---------------- 内部实现 ----------------

    /**
     * 区间下界（含）：a 为 null 表示数值 0（头插起点）；否则为 a 在 k 位刻度上的数值
     */
    private static BigInteger lowerBound(String a, int k) {
        if (a == null) {
            return BigInteger.ZERO;
        }
        return digitValue(a, k);
    }

    /**
     * 区间上界（不含）：b 为 null 表示 a 的桶位+1 的起点（尾插终点）；否则为 b 在 k 位刻度上的数值
     */
    private static BigInteger upperBound(String a, String b, int k) {
        if (b == null) {
            // 桶位+1 的整数值 = (head+1) * 26^(k-1)
            return BigInteger.valueOf(charDigit(a.charAt(0)) + 1L).multiply(BigInteger.valueOf(BASE).pow(k - 1));
        }
        return digitValue(b, k);
    }

    /**
     * 键在 k 位刻度上的数值：缺失位按 0（'a'）补齐
     */
    private static BigInteger digitValue(String s, int k) {
        BigInteger value = BigInteger.ZERO;
        for (int i = 0; i < s.length(); i++) {
            value = value.multiply(BigInteger.valueOf(BASE))
                    .add(BigInteger.valueOf(charDigit(s.charAt(i))));
        }
        // 不足 k 位时右侧补 0（乘 26^补位数）
        return value.multiply(BigInteger.valueOf(BASE).pow(k - s.length()));
    }

    /**
     * 数值编码为 k 位键，并规范化（去除末尾 'a'）
     */
    private static String encode(BigInteger value, int k) {
        StringBuilder sb = new StringBuilder(k);
        BigInteger base = BigInteger.valueOf(BASE);
        for (int i = k - 1; i >= 0; i--) {
            BigInteger[] div = value.divideAndRemainder(base.pow(i));
            sb.append((char) (MIN_CHAR + div[0].intValue()));
            value = div[1];
        }
        return normalize(sb.toString());
    }

    /**
     * 规范化：去除末尾 'a'（小数末尾的 0）；全 'a' 时保留单个 'a'
     */
    private static String normalize(String rank) {
        if (rank == null) {
            return null;
        }
        int end = rank.length();
        while (end > 1 && rank.charAt(end - 1) == MIN_CHAR) {
            end--;
        }
        return rank.substring(0, end);
    }

    private static int charDigit(char c) {
        if (c < MIN_CHAR || c > MAX_CHAR) {
            throw new IllegalArgumentException("非法排序键字符: " + c);
        }
        return c - MIN_CHAR;
    }
}
