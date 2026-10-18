package com.hiddenwhite.shop.domain;

/**
 * 取餐号规则。
 *
 *<p><b>业务含义</b>：取餐号是顾客到店时向咖啡师报出的号码，需满足
 * <ol>
 *   <li><b>当日唯一</b>——同一天内不可能出现两个相同的号码，否则咖啡师会拿错餐；</li>
 *   <li><b>循环范围 1~999</b>——单店单日 999 单远超实际峰值；3 位号码顾客好报、
 *       咖啡师好记，超过 999 单时应改为分段叫号而非无限扩位；</li>
 *   <li><b>与订单号解耦</b>——订单号是对账凭据，必须严格递增永不复用；
 *       取餐号是门店叫号凭据，可以循环。两者语义不同，不应互相派生。</li>
 * </ol>
 *
 * <p><b>改造前的缺陷</b>：取餐号取自 {@code orderNumber.substring(len-3)}，
 * 即「订单号末3 位」。导致两个问题——
 * <ul>
 *   <li>序号上限只有 999，单店日单量超过 999 就会回绕撞号；</li>
 *   <li>取餐号实际由订单号派生，而订单号<b>跨日会重置</b>，
 *       于是前一天第 001 号与今天第 001 号在门店叫号时完全无法区分。</li>
 * </ul>
 *
 * <p>该类是取餐号规则的唯一实现点，SQL 与业务代码都不得另写阈值。
 */
public final class PickupNumber {

    /** 号码下限。 */
    public static final int MIN = 1;

    /** 号码上限：单日 999 单循环。 */
    public static final int MAX = 999;

    /**
     * 号码位数（上限 3 位）。不足位数时左侧补零，便于咖啡师按固定宽度对齐。
     */
    private static final int WIDTH = 3;

    private PickupNumber() {
    }

    /**
     * 格式化取餐号为固定 3 位字符串。
     *
     * <p>如 7 → {@code "007"}。左侧补零既让号码视觉对齐，
     * 也保证字符串长度稳定，前端无需处理变长情况。
     */
    public static String format(int value) {
        if (value < MIN || value > MAX) {
            throw new IllegalArgumentException("取餐号超出范围 " + MIN + "~" + MAX + "：" + value);
        }
        return String.format("%0" + WIDTH + "d", value);
    }

    /**
     * 按当日已用的最大号码推出下一个号码，超出上限则回到 {@link #MIN}。
     *
     * <p><b>为什么复用已取消订单占用的号码是安全的</b>：顾客报号时，
     * 咖啡师看的是「此刻叫到几号」，而当日队列中取餐号互不相同即可。
     * 一笔订单被取消意味着顾客不会再出现，号码可安全回池。
     * 真正会造成拿错餐的是<b>同时有两笔进行中订单</b>同号，
     * 而这已被「同一会员仅一张进行中订单」限制在不同会员之间仍可能发生，
     * 故本方法以<b>全店当日最大号码</b>为基准递增，而非仅看进行中订单。
     *
     * @param lastUsed 当日已分配的最大号码；无历史订单时传 0
     * @return 下一个可用号码
     */
    public static int nextAfter(int lastUsed) {
        if (lastUsed < MIN) {
            return MIN;
        }
        return lastUsed >= MAX ? MIN : lastUsed + 1;
    }
}