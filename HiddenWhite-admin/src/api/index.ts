import { httpClient } from './http.ts'
import { createAuthApi } from './auth.ts'
import { createSystemApi } from './system.ts'
import { createPortalApi } from './portal.ts'
import { createCoffeeApi } from './coffee.ts'
import { createOperationApi } from './operation.ts'
import { createRedeemApi } from './redeem.ts'

export * from './http.ts'
export * from './auth.ts'
export * from './session.ts'
export * from './system.ts'
export * from './portal.ts'
export * from './coffee.ts'
export * from './operation.ts'
export * from './redeem.ts'

export const authApi = createAuthApi(httpClient)
export const systemApi = createSystemApi(httpClient)
export const portalApi = createPortalApi(httpClient)
export const coffeeApi = createCoffeeApi(httpClient)
export const operationApi = createOperationApi(httpClient)
export const redeemApi = createRedeemApi(httpClient)
