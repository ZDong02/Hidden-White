import type { HttpClient } from './http'

/**
 * 免费升杯策略（对应后端 member_upsize_policies）。
 *
 * <p><b>-1 是「不限」</b>：{@code monthlyLimit = -1} 表示本月次数不限，
 * {@code freeQuota = -1} 表示免费次数不限。页面上要显示成「不限」而不是 -1 ——
 * 运营看到 -1 会以为配错了。
 *
 * <p>{@code sizeDiff} 为 null 时说明 {@code fromSize/toSize} 在
 * {@code product_options} 里找不到，此时该等级的升杯会静默失效，页面必须报警。
 */
export interface UpsizePolicyRow {
  levelKey: string
  levelName: string
  monthlyLimit: number
  freeQuota: number
  pointsCost: number
  fromSize: string
  toSize: string
  enabled: boolean
  note: string
  sortOrder: number
  /** 杯型差价（元）；null 表示杯型配置有问题 */
  sizeDiff: number | null
  sizeValid: boolean
  /** 该等级会员数 */
  memberCount: number
  /** 本月该等级已用次数（status='USED'） */
  usedThisMonth: number
}

export interface UpsizeOverview {
  monthKey: string
  levels: UpsizePolicyRow[]
  /** 可供选择的杯型 label */
  sizes: string[]
  /** 成长值阶梯 */
  ladder: Array<{ name: string; threshold: number }>
  totalMembers: number
}

export interface UpsizePolicyWrite {
  levelKey: string
  monthlyLimit?: number
  freeQuota?: number
  pointsCost?: number
  fromSize?: string
  toSize?: string
  enabled?: boolean
  note?: string
}

export interface UpsizeRecordRow {
  id: number
  member_id: string
  display_name: string | null
  phone: string | null
  level_key: string
  month_key: string
  pay_type: string
  points_cost: number
  size_from: string
  size_to: string
  order_id: string
  order_number: string | null
  status: string
  created_at: string
  released_at: string | null
}

export interface UpsizeRecordQuery {
  pageNum?: number
  pageSize?: number
  monthKey?: string
  payType?: string
  status?: string
  keyword?: string
}

export function createUpsizeApi(client: HttpClient) {
  return {
    overview: () => client.get<UpsizeOverview>('/upsize/policies'),
    save: (body: UpsizePolicyWrite) => client.put<void>('/upsize/policies', body),
    records: (query: UpsizeRecordQuery) => {
      const params = new URLSearchParams()
      Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined && value !== null && `${value}`.length > 0) {
          params.append(key, `${value}`)
        }
      })
      const suffix = params.toString()
      return client.get<{ rows: UpsizeRecordRow[]; total: number; pageNum: number; pageSize: number }>(
        `/upsize/records${suffix ? `?${suffix}` : ''}`,
      )
    },
  }
}
