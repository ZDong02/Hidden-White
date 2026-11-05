import type { HttpClient } from './http'

/**
 * 会员等级折扣（对应后端 member_level_policies + member_level_daily_rates）。
 *
 * <p><b>{@code rate} 是「折后价 / 原价」</b>：0.850 表示 8.5 折，1.000 表示不打折。
 * 页面上按「折」展示（8.5），换算只发生在这一层 ——
 * 服务端与数据库一律用比率，避免 8.5 到底是 8.5 折还是 85 折这种歧义。
 */
export interface LevelDiscountRow {
  levelKey: string
  levelName: string
  /** 该等级起点成长值（来自 MemberLevels 阶梯） */
  minGrowth: number
  enabled: boolean
  /** 基准折扣率：0.950 = 9.5 折 */
  baseRate: number
  /** 折扣门槛：商品金额下限，0 为无门槛 */
  minSpend: number
  note: string
  /** 仅包含「单独配置过的星期」：键为 "1"–"7"，缺省的天继承 baseRate */
  dailyRates: Record<string, number>
  memberCount: number
  /** 是否已有策略行；false 表示从未保存过，页面上按默认值展示 */
  configured: boolean
}

export interface LevelDiscountOverview {
  levels: LevelDiscountRow[]
  weekdays: Array<{ weekday: number; label: string }>
  /** 成长值阶梯（含未配置策略的等级） */
  ladder: Array<{ name: string; minGrowth: number }>
  /** 会员身上出现、但不在阶梯中的等级：这些会员永远拿不到折扣 */
  orphanLevels: Array<{ levelKey: string; memberCount: number }>
  todayWeekday: number
  totalMembers: number
}

export interface LevelDiscountWrite {
  levelKey: string
  enabled: boolean
  baseRate: number
  minSpend: number
  note: string
  /** 只传要覆盖的星期；传空对象 = 整周跟随基准 */
  dailyRates: Record<string, number>
}

export function createLevelDiscountApi(client: HttpClient) {
  return {
    overview: () => client.get<LevelDiscountOverview>('/level-discounts'),
    save: (body: LevelDiscountWrite) => client.put<void>('/level-discounts', body),
    setEnabled: (body: { levelKey: string; enabled: boolean }) =>
      client.put<void>('/level-discounts/enabled', body),
  }
}
