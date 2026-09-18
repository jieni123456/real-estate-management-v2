/**
 * token 的存取。对应需求报告 R-004 阶段 2。
 *
 * 单独成一个模块，是为了**避免循环依赖**：
 *   http.js 需要 token  →  session store 也需要 token
 *   session store 需要调 http.js 发登录请求
 * 如果 http.js 直接 import store，就形成 http → store → api → http 的环。
 * 把「往哪存」这件事摘出来给两边共用，环自然解开。
 *
 * 存 localStorage 而不是内存：刷新页面后还能保持登录。
 * 代价是 XSS 能读到它 —— 这是 JWT + localStorage 方案的固有弱点，
 * 更稳的做法是 HttpOnly Cookie，但那样就要处理 CSRF，属于另一层取舍。
 */

const TOKEN_KEY = 'realestate.token'

/** 读取 token；没有时返回空串，调用方不必判 null */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}
