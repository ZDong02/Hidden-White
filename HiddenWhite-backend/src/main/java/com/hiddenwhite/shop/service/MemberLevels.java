package com.hiddenwhite.shop.service;

import java.util.List;

/**
 * 会员等级阶梯工具类。
 * <p>成长值阶梯：普通会员 0+、白银会员 500+、黄金会员 1500+、铂金会员 4000+、钻石会员 10000+。
 * 1 元 = 10 成长值、1 元 = 1 积分的换算在 {@code ShopRepository.awardOrderPoints} 中完成。</p>
 */
public final class MemberLevels {
    /** 单个等级定义：等级名称 + 晋升所需最低成长值。 */
    public record Level(String name, int threshold) {}

    /** 等级阶梯（升序），新增等级时在中间插入即可。 */
    private static final List<Level> LADDER = List.of(
            new Level("普通会员", 0),
            new Level("白银会员", 500),
            new Level("黄金会员", 1500),
            new Level("铂金会员", 4000),
            new Level("钻石会员", 10000));

    private MemberLevels() {
        // 纯静态工具类，禁止实例化
    }

    /** 返回成长值对应的最高等级（成长值不足任一更高门槛时回落到普通会员）。 */
    public static String levelFor(int growthValue) {
        String result = LADDER.get(0).name();
        for (Level level : LADDER) {
            if (growthValue >= level.threshold()) {
                result = level.name();
            }
        }
        return result;
    }

    /** 返回等级在阶梯中的阶位（0 起）；不在阶梯内的等级（如管理端自定义）视为 -1（最低）。 */
    public static int rankOf(String level) {
        for (int i = 0; i < LADDER.size(); i++) {
            if (LADDER.get(i).name().equals(level)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 取阶位更高的等级，保证等级只升不降。
     * 现有等级不在阶梯内（阶位 -1）时，一律由成长值对应的等级晋升覆盖。
     */
    public static String maxLevel(String current, String growthLevel) {
        return rankOf(current) > rankOf(growthLevel) ? current : growthLevel;
    }
}
