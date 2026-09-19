import http from './http'
import { parseFileName } from '@/utils/download'

/**
 * 客户接口。对应需求报告 R-004 阶段 4。
 *
 * 返回形态与 house.js 一致（两种，已在此收敛）：
 *   · 查询类（列表 / 删除预告）—— 拦截器拆掉统一外壳后直接给 data；
 *   · 写入类与导出 —— 带 `rawResponse`，因为要用外壳里的 message 与响应头里的文件名。
 *   · 为了不把这两个概念泄漏到页面里，写入类统一收敛成 `{ message }`。
 */

export function listCustomers() {
  return http.get('/customers')
}

/** 删除前的后果预告：会连带删掉多少条带看记录（G-008 的级联删除） */
export function fetchDeletionInfo(id) {
  return http.get(`/customers/${encodeURIComponent(id)}/deletion-info`)
}

export async function createCustomer(payload) {
  const response = await http.post('/customers', payload, { rawResponse: true })
  return { message: response.data.message }
}

export async function updateCustomer(id, payload) {
  const response = await http.put(`/customers/${encodeURIComponent(id)}`, payload, {
    rawResponse: true
  })
  return { message: response.data.message }
}

export async function deleteCustomer(id) {
  const response = await http.delete(`/customers/${encodeURIComponent(id)}`, {
    rawResponse: true
  })
  return { message: response.data.message }
}

/**
 * 导出 CSV。
 *
 * keyword 必须与界面上的筛选条件一致——后端会按同样的条件重算一遍，
 * 这样「导出的就是屏幕上看到的」由构造保证。
 */
export async function exportCustomers({ keyword = '' } = {}) {
  const response = await http.get('/customers/export', {
    params: { keyword },
    responseType: 'blob',
    rawResponse: true
  })

  return {
    blob: response.data,
    fileName: parseFileName(response.headers?.['content-disposition'])
  }
}
