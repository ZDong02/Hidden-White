package com.hiddenwhite.admin.leveldiscount.domain.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 会员折扣策略写入请求。
 *
 * <p>两个端点共用：
 * <ul>
 *   <li>{@code PUT /api/admin/level-discounts} 读全部字段（整行保存）；</li>
 *   <li>{@code PUT /api/admin/level-discounts/enabled} 只读 {@code levelKey} + {@code enabled}
 *       （单字段开关，避免整行更新的必填校验把「只想关掉」的调用拒掉）。</li>
 * </ul>
 *
 * <p><b>为什么 levelKey 走请求体而不是 URL 路径</b>：等级键是中文（「白银会员」），
 * 放进路径要依赖各层对 URL 编码的一致处理；放 body 里没有这个风险。
 *
 * @param dailyRates 星期（"1"–"7"，1 = 周一）→ 折扣率。**只传要覆盖的天**，
 *                   未出现的天回落到 baseRate；传空对象表示整周都用基准
 */
public record LevelDiscountWriteRequest(
        String levelKey,
        Boolean enabled,
        BigDecimal baseRate,
        BigDecimal minSpend,
        String note,
        Map<String, BigDecimal> dailyRates) {
}
