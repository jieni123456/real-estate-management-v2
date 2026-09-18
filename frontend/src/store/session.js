import { defineStore } from 'pinia'
import { fetchMe, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/api/token'

/**
 * 当前登录用户。对应需求报告 R-004 阶段 2。
 *
 * 这个 store 是桌面版 core/util/Session.java 在网页端的对应物 ——
 * 但两者有个本质区别：桌面版同时只有一个用户，可以安心用静态字段；
 * 网页端是并发多用户，身份必须「每个请求自带」，所以真相在服务端的 token 里，
 * 这里的 user 只是**当前标签页的缓存**，用于决定界面显示什么。
 */
export const useSessionStore = defineStore('session', {
  state: () => ({
    /** 从 localStorage 恢复，刷新页面不会掉登录 */
    token: getToken(),
    /** {username, role, roleName, permissions}；刷新后由 restore() 重新取回 */
    user: null
  }),

  getters: {
    /** 有 token 就认为已登录；token 是否真的有效由后端说了算 */
    isLoggedIn: (state) => Boolean(state.token),

    /** 应用栏上的「用户名 · 角色名」 */
    displayLabel: (state) =>
      state.user ? `${state.user.username} · ${state.user.roleName}` : '',

    hasPermission: (state) => (permission) =>
      Boolean(state.user?.permissions?.includes(permission))
  },

  actions: {
    async login(username, password) {
      const data = await loginApi(username, password)
      setToken(data.token)
      this.token = data.token

      // 登录响应里已带 username/role/roleName，但权限点清单要另外取，
      // 顺手调一次 /auth/me，让「登录后立刻就能按权限渲染」
      await this.restore()
      return data
    },

    /** 用当前 token 取回身份。刷新页面后靠它恢复 */
    async restore() {
      this.user = await fetchMe()
      return this.user
    },

    async logout() {
      try {
        await logoutApi()
      } catch {
        // 服务端登出只是记日志；即便失败也必须让本地退出，
        // 否则用户会卡在登录态里出不来
      } finally {
        this.clear()
      }
    },

    /** 清空本地登录态。token 失效时由 http 层触发 */
    clear() {
      clearToken()
      this.token = ''
      this.user = null
    }
  }
})
