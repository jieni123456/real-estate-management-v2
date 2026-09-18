import http from './http'

/**
 * 房屋接口。对应需求报告 G-024。
 *
 * 本阶段只有「列表」一条；增 / 改 / 删 / 导出在阶段 3 照同样的模式补上。
 */
export function listHouses() {
  return http.get('/houses')
}
