import type { HttpClient, QueryParams } from './http'
import type { PageResult } from './system'

/**
 * 每周优惠券（对应后端 coupon_config 单行表 + member_coupons 记录表）。
 *
 * <p>{@code week*} 系列字段是「本周」口径：服务端按自然周（周一 00:00 起）算好下发，
 * 前端不做任何日期计算 —— 运营看到的「本周已领 37 张」必须与「周一重置」是同一个边界。
 */
export interface CouponConfig {
  enabled: boolean
  /** 券面额（元） */
  amount: number
  /** 使用门槛：订单商品金额下限（元） */
  minSpend: number
  /** 领取后有效天数 */
  validDays: number
  /** 同时持有上限，0 表示不限 */
  maxHeld: number
  /** 总发行量，null 表示不限量 */
  totalStock: number | null
  issuedCount: number
  remaining: number | null
  title: string
  subtitle: string
  description: string

  /** 本周归属（周一日期，yyyy-MM-dd） */
  weekKey: string
  weekFrom: string
  weekTo: string
  /** 本周重置时刻（下周一 00:00） */
  weekResetAt: string
  /** 每人每周限领张数（固定 1，留给前端展示与将来放开） */
  weeklyLimit: number

  /** 本周领取数 */
  claimedThisWeek: number
  /** 本周核销数 */
  usedThisWeek: number
  /** 累计发放数 */
  claimedTotal: number
  /** 累计核销数 */
  usedTotal: number
  /** 当前未使用且未过期的券数 */
  activeCount: number
  /** 领过券的会员数 */
  memberCount: number
  /** 累计抵扣金额 */
  discountTotal: number
}

export interface CouponConfigWrite {
  enabled: boolean
  amount: number
  minSpend: number
  validDays: number
  maxHeld: number
  totalStock: number | null
  title: string
  subtitle: string
  description: string
}

export type CouponStatus = 'UNUSED' | 'USED' | 'EXPIRED'

/** 发放与使用记录行：既是「谁在哪周领了券」，也是「这张券用在哪笔订单」 */
export interface CouponRecordRow {
  id: string
  memberId: string
  displayName: string
  phone: string | null
  weekKey: string
  title: string
  amount: number
  minSpend: number
  status: CouponStatus
  claimedAt: string
  expiresAt: string
  usedAt: string | null
  orderId: string | null
  orderNumber: string | null
  discountAmount: number
}

/** 筛选下拉的自然周 */
export interface CouponWeekOption {
  key: string
  label: string
}

export function createCouponApi(client: HttpClient) {
  return {
    config: () => client.get<CouponConfig>('/coupons/config'),
    saveConfig: (body: CouponConfigWrite) => client.put<void>('/coupons/config', body),
    records: (query?: QueryParams) => client.get<PageResult<CouponRecordRow>>('/coupons/records', query),
    weeks: () => client.get<CouponWeekOption[]>('/coupons/weeks'),
  }
}
