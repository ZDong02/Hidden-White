/**
 * 坐标归一化工具。
 *
 * 地图 API（微信 wx.openLocation / 高德 URI）对经纬度的宽容度很低：
 * - 传 null / undefined / NaN / 空串 → 地图能打开，但**不落标记**，用户看到一片空白街区；
 * - 传 WGS84（GPS 原始坐标）→ 在高德底图上偏移几百米，店面标记落到隔壁楼；
 * - 传 (0, 0) → 打开几内亚湾海面，这是「接口漏填」最常见的假有值。
 *
 * 所以坐标进地图前统一在本地收紧成「有限数字 + 合法范围 + 非空点」，
 * 非法一律返回 null，由调用方走降级路径，而不是把坏数据塞进 API 到真机上才暴露。
 *
 * 坐标系约定：本文件不转换坐标系，只做校验。入库与常量必须统一为 GCJ-02，
 * 管理端录入门店坐标时也须用「腾讯地图 / 高德坐标拾取器」取点（GCJ-02），
 * 不要直接贴 GPS 设备读数（WGS84）或百度坐标（BD09）。
 */

const LAT_MIN = -90
const LAT_MAX = 90
const LNG_MIN = -180
const LNG_MAX = 180

/** 后端 BigDecimal 可能序列化成数字也可能是字符串，统一转有限数字 */
function toFiniteNumber(value) {
  if (value === null || value === undefined || value === '') return null
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(number) ? number : null
}

/**
 * 校验并归一化门店坐标。
 *
 * @param {number|string|null} latitude 纬度
 * @param {number|string|null} longitude 经度
 * @returns {{ latitude: number, longitude: number } | null} 合法坐标，非法返回 null
 */
export function normalizeCoordinate(latitude, longitude) {
  const lat = toFiniteNumber(latitude)
  const lng = toFiniteNumber(longitude)
  if (lat === null || lng === null) return null
  if (lat < LAT_MIN || lat > LAT_MAX) return null
  if (lng < LNG_MIN || lng > LNG_MAX) return null
  if (lat === 0 && lng === 0) return null
  return { latitude: lat, longitude: lng }
}
