// 门店信息
export const STORE = {
  name: '隐白咖啡 · 静安嘉里店',
  address: '上海市静安区南京西路 1515 号 · 一层',
  shortAddress: '上海市静安区南京西路1515号 静安嘉里中心',
  latitude: 31.22472,
  longitude: 121.44618,
  distance: '320 米',
  hours: '08:00–20:00'
}

// 业务规则
export const BUSINESS = {
  firstOrderDiscount: 6, // 首单折扣金额
  preparationTime: 5, // 预计制作时间（分钟）
  packagingFee: 0
}

// 产品选项
export const OPTIONS = {
  sizes: [
    { label: '标准杯 240 毫升', price: 0 },
    { label: '大杯 360 毫升', price: 6 }
  ],
  temperatures: ['热', '冰'],
  milks: [
    { label: '全脂奶', price: 0 },
    { label: '燕麦奶', price: 4 },
    { label: '脱脂奶', price: 0 }
  ]
}

// 商品分类名（后端 products.category 只存数字，名称由前端维护）
// 未登记的数字分类会以「分类 N」的形式出现，管理端新增分类不会因此被隐藏。
export const CATEGORY_NAMES = {
  0: '招牌精选',
  1: '意式咖啡'
}

// 图片路径
// 图标不在这里登记：统一图标由 components/ui/Icon.vue 按 config/icon-set.json 解析，
// 避免同一张图在两处各写一遍路径。
export const IMAGES = {
  coffee: {
    latte: '/static/coffee/latte.png',
    latteDetail: '/static/coffee/detail.png',
    americano: '/static/coffee/americano.png',
    banner: '/static/coffee/banner.png'
  }
}
