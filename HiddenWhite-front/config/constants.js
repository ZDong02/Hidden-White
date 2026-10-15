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

// 图片路径
export const IMAGES = {
  coffee: {
    latte: '/static/coffee/latte.png',
    latteDetail: '/static/coffee/detail.png',
    americano: '/static/coffee/americano.png',
    banner: '/static/coffee/banner.png'
  },
  icons: {
    pin: '/static/icons/pin.png',
    back: '/static/icons/back.png',
    wifi: '/static/icons/wifi.png',
    wallet: '/static/icons/wallet.png',
    cup: '/static/icons/cup.png',
    orders: '/static/icons/orders.png',
    user: '/static/icons/user.png'
  }
}
