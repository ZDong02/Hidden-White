/**
 * 日期时间格式化工具
 */

// 补零函数
function pad(num, length = 2) {
  return String(num).padStart(length, '0')
}

// 格式化日期时间（完整）
export function formatDateTime(date) {
  const d = date instanceof Date ? date : new Date(date)

  return {
    date: `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`,
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}`,
    datetime: `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${pad(d.getHours())}:${pad(d.getMinutes())}`,
    orderNumber: `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
  }
}

// 格式化时间（仅时间）
export function formatTime(date) {
  const d = date instanceof Date ? date : new Date(date)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 计算预计取餐时间
export function calculateReadyTime(minutes = 5) {
  const ready = new Date(Date.now() + minutes * 60000)
  return formatTime(ready)
}
