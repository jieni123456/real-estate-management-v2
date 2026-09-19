import http from './http'
import { parseFileName } from '@/utils/download'

/**
 * 带看记录接口。对应需求报告 R-004 阶段 4。
 *
 * 返回形态与 house.js / customer.js 一致：查询类直接拿 data，
 * 写入类与导出用 `rawResponse`（需要 message 与响应头里的文件名）。
 */

export function listViewings() {
  return http.get('/viewings')
}

/**
 * 「登记带看」对话框的两份下拉数据。
 *
 * keepHouseId 只在编辑时传：那条记录原本挂着的房源即便已租出也要保留在候选里，
 * 否则房子后来租出去了，这条记录连改个备注都存不下来。
 */
export function fetchOptions(keepHouseId = '') {
  return http.get('/viewings/options', {
    params: keepHouseId ? { keepHouseId } : {}
  })
}

export async function createViewing(payload) {
  const response = await http.post('/viewings', payload, { rawResponse: true })
  return { message: response.data.message }
}

export async function updateViewing(id, payload) {
  const response = await http.put(`/viewings/${id}`, payload, { rawResponse: true })
  return { message: response.data.message }
}

export async function deleteViewing(id) {
  const response = await http.delete(`/viewings/${id}`, { rawResponse: true })
  return { message: response.data.message }
}

/** 导出 CSV。keyword / result 与界面筛选一致，由后端按同一套规则重算 */
export async function exportViewings({ keyword = '', result = '' } = {}) {
  const response = await http.get('/viewings/export', {
    params: { keyword, result },
    responseType: 'blob',
    rawResponse: true
  })

  return {
    blob: response.data,
    fileName: parseFileName(response.headers?.['content-disposition'])
  }
}
