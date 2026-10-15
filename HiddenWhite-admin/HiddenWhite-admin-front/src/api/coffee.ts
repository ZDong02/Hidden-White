import type { HttpClient, QueryParams } from './http'
import type { IdResponse, PageResult } from './system'

/** 咖啡业务行类型（对应 hidden_white 库表结构与 CoffeeController 返回字段） */
export interface CoffeeProductRow {
  id: string
  name: string
  subtitle: string
  description: string
  detailDescription: string
  price: number
  image: string
  detailImage: string
  category: number
  hasMilk: boolean
  enabled: boolean
}

export interface CoffeeProductWrite {
  id?: string
  name?: string
  subtitle?: string
  description?: string
  detailDescription?: string
  price?: number
  image?: string
  detailImage?: string
  category?: number
  hasMilk?: boolean
  enabled?: boolean
}

export interface CoffeeOptionRow {
  id: number
  type: 'SIZE' | 'MILK' | 'TEMPERATURE'
  label: string
  extraPrice: number
  sortOrder: number
  enabled: boolean
}

export interface CoffeeOptionWrite {
  type?: string
  label?: string
  extraPrice?: number
  sortOrder?: number
  enabled?: boolean
}

export interface CoffeeOrderItem {
  name: string
  size: string
  temperature: string
  milk: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

export interface CoffeeOrderRow {
  id: string
  memberId: string
  subtotal: number
  discount: number
  packagingFee: number
  total: number
  itemCount: number
  pickup: string
  orderNumber: string
  createdAt: string
  readyAt: string
  status: 'IN_PROGRESS' | 'COMPLETED'
  note: string
  completedAt: string | null
  items?: CoffeeOrderItem[]
}

export interface CoffeeMemberRow {
  id: string
  displayName: string
  phone: string | null
  level: string
  cardNumber: string
  points: number
  coupons: number
  growthValue: number
  createdAt: string
}

export interface CoffeeMemberWrite {
  displayName?: string
  level?: string
  points?: number
  coupons?: number
  growthValue?: number
}

export interface CoffeeBenefitRow {
  id: number
  icon: string
  title: string
  description: string
  sortOrder: number
  enabled: boolean
}

export interface CoffeeBenefitWrite {
  icon?: string
  title?: string
  description?: string
  sortOrder?: number
  enabled?: boolean
}

export interface CoffeeStoreRow {
  name: string
  address: string
  shortAddress: string
  latitude: number
  longitude: number
  distance: string
  openingTime: string
  closingTime: string
}

export interface CoffeeSummary {
  productCount: number
  memberCount: number
  orderCount: number
  inProgressCount: number
}

export function createCoffeeApi(client: HttpClient) {
  function crudPage<Row, WriteBody>(path: string) {
    return {
      list: (query?: QueryParams) => client.get<PageResult<Row>>(path, query),
      create: (body: WriteBody) => client.post<IdResponse>(path, body),
      update: (id: string | number, body: Partial<WriteBody>) => client.put<void>(`${path}/${id}`, body),
      remove: (id: string | number) => client.delete<{ notice?: string }>(`${path}/${id}`),
    }
  }

  return {
    summary: () => client.get<CoffeeSummary>('/coffee/summary'),
    products: crudPage<CoffeeProductRow, CoffeeProductWrite>('/coffee/products'),
    options: {
      list: () => client.get<CoffeeOptionRow[]>('/coffee/options'),
      create: (body: CoffeeOptionWrite) => client.post<IdResponse>('/coffee/options', body),
      update: (id: number, body: CoffeeOptionWrite) => client.put<void>(`/coffee/options/${id}`, body),
      remove: (id: number) => client.delete<void>(`/coffee/options/${id}`),
    },
    orders: {
      list: (query?: QueryParams) => client.get<PageResult<CoffeeOrderRow>>('/coffee/orders', query),
      complete: (id: string) => client.put<void>(`/coffee/orders/${id}/complete`),
      remove: (id: string) => client.delete<void>(`/coffee/orders/${id}`),
    },
    members: {
      list: (query?: QueryParams) => client.get<PageResult<CoffeeMemberRow>>('/coffee/members', query),
      update: (id: string, body: CoffeeMemberWrite) => client.put<void>(`/coffee/members/${id}`, body),
      remove: (id: string) => client.delete<void>(`/coffee/members/${id}`),
    },
    benefits: {
      list: () => client.get<CoffeeBenefitRow[]>('/coffee/benefits'),
      create: (body: CoffeeBenefitWrite) => client.post<IdResponse>('/coffee/benefits', body),
      update: (id: number, body: CoffeeBenefitWrite) => client.put<void>(`/coffee/benefits/${id}`, body),
      remove: (id: number) => client.delete<void>(`/coffee/benefits/${id}`),
    },
    store: {
      get: () => client.get<CoffeeStoreRow>('/coffee/store'),
      update: (body: CoffeeStoreRow) => client.put<void>('/coffee/store', body),
    },
  }
}
