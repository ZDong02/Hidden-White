/**
 * 会员等级 → 卡面主题键 / 折扣文案。
 *
 * <p>放在 utils 而不是各组件里各写一份：会员卡（`MemberPanel`）与折扣详情页
 * 必须对同一个等级给出同一套配色，否则会出现「会员卡是蓝色、折扣页是紫色」。
 *
 * <p>匹配用 `includes` 而不是全等：管理端的会员等级是自由文本，
 * 历史上出现过「金卡会员」这种不在阶梯里的别名；全等匹配会让它掉到普通档。
 * 顺序从高到低 —— 先判「钻石」再判「金」，避免「铂金」被「金」吃掉。
 */
const THEMES = [
  { key: 'diamond', match: ['钻石'] },
  { key: 'platinum', match: ['铂金'] },
  { key: 'gold', match: ['黄金', '金卡', '金'] },
  { key: 'silver', match: ['白银', '银'] }
]

export function themeOfLevel(name) {
  const value = String(name || '')
  for (const theme of THEMES) {
    if (theme.match.some((word) => value.includes(word))) return theme.key
  }
  return 'common'
}

/**
 * 折扣率 → 「9.5 折」。
 *
 * <p>只做展示换算（×10），**不参与任何金额计算** —— 所有乘法都在服务端。
 * 0.95 → 9.5、0.9 → 9（抹掉末尾零，不显示「9.0 折」）。
 */
export function rateText(rate) {
  const value = Number(rate)
  if (!Number.isFinite(value)) return '—'
  if (value >= 1) return '原价'
  return `${Number((value * 10).toFixed(2))} 折`
}

/** 折扣率 → 周历格子里的短文案（不带「折」字）：`9.5` / `原` */
export function shortRate(rate) {
  const value = Number(rate)
  if (!Number.isFinite(value)) return '—'
  if (value >= 1) return '原'
  return String(Number((value * 10).toFixed(2)))
}
