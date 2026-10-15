/**
 * 通用的 Composables（Vue 3 组合式函数）
 * 抽取常见的响应式逻辑，供多个组件复用
 */

import { ref, computed } from 'vue'

/**
 * 表单字段管理
 */
export function useFormField(initialValue = '', validator = null) {
  const value = ref(initialValue)
  const error = ref('')
  const touched = ref(false)

  const validate = () => {
    if (!validator) return true
    const result = validator(value.value)
    error.value = result.valid ? '' : result.message
    return result.valid
  }

  const touch = () => {
    touched.value = true
    validate()
  }

  const reset = () => {
    value.value = initialValue
    error.value = ''
    touched.value = false
  }

  return {
    value,
    error,
    touched,
    validate,
    touch,
    reset
  }
}

/**
 * 加载状态管理
 */
export function useLoading(initialState = false) {
  const loading = ref(initialState)
  const error = ref(null)

  const startLoading = () => {
    loading.value = true
    error.value = null
  }

  const stopLoading = () => {
    loading.value = false
  }

  const setError = (err) => {
    error.value = err
    loading.value = false
  }

  const withLoading = async (fn) => {
    startLoading()
    try {
      const result = await fn()
      stopLoading()
      return result
    } catch (err) {
      setError(err)
      throw err
    }
  }

  return {
    loading,
    error,
    startLoading,
    stopLoading,
    setError,
    withLoading
  }
}

/**
 * 列表筛选
 */
export function useFilter(items, filterFn) {
  const filter = ref('')

  const filtered = computed(() => {
    if (!filter.value) return items.value
    return items.value.filter((item) => filterFn(item, filter.value))
  })

  return {
    filter,
    filtered
  }
}

/**
 * 分页管理
 */
export function usePagination(items, pageSize = 10) {
  const currentPage = ref(1)

  const totalPages = computed(() => {
    return Math.ceil(items.value.length / pageSize)
  })

  const paginatedItems = computed(() => {
    const start = (currentPage.value - 1) * pageSize
    const end = start + pageSize
    return items.value.slice(start, end)
  })

  const nextPage = () => {
    if (currentPage.value < totalPages.value) {
      currentPage.value++
    }
  }

  const prevPage = () => {
    if (currentPage.value > 1) {
      currentPage.value--
    }
  }

  const goToPage = (page) => {
    if (page >= 1 && page <= totalPages.value) {
      currentPage.value = page
    }
  }

  return {
    currentPage,
    totalPages,
    paginatedItems,
    nextPage,
    prevPage,
    goToPage
  }
}

/**
 * 切换状态
 */
export function useToggle(initialState = false) {
  const state = ref(initialState)

  const toggle = () => {
    state.value = !state.value
  }

  const setTrue = () => {
    state.value = true
  }

  const setFalse = () => {
    state.value = false
  }

  return {
    state,
    toggle,
    setTrue,
    setFalse
  }
}

/**
 * 倒计时
 */
export function useCountdown(seconds) {
  const remaining = ref(seconds)
  const active = ref(false)
  let timer = null

  const start = () => {
    if (active.value) return
    active.value = true
    timer = setInterval(() => {
      remaining.value--
      if (remaining.value <= 0) {
        stop()
      }
    }, 1000)
  }

  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
    active.value = false
  }

  const reset = (newSeconds) => {
    stop()
    remaining.value = newSeconds || seconds
  }

  return {
    remaining,
    active,
    start,
    stop,
    reset
  }
}
