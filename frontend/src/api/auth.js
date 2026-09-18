import http from './http'

/**
 * 登录相关接口。对应需求报告 G-024。
 *
 * 注意 login 上的 skipAuthRedirect：登录失败也是 401，但不该触发
 * 「登录状态已失效，请重新登录」那套跳转逻辑（用户本来就在登录页）。
 */
export function login(username, password) {
  return http.post('/auth/login', { username, password }, { skipAuthRedirect: true })
}

/** 当前登录用户（含权限点清单）。刷新页面后用它恢复「我是谁」 */
export function fetchMe() {
  return http.get('/auth/me')
}

/** 退出登录。服务端无状态，真正的登出是前端丢弃 token */
export function logout() {
  return http.post('/auth/logout')
}
