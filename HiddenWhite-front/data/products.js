import { IMAGES } from '../config/constants'

export const productData = [
  {
    id: 'latte',
    name: '招牌馥白',
    subtitle: '双份浓缩 · 丝绒奶感',
    description: '浓郁咖啡与细腻奶泡的平衡。坚果香气，绵长回甘，每一杯现点现做。',
    detailDescription: '双份精萃浓缩 · 中深烘焙',
    price: 32,
    image: IMAGES.coffee.latte,
    detailImage: IMAGES.coffee.latteDetail,
    category: 0,
    hasMilk: true
  },
  {
    id: 'americano',
    name: '经典美式',
    subtitle: '纯粹浓郁 · 干净回甘',
    description: '双份浓缩与纯净水的恰好融合。醇厚香气，干净回甘，每一杯现点现做。',
    detailDescription: '纯粹浓郁 · 中深烘焙',
    price: 26,
    image: IMAGES.coffee.americano,
    detailImage: IMAGES.coffee.americano,
    category: 1,
    hasMilk: false
  }
]

export const categories = [
  { id: 'all', name: '全部' },
  { id: 0, name: '招牌精选' },
  { id: 1, name: '意式咖啡' }
]
