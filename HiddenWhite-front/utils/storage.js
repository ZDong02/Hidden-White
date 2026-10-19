import { toRaw } from 'vue'

/**
 * 统一的本地存储工具
 * 处理序列化、错误捕获和性能优化
 */

// 从本地存储读取数据
export function getStorage(key, fallback = null) {
  try {
    const data = uni.getStorageSync(key)
    return data !== undefined && data !== null ? data : fallback
  } catch (error) {
    console.error(`读取存储失败 [${key}]:`, error)
    return fallback
  }
}

// 保存到本地存储
export function setStorage(key, value) {
  try {
    // uni.setStorageSync 内部会进行 JSON 序列化
    // 使用 toRaw 移除响应式包装，避免存储多余的响应式元数据
    const data = value && typeof value === 'object' ? toRaw(value) : value
    uni.setStorageSync(key, data)
    return true
  } catch (error) {
    console.error(`保存存储失败 [${key}]:`, error)
    return false
  }
}

// 删除存储项
export function removeStorage(key) {
  try {
    uni.removeStorageSync(key)
    return true
  } catch (error) {
    console.error(`删除存储失败 [${key}]:`, error)
    return false
  }
}

// 防抖存储：连续写入时只保存最后一次
const debounceTimers = new Map()

export function setStorageDebounced(key, value, delay = 200) {
  if (debounceTimers.has(key)) {
    clearTimeout(debounceTimers.get(key))
  }

  debounceTimers.set(key, setTimeout(() => {
    debounceTimers.delete(key)
    setStorage(key, value)
  }, delay))
}

// 立即保存（取消防抖）
export function setStorageImmediate(key, value) {
  if (debounceTimers.has(key)) {
    clearTimeout(debounceTimers.get(key))
    debounceTimers.delete(key)
  }
  return setStorage(key, value)
}
