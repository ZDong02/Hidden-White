import type { HttpClient, QueryParams } from './http'
import type { PageResult } from './system'

/**
 * 生日礼遇配置（对应后端 birthday_gift_config 单行表 + 年度概览）。
 *
 * <p>{@code giftType} 在 V1 只有 {@code POINTS}：赠饮模式需要「0 元兑换单 + 核销」
 * 联动，链路未通。这里保留联合类型而不是写死字符串，等 V2 放开时只需加一个枚举值。
 */
export interface BirthdayConfig {
  enabled: boolean
  giftType: 'POINTS' | 'DRINK'
  pointsAmount: number
  windowMode: 'MONTH' | 'AROUND'
  windowDays: number
  title: string
  subtitle: string
  description: string
  /** 概览：统计年份 */
  year: number
  /** 本年度已发放人次 */
  giftCount: number
  /** 本年度已发放积分合计 */
  pointsTotal: number
  /** 已填写生日的会员数（礼遇可触达人群） */
  birthdayMemberCount: number
}

export interface BirthdayConfigWrite {
  enabled: boolean
  giftType: 'POINTS'
  pointsAmount: number
  windowMode: 'MONTH' | 'AROUND'
  windowDays: number
  title: string
  subtitle: string
  description: string
}

/** 领取记录行 */
export interface BirthdayGiftRow {
  id: number
  memberId: string
  displayName: string
  phone: string | null
  giftYear: number
  giftType: string
  amount: number
  /** 对应积分流水 id，可用来核对账本 */
  pointsLogId: number | null
  createdAt: string
}

export function createBirthdayApi(client: HttpClient) {
  return {
    config: () => client.get<BirthdayConfig>('/birthday/config'),
    saveConfig: (body: BirthdayConfigWrite) => client.put<void>('/birthday/config', body),
    gifts: (query?: QueryParams) => client.get<PageResult<BirthdayGiftRow>>('/birthday/gifts', query),
  }
}
