// 格式化日期时间
export function formatDateTime(date) {
  const d = date instanceof Date ? date : new Date(date)
  const pad = (n) => String(n).padStart(2, '0')
  
  return {
    date: `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`,
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}`,
    orderNumber: `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
  }
}

// 计算预计取餐时间
export function calculateReadyTime(minutes = 5) {
  const ready = new Date(Date.now() + minutes * 60000)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(ready.getHours())}:${pad(ready.getMinutes())}`
}

// 生成取餐号（临时方案，实际应由后端生成）
export function generatePickupNumber() {
  return String(Math.floor(Math.random() * 900) + 100).padStart(3, '0')
}
