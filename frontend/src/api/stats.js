import http from './http'

/**
 * 概览统计接口。对应需求报告 R-004 阶段 5。
 *
 * 纯查询，因此直接拿拦截器拆壳后的 data —— 与三个列表接口一致。
 * 读取失败会以异常抛出（后端返回 503），页面需要把「读不出来」与
 * 「真的是 0」分开展示，不能都显示成 0。
 */

export function fetchOverview() {
  return http.get('/stats/overview')
}
