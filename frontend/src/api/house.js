import http from './http'
import { parseFileName } from '@/utils/download'

/**
 * 房屋接口。对应需求报告 R-004 阶段 2（列表）与阶段 3（增改删导出）。
 *
 * 说明这里的两种返回形态：
 *   · 查询类（列表 / 房东 / 删除预告）—— 拦截器拆掉统一外壳后直接给 data；
 *   · 写入类与导出 —— 带上 `rawResponse`，因为要用到外壳里的 message
 *     （「房东已存在，沿用其原有信息」这类说明不能丢），以及响应头里的文件名。
 *     为了不让这两个概念泄漏到页面里，本模块统一把写入类收敛成 `{ message }`。
 */

export function listHouses() {
  return http.get('/houses')
}

/** 房东列表，供对话框下拉选择（G-007） */
export function listLandlords() {
  return http.get('/houses/landlords')
}

/** 删除前的后果预告：会连带删掉多少带看记录、房东会不会被一并清理（G-008 / G-018） */
export function fetchDeletionInfo(id) {
  return http.get(`/houses/${encodeURIComponent(id)}/deletion-info`)
}

export async function createHouse(payload) {
  const response = await http.post('/houses', payload, { rawResponse: true })
  return { message: response.data.message }
}

export async function updateHouse(id, payload) {
  const response = await http.put(`/houses/${encodeURIComponent(id)}`, payload, {
    rawResponse: true
  })
  return { message: response.data.message }
}

export async function deleteHouse(id) {
  const response = await http.delete(`/houses/${encodeURIComponent(id)}`, {
    rawResponse: true
  })
  return { message: response.data.message }
}

/**
 * 导出 CSV。
 *
 * keyword / status 必须与界面上的筛选条件一致——后端会按同样的条件重算一遍，
 * 这样「导出的就是屏幕上看到的」由构造保证，而不是靠前端把行数据传回去。
 */
export async function exportHouses({ keyword = '', status = '' } = {}) {
  const response = await http.get('/houses/export', {
    params: { keyword, status },
    responseType: 'blob',
    rawResponse: true
  })

  return {
    blob: response.data,
    fileName: parseFileName(response.headers?.['content-disposition'])
  }
}

/**
 * 批量导入 CSV。对应需求报告 R-006。
 *
 * 只把文件原样递过去，前端不做解析、不做校验 —— 解析、逐行校验、写库全在
 * core 里，与「添加房屋」走的是同一条路径。这样「从界面上导进去的」与
 * 「手动一条条添加能被接受的」必然是同一套规则，不会出现两套。
 *
 * 注意这里的 Content-Type 不手写：axios 发现 data 是 FormData 时会自己补上
 * 带 boundary 的那一份，手写反而会把 boundary 弄丢，服务端解析不出文件。
 *
 * 失败行是「正常返回」而不是异常：HTTP 仍是 200，逐条原因在 report 里。
 */
export async function importHouses(file) {
  const form = new FormData()
  form.append('file', file)

  const response = await http.post('/houses/import', form, { rawResponse: true })
  return {
    message: response.data.message,
    report: response.data.data
  }
}
