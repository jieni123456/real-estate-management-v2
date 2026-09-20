import http from './http'

/**
 * 操作日志接口。对应需求报告 G-017 的 Web 端形态（阶段 5 落地）。
 *
 * 日志只有查询，没有增删改 —— 它的价值在于「谁在什么时候动了什么」，
 * 能改就不成其为凭据了。
 */

/**
 * 最近的若干条操作记录，按时间倒序。
 *
 * @param {number} limit 条数。后端会把越界值夹到 1~200 之间，不报错 ——
 *                       这类参数错误没有提示价值，返回能给的限度即可。
 */
export function fetchRecentLogs(limit = 5) {
  return http.get('/logs', { params: { limit } })
}
