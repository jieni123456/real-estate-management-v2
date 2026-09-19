import { createRouter, createWebHistory } from 'vue-router'
import { useSessionStore } from '@/store/session'

/**
 * 路由表与登录守卫。对应需求报告 R-004 阶段 2。
 *
 * 路由懒加载（() => import(...)）不是必要的：这个规模的项目一次全打进去也不慢。
 * 用它主要是习惯 —— 每个页面被拆成独立的 chunk，将来页面变多时首屏不会跟着变大。
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: { name: 'houses' } },
      {
        path: 'houses',
        name: 'houses',
        component: () => import('@/views/HouseListView.vue'),
        meta: { title: '房屋管理' }
      },
      {
        path: 'customers',
        name: 'customers',
        component: () => import('@/views/CustomerListView.vue'),
        meta: { title: '客户管理' }
      },
      {
        path: 'viewings',
        name: 'viewings',
        component: () => import('@/views/ViewingListView.vue'),
        meta: { title: '带看记录' }
      }
    ]
  },
  // 未匹配的地址一律回首页，避免出现空白页
  { path: '/:pathMatch(.*)*', redirect: { name: 'houses' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/**
 * 全局前置守卫。规则只有两条：
 *   · 没登录 → 只能进 public 页面，其余一律跳登录页，并记住原本想去的地址
 *   · 已登录 → 再打开登录页就没意义了，直接送回首页
 *
 * 说明：这里拦的是「页面访问」，不是「数据安全」。真正的防线在后端
 * ——即便有人手动改地址栏进来，页面里的每个请求都会被后端按 token 校验。
 *
 * 守卫写成 async 是为了「刷新页面后把身份取回来」这一步：
 * token 存在 localStorage 里能活过刷新，但用户名与权限清单在内存里，
 * 刷新后就没了 —— 不补这一步，应用栏的用户名会是一片空白。
 */
router.beforeEach(async (to) => {
  const session = useSessionStore()

  if (session.isLoggedIn && !session.user) {
    try {
      await session.restore()
    } catch {
      // token 已失效：http 层会清掉它并触发跳转登录页，
      // 这里不再往上抛，交给下面的判断按「未登录」处理即可
    }
  }

  if (to.meta.public) {
    return session.isLoggedIn ? { name: 'houses' } : true
  }

  if (!session.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

export default router
