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

/**
 * 订单状态。与后端 OrderStatus 五态严格一致。
 *
 * CREATED=已创建 / PENDING_PAYMENT=待支付 / PAID=已支付制作中 /
 * COMPLETED=已完成 / CANCELLED=已取消。
 * 只有 PAID 能标记完成——「没付钱不能取餐」是状态机的核心不变式。
 */
export type CoffeeOrderStatus = 'CREATED' | 'PENDING_PAYMENT' | 'PAID' | 'COMPLETED' | 'CANCELLED'

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
  status: CoffeeOrderStatus
  note: string
  completedAt: string | null
  /** 支付渠道：WECHAT_JSAPI；发起支付前为空 */
  payChannel?: string | null
  /** 微信支付交易号，对账与退款必需 */
  transactionId?: string | null
  paidAt?: string | null
  refundId?: string | null
  refundedAt?: string | null
  /** 取消原因：TIMEOUT_UNPAID / TIMEOUT_UNPICKED / MANUAL / PAY_FAILED */
  cancelReason?: string | null
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

/** 图片用途：列表图 / 详情图，两者目标宽高独立配置 */
export type CoffeeImageSlot = 'list' | 'detail'

/** 单个用途的图片目标规格 */
export interface CoffeeImageTarget {
  /** 目标宽度（像素） */
  width: number
  /** 目标高度（像素） */
  height: number
  /** 缩放方式：cover 居中裁剪 / contain 等比缩放 */
  mode: 'cover' | 'contain'
}

/** 图片上传规格：各用途目标 + 大小上限 */
export interface CoffeeImageSpec {
  /** 单张图片大小上限（字节） */
  maxBytes: number
  /** 列表图目标 */
  list: CoffeeImageTarget
  /** 详情图目标 */
  detail: CoffeeImageTarget
}

/** 图片上传结果 */
export interface CoffeeImageUpload {
  /** 图片可访问地址 */
  url: string
  /** 处理后宽度 */
  width: number
  /** 处理后高度 */
  height: number
  /** 输出格式 */
  format: string
  /** 输出字节数 */
  size: number
  /** 原图宽度 */
  sourceWidth: number
  /** 原图高度 */
  sourceHeight: number
  /** 原图与目标比例是否不一致（true 表示发生了裁剪） */
  ratioChanged: boolean
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
    images: {
      /** 查询各用途图片的目标规格（用于上传前预校验与提示） */
      spec: () => client.get<CoffeeImageSpec>('/coffee/images/spec'),
      /** 上传图片并按目标比例处理，返回可访问地址 */
      upload: (file: File, slot: CoffeeImageSlot) => {
        const form = new FormData()
        form.append('file', file)
        form.append('slot', slot)
        return client.upload<CoffeeImageUpload>('/coffee/images/upload', form)
      },
    },
  }
}
