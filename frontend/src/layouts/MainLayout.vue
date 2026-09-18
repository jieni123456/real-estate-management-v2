<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Expand, Fold, House, SwitchButton } from '@element-plus/icons-vue'
import { useSessionStore } from '@/store/session'
import { useUiStore } from '@/store/ui'

/**
 * 主框架。对应需求报告 R-002 第 5 轮结论（可折叠侧边栏 + 应用栏 + 状态栏）。
 *
 * 四层结构与桌面版一致：应用栏 / 侧边栏 + 内容区 / 状态栏。
 * 应用栏右侧集中放「当前用户 · 角色」与「退出登录」——桌面版里是同一个位置。
 *
 * 阶段 2 只接了房屋管理一个模块。其余模块**不渲染成点不动的灰项**：
 * 那样会让人以为是坏了，不如老实说一句「后续阶段接入」。
 */
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const ui = useUiStore()

const navItems = [{ name: 'houses', title: '房屋管理', icon: House }]

/**
 * 侧边栏只有 120px 宽，长句子会被折成很难看的碎行，
 * 所以这里用换行符手动断句（配合 CSS 的 white-space: pre-line）。
 */
const pendingHint = '更多模块\n后续阶段接入'

/** 收起时只留下图标，所以标题要换成 tooltip 才不会看不懂 */
const collapsed = computed(() => ui.sidebarCollapsed)

function isActive(item) {
  return route.name === item.name
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    // 用户点了取消 —— el-message-box 用 reject 表示取消，这里静默返回即可
    return
  }

  await session.logout()
  ElMessage.success('已退出登录')
  router.replace({ name: 'login' })
}
</script>

<template>
  <div class="layout">
    <!-- 应用栏：系统名 / 当前用户 / 退出登录 -->
    <header class="appbar">
      <button
        class="collapse-btn"
        :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="ui.toggleSidebar()"
      >
        <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
      </button>

      <span class="brand">二手房中介管理系统</span>

      <div class="appbar-right">
        <span class="user-label">{{ session.displayLabel }}</span>
        <el-button
          class="logout-btn"
          type="primary"
          size="small"
          :icon="SwitchButton"
          @click="handleLogout"
        >
          退出登录
        </el-button>
      </div>
    </header>

    <div class="body">
      <!-- 侧边栏：可折叠。宽度由 ui store 控制 -->
      <aside class="sidebar" :class="{ collapsed }">
        <nav>
          <router-link
            v-for="item in navItems"
            :key="item.name"
            class="nav-item"
            :class="{ active: isActive(item) }"
            :to="{ name: item.name }"
            :title="item.title"
          >
            <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
            <span v-if="!collapsed" class="nav-text">{{ item.title }}</span>
          </router-link>
        </nav>

        <p v-if="!collapsed" class="pending">{{ pendingHint }}</p>
      </aside>

      <!-- 内容区：路由匹配到的页面在这里渲染 -->
      <main class="content">
        <router-view />
      </main>
    </div>

    <!-- 状态栏：各页面把「共 N 条…」这类说明写进 ui store -->
    <footer class="statusbar">
      <span>{{ ui.statusText }}</span>
    </footer>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--page-bg);
}

/* ------------------------------------------------------------ 应用栏 */
.appbar {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--appbar-height);
  padding: 0 16px;
  background: var(--accent);
  color: var(--text-on-accent);
  flex: none;
}

.collapse-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-on-accent);
  cursor: pointer;
  font-size: 16px;
}

.collapse-btn:hover {
  background: var(--accent-dark);
}

.brand {
  font-size: var(--font-body);
  font-weight: var(--weight-bold);
  letter-spacing: 0.5px;
}

.appbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.user-label {
  font-size: var(--font-body);
  opacity: 0.95;
}

/* 主色底上的按钮要反过来配色，否则和背景糊在一起 */
.logout-btn {
  --el-button-bg-color: rgba(255, 255, 255, 0.16);
  --el-button-border-color: rgba(255, 255, 255, 0.4);
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.28);
  --el-button-hover-border-color: #ffffff;
  --el-button-text-color: #ffffff;
  --el-button-hover-text-color: #ffffff;
}

/* ------------------------------------------------------------ 主体 */
.body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.sidebar {
  display: flex;
  flex-direction: column;
  width: var(--sidebar-width);
  flex: none;
  padding: 10px 8px;
  background: var(--surface);
  border-right: 1px solid var(--border);
  transition: width 0.16s ease;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-width);
  padding: 10px 4px;
  align-items: center;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  margin-bottom: 4px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: var(--font-body);
}

.sidebar.collapsed .nav-item {
  justify-content: center;
  padding: 0;
  width: 30px;
}

.nav-item:hover {
  background: var(--hover-bg);
  color: var(--text-primary);
}

/* 选中态用主色底 + 白字，与表格选中行是同一套语言 */
.nav-item.active {
  background: var(--accent);
  color: var(--text-on-accent);
}

.nav-icon {
  font-size: 15px;
}

.pending {
  margin: auto 4px 4px;
  font-size: var(--font-caption);
  color: var(--text-secondary);
  /* 手动断好的行要保留，否则 120px 宽度里会被折成碎行 */
  white-space: pre-line;
  line-height: 1.7;
  opacity: 0.85;
}

.content {
  flex: 1;
  min-width: 0;
  overflow: auto;
  padding: 16px;
}

/* ---------------------------------------------------------- 状态栏 */
.statusbar {
  display: flex;
  align-items: center;
  height: var(--statusbar-height);
  padding: 0 16px;
  flex: none;
  background: var(--chrome-bg);
  border-top: 1px solid var(--border);
  font-size: var(--font-caption);
  color: var(--text-secondary);
}
</style>
