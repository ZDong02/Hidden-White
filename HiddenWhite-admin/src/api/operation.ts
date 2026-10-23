import type { HttpClient, QueryParams } from './http'
import type { PageResult } from './system'

/** 店铺运营行类型（对应 hidden_white 库表结构与 OperationController 返回字段） */

export interface OperationCurrent {
  status: 'OPEN' | 'CLOSED'
  reason: '' | 'MANUAL' | 'DAY_OFF' | 'NOT_YET_OPEN' | 'AFTER_HOURS'
  note: string
}

export interface OperationDayHours {
  dayOfWeek: number
  openTime: string
  closeTime: string
  closed: boolean
}

export interface OperationStatus {
  manualClosed: boolean
  statusNote: string
  current: OperationCurrent
  today: OperationDayHours
  tomorrow: OperationDayHours
  traffic: {
    date: string
    orderCount: number
    revenue: number
    inProgressCount: number
    settled: boolean
  }
}

export interface OperationHours {
  defaultOpen: string
  defaultClose: string
  days: OperationDayHours[]
}

export interface OperationHoursWrite {
  defaultOpen?: string
  defaultClose?: string
  days?: OperationDayHours[]
}

export interface OperationTrafficToday {
  date: string
  orderCount: number
  revenue: number
  visitorEstimate: number
  inProgressCount: number
  settled: boolean
}

export interface OperationTrafficRow {
  statDate: string
  orderCount: number
  revenue: number
  visitorCount: number
  closedMinutes: number
  originOrderCount: number | null
  originRevenue: number | null
  originVisitorCount: number | null
  adjusted: boolean
  adjustReason: string
  settled: boolean
}

export interface OperationTrafficAdjust {
  orderCount?: number
  revenue?: number
  visitorCount?: number
  reason?: string
}

export function createOperationApi(client: HttpClient) {
  return {
    status: {
      get: () => client.get<OperationStatus>('/operation/status'),
      update: (body: { manualClosed: boolean; statusNote?: string }) =>
        client.put<OperationStatus>('/operation/status', body),
    },
    hours: {
      get: () => client.get<OperationHours>('/operation/hours'),
      update: (body: OperationHoursWrite) => client.put<void>('/operation/hours', body),
    },
    traffic: {
      today: () => client.get<OperationTrafficToday>('/operation/traffic/today'),
      list: (query?: QueryParams) => client.get<PageResult<OperationTrafficRow>>('/operation/traffic', query),
      adjust: (date: string, body: OperationTrafficAdjust) => client.put<void>(`/operation/traffic/${date}`, body),
    },
  }
}
