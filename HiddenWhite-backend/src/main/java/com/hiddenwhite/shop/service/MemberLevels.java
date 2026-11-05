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

    /**
     * 完整等级阶梯（升序，不可变）。
     *
     * <p>对外暴露是为了让「会员折扣」这类按等级配置的功能能列出全部等级，
     * 而不是各自再抄一份等级名 —— 抄一份就会出现「折扣页少一个等级」而没人发现。
     * 返回不可变列表，调用方不得修改（等级阶梯是代码常量，不是数据）。
     */
    public static List<Level> levels() {
        return LADDER;
    }

    /**
     * 某等级的起点成长值；不在阶梯内返回 {@code null}。
     *
     * <p>「不在阶梯内」是真实存在的数据状态：管理端会员编辑允许手写等级名，
     * 历史上出现过「金卡会员」这种既不在阶梯、也不会被成长值晋升覆盖的值。
     * 调用方需要区分「0 成长值」与「查无此等级」。
     */
    public static Integer thresholdOf(String level) {
        for (Level item : LADDER) {
            if (item.name().equals(level)) {
                return item.threshold();
            }
        }
        return null;
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
