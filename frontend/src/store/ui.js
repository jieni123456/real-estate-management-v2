import { defineStore } from 'pinia'

/**
 * 界面状态。对应需求报告 R-004 阶段 2。
 *
 * 状态栏文案与侧边栏折叠状态放在共享 store 里，而不是层层 props 传递
 * —— 它们要被主框架和各个页面同时读写（与桌面版 MainView.setStatus 的用法一致）。
 */
export const useUiStore = defineStore('ui', {
  state: () => ({
    /** 底部状态栏文案。各页面在加载完成后写入「共 N 条…」这样的说明 */
    statusText: '就绪',
    /** 侧边栏是否收起（收起时只留下图标与首个字） */
    sidebarCollapsed: false
  }),

  actions: {
    setStatus(text) {
      this.statusText = text || '就绪'
    },

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
    }
  }
})
