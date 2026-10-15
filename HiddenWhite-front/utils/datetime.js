/**
 * 日期时间格式化工具（增强版）
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

// 格式化日期（仅日期）
export function formatDate(date) {
  const d = date instanceof Date ? date : new Date(date)
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
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

// 生成取餐号（临时方案，实际应由后端生成）
export function generatePickupNumber() {
  return pad(Math.floor(Math.random() * 900) + 100, 3)
}

// 相对时间显示（如：刚刚、5分钟前、昨天）
export function formatRelativeTime(date) {
  const d = date instanceof Date ? date : new Date(date)
  const now = new Date()
  const diff = now - d
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return formatDate(d)
}

// ISO 格式转本地时间
export function parseISODate(isoString) {
  return new Date(isoString)
}

// 检查是否是今天
export function isToday(date) {
  const d = date instanceof Date ? date : new Date(date)
  const today = new Date()
  return d.getDate() === today.getDate() &&
    d.getMonth() === today.getMonth() &&
    d.getFullYear() === today.getFullYear()
}

// 检查是否是昨天
export function isYesterday(date) {
  const d = date instanceof Date ? date : new Date(date)
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  return d.getDate() === yesterday.getDate() &&
    d.getMonth() === yesterday.getMonth() &&
    d.getFullYear() === yesterday.getFullYear()
}
