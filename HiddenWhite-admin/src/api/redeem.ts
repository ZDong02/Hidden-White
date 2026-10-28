import type { HttpClient, QueryParams } from './http'
import type { PageResult } from './system'
import type { CoffeeImageSlot, CoffeeImageSpec, CoffeeImageUpload } from './coffee'

/** 兑换品行（对应 redeem_products 表与 RedeemAdminController 返回字段） */
export interface RedeemProductRow {
  id: string
  name: string
  subtitle: string
  description: string
  image: string
  /** 兑换所需积分 */
  pointsCost: number
  /** 库存：null 表示不限量 */
  stock: number | null
  /** 每人限兑次数，0 表示不限 */
  perMemberLimit: number
  sortOrder: number
  enabled: boolean
  createdAt?: string
}

/**
 * 兑换品写请求。
 *
 * <p>{@code stock} 必须允许 null 且不能省略：null 是「不限量」这个业务语义，
 * 后端据此决定是否做原子递减；传 0 会被理解为「已兑完」。
 */
export interface RedeemProductWrite {
  id?: string
  name: string
  subtitle?: string
  description?: string
  image?: string
  pointsCost: number
  stock: number | null
  perMemberLimit?: number
  sortOrder?: number
  enabled?: boolean
}

/** 兑换单状态，与后端 RedemptionStatus 枚举严格一致 */
export type RedemptionStatus = 'PENDING' | 'REDEEMED' | 'CANCELLED' | 'EXPIRED'

export interface RedemptionRow {
  id: string
  redeemNo: string
  memberId: string
  /** 会员昵称（后端 LEFT JOIN members 带出，便于运营判断要不要联系顾客） */
  memberName: string | null
  productId: string
  productName: string
  productImage: string
  pointsCost: number
  /** 8 位核销码 */
  code: string
  status: RedemptionStatus
  expiresAt: string
  verifiedAt: string | null
  verifiedBy: string | null
  cancelledAt: string | null
  createdAt: string
}

/** 核销成功后的回执：前端直接用它提示「已核销哪一件、扣了多少分」 */
export interface RedeemVerifyResult {
  redeemNo: string
  productName: string
  pointsCost: number
}

export function createRedeemApi(client: HttpClient) {
  return {
    products: {
      list: (query?: QueryParams) => client.get<PageResult<RedeemProductRow>>('/redeem/products', query),
      create: (body: RedeemProductWrite) => client.post<void>('/redeem/products', body),
      // 后端 updateProduct 为整行写入，必须传全量
      update: (id: string, body: RedeemProductWrite) => client.put<void>(`/redeem/products/${id}`, body),
      /** 上下架走独立端点：整行更新要求名称 / 积分价齐全，只带 enabled 会被拒 */
      setEnabled: (id: string, enabled: boolean) =>
        client.put<void>(`/redeem/products/${id}/enabled`, { enabled }),
      remove: (id: string) => client.delete<void>(`/redeem/products/${id}`),
    },
    orders: {
      list: (query?: QueryParams) => client.get<PageResult<RedemptionRow>>('/redeem/orders', query),
      /** 按顾客出示的 8 位核销码核销 */
      verify: (code: string) => client.post<RedeemVerifyResult>('/redeem/orders/verify', { code }),
    },
    images: {
      spec: () => client.get<CoffeeImageSpec>('/redeem/images/spec'),
      upload: (file: File, slot: CoffeeImageSlot) => {
        const form = new FormData()
        form.append('file', file)
        form.append('slot', slot)
        return client.upload<CoffeeImageUpload>('/redeem/images/upload', form)
      },
    },
  }
}
