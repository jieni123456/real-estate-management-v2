import axios from 'axios'
import { clearToken, getToken } from './token'

/**
 * HTTP 客户端。对应需求报告 R-004 阶段 2（统一响应外壳 G-023 的前端一侧）。
 *
 * 后端约定：无论成功失败都返回 {code, message, data}，code 为 0 表示成功。
 * 本模块把这层外壳「拆掉」，让业务代码直接拿到 data、失败时拿到一个带 message
 * 的异常 —— 这样每个页面里就不用反复写 body.code === 0 的判断。
 */

/** 带上 HTTP 状态码的异常，便于调用方按 code 分支处理 */
export class ApiError extends Error {
  constructor(message, code) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 「登录状态失效」的回调。
 *
 * 不在这里直接 import router：那会形成 http → router → store → api → http 的环。
 * 改由 main.js 在装配完成后注册进来，职责也更清楚
 * ——http 层只负责「通知」，跳转到哪由应用决定。
 */
let unauthorizedHandler = null

export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

const http = axios.create({
  // 开发期是相对路径 /api，由 Vite 代理转发到后端（见 vite.config.js）
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

/* 请求拦截器：统一带上 token。
 * 放在这里而不是每个请求里手写，是为了保证「没有漏网的请求」。 */
http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    /* 需要读响应头或需要那句 message 时，调用方用 rawResponse 声明，整包返回。
     * 两种真实场景：① 导出下载要读 Content-Disposition 里的文件名；
     * ② 新增 / 编辑成功的说明文字（例如「房东已存在，沿用其原有信息」）
     * 就在 message 上，而默认行为只把 data 交给调用方。 */
    if (response.config?.rawResponse) {
      return response
    }

    const body = response.data

    // 统一外壳：code 为 0 才算成功，把 data 交给调用方
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      return Promise.reject(new ApiError(body.message || '请求失败', body.code))
    }

    // 不是统一外壳（例如将来接了别人家的接口），原样返回
    return body
  },
  (error) => {
    const status = error.response?.status
    const data = error.response?.data
    const message = pickMessage(error, data, status)

    /* 401 有两种含义，必须分开处理：
     *   ① 登录接口返回的 401 —— 就是「用户名或密码错误」，此时用户本来就在登录页，
     *      再跳一次登录页并提示「登录状态已失效」只会让人困惑；
     *   ② 其它接口返回的 401 —— token 过期或无效，确实该退回登录页。
     * 由发起请求的一方用 skipAuthRedirect 声明自己属于哪一种。 */
    if (status === 401 && !error.config?.skipAuthRedirect) {
      clearToken()
      if (unauthorizedHandler) {
        unauthorizedHandler(message)
      }
    }

    return Promise.reject(new ApiError(message, status ?? 0))
  }
)

/** 把各种失败整理成一句能给用户看的话 */
function pickMessage(error, data, status) {
  if (data?.message) {
    return data.message
  }
  if (error.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }
  if (!error.response) {
    // 请求根本没发出去：后端没启动、代理配错、断网
    return '无法连接服务，请确认后端已启动'
  }
  if (status === 404) {
    return '接口不存在（' + (error.config?.url || '') + '），请确认后端版本'
  }
  return `请求失败（HTTP ${status}）`
}

export default http
