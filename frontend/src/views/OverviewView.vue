<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { fetchRecentLogs } from '@/api/log'
import { fetchOverview } from '@/api/stats'
import { useUiStore } from '@/store/ui'

/**
 * 系统概览页。对应需求报告 G-009 的 Web 端形态（阶段 5 落地）。
 *
 * 与桌面端的 OverviewView 同构：五张指标卡（房源 / 客户 / 房东 / 带看 / 空置）
 * + 户型分布 + 最近操作。之所以做成登录后的默认落地页：几个数字一眼可见，
 * 比直接扔一张空表格更能说明「这个系统里有什么」。
 *
 * 两处与桌面端不同，都是有意的：
 *   ① 户型分布用 CSS 条形图，不引图表库。数据只有几个户型、维度单一，
 *      引一个几十 KB 的图表库不划算（这条与「element-plus 全量引入」那个
 *      遗留问题是同一类权衡，见 G-022）。
 *   ② 最近操作分「时间列 + 内容列」两栏，而不是像桌面端那样拼成一整行字符串
 *      —— HTML 排版是免费的，对齐后更好扫读。内容本身与桌面端一致。
 *
 * 错误处理遵循项目规矩：「读不出来」与「真的是 0」必须分开。统计读失败时
 * 卡片显示占位符并给出提示，而不是显示一排 0 —— 那会被读成「系统里没有数据」。
 */
const ui = useUiStore()

/** 概览页展示最近几条操作（与桌面端一致） */
const RECENT_LOG_LIMIT = 5

const loading = ref(false)
const overview = ref(null)
/** 非空表示统计读取失败。与「真的是 0」严格区分 */
const statsError = ref('')

const logs = ref([])
/** 非空表示日志读取失败。与「暂无操作记录」严格区分 */
const logsError = ref('')

/**
 * 五张卡片。顺序与配色都对齐桌面端（配色取自 theme.css 的 --metric-*，
 * 而那一组又对应桌面端 Theme.METRIC_*）。
 */
const cards = computed(() => {
  const data = overview.value
  const failed = Boolean(statsError.value)

  return [
    { caption: '房源总数', value: failed || !data ? '—' : `${data.houseCount} 套`, accent: 'var(--metric-house)' },
    { caption: '客户总数', value: failed || !data ? '—' : `${data.customerCount} 位`, accent: 'var(--metric-customer)' },
    { caption: '房东总数', value: failed || !data ? '—' : `${data.landlordCount} 位`, accent: 'var(--metric-landlord)' },
    { caption: '带看记录', value: failed || !data ? '—' : `${data.viewingCount} 次`, accent: 'var(--metric-viewing)' },
    { caption: '空置房源', value: failed || !data ? '—' : `${data.vacantCount} 套`, accent: 'var(--metric-vacant)' }
  ]
})

/**
 * 户型分布的行。条形长度以本组最大值归一 —— 数据只有几个户型，
 * 用「占最大值的百分比」比占总数更直观（占总数时短户型几乎看不见）。
 */
const typeRows = computed(() => {
  const counts = overview.value?.typeCounts ?? []
  if (counts.length === 0) {
    return []
  }

  const max = Math.max(...counts.map((item) => item.count))
  return counts.map((item) => ({
    type: item.type,
    count: item.count,
    // 数量不为 0 时给个下限，否则极小的值会缩成一条看不见的线
    percent: max === 0 ? 0 : Math.max(item.count > 0 ? 6 : 0, Math.round((item.count / max) * 100))
  }))
})

/** 操作日志一行：与桌面端 OperationLog.toLine() 用同样的分隔符与顺序 */
function formatLog(log) {
  const parts = []
  parts.push(log.role ? `${log.operator}（${log.role}）` : log.operator || '?')
  if (log.action) {
    parts.push(log.action)
  }
  if (log.target) {
    parts.push(log.target)
  }
  if (log.detail) {
    parts.push(log.detail)
  }
  return parts.join('　')
}

/**
 * 加载两批数据。
 *
 * 刻意用 allSettled 而不是 all：统计与日志是两件互不相干的事，
 * 任何一方读不出来都不该把另一方也拖下水。分别记录各自的失败原因。
 */
async function load() {
  loading.value = true
  try {
    const [statsResult, logResult] = await Promise.allSettled([
      fetchOverview(),
      fetchRecentLogs(RECENT_LOG_LIMIT)
    ])

    if (statsResult.status === 'fulfilled') {
      overview.value = statsResult.value
      statsError.value = ''
      ui.setStatus(
        `共 ${statsResult.value.houseCount} 套房屋（空置 ${statsResult.value.vacantCount} 套）、` +
          `${statsResult.value.customerCount} 位客户、${statsResult.value.viewingCount} 条带看记录`
      )
    } else {
      overview.value = null
      statsError.value = statsResult.reason?.message || '未知错误'
      ui.setStatus('统计数据读取失败')
    }

    if (logResult.status === 'fulfilled') {
      logs.value = logResult.value ?? []
      logsError.value = ''
    } else {
      logs.value = []
      logsError.value = logResult.reason?.message || '未知错误'
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="overview">
    <header class="page-head">
      <h2 class="page-title">系统概览</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
    </header>

    <!-- 统计读不出来时给出明确提示，而不是显示一排 0 -->
    <el-alert
      v-if="statsError"
      class="stats-error"
      type="error"
      :closable="false"
      title="统计数据读取失败"
      :description="statsError"
      show-icon
    />

    <div class="cards">
      <div
        v-for="card in cards"
        :key="card.caption"
        class="card"
        :style="{ '--card-accent': card.accent }"
      >
        <span class="card-caption">{{ card.caption }}</span>
        <span class="card-value">{{ card.value }}</span>
      </div>
    </div>

    <section class="panel">
      <h3 class="panel-title">户型分布</h3>
      <p v-if="typeRows.length === 0" class="panel-empty">暂无房屋数据</p>
      <div v-for="row in typeRows" :key="row.type" class="dist-row">
        <span class="dist-label">{{ row.type }}</span>
        <div class="dist-track">
          <div class="dist-fill" :style="{ width: `${row.percent}%` }" />
        </div>
        <span class="dist-count">{{ row.count }} 套</span>
      </div>
    </section>

    <section class="panel">
      <h3 class="panel-title">最近操作</h3>
      <p v-if="logsError" class="panel-empty failed">操作日志读取失败</p>
      <p v-else-if="logs.length === 0" class="panel-empty">暂无操作记录</p>
      <ul v-else class="log-list">
        <li v-for="(log, index) in logs" :key="index" class="log-row">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-text">{{ formatLog(log) }}</span>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  margin: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-medium);
  color: var(--text-heading);
}

.stats-error {
  margin: 0;
}

/* ------------------------------------------------------------ 指标卡 */
.cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

/* 窗口窄时退成两列，避免五张卡被压成一条条 */
@media (max-width: 1080px) {
  .cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

/* 左侧强调短条。只占垂直中线一段，与桌面端画法一致 */
.card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 30px;
  border-radius: 3px;
  background: var(--card-accent);
}

.card-caption {
  font-size: var(--font-caption);
  color: var(--text-secondary);
}

.card-value {
  font-size: var(--font-metric);
  font-weight: var(--weight-medium);
  color: var(--text-heading);
  line-height: 1.2;
}

/* ------------------------------------------------------------ 通用卡片 */
.panel {
  padding: 14px 18px 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
}

.panel-title {
  margin: 0 0 14px;
  font-size: var(--font-title);
  font-weight: var(--weight-medium);
  color: var(--text-heading);
}

.panel-empty {
  margin: 0;
  font-size: var(--font-caption);
  color: var(--text-secondary);
}

/* 「读不出来」用危险色，与「暂无数据」在视觉上就分得开 */
.panel-empty.failed {
  color: var(--danger);
}

/* ------------------------------------------------------------ 户型分布 */
.dist-row {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 32px;
}

.dist-label {
  width: 100px;
  flex: none;
  font-size: var(--font-body);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dist-track {
  flex: 1;
  min-width: 40px;
  height: 10px;
  border-radius: 10px;
  background: var(--border-light);
  overflow: hidden;
}

.dist-fill {
  height: 100%;
  border-radius: 10px;
  background: var(--accent);
  transition: width 0.25s ease;
}

.dist-count {
  width: 54px;
  flex: none;
  text-align: right;
  font-size: var(--font-caption);
  color: var(--text-secondary);
}

/* ------------------------------------------------------------ 最近操作 */
.log-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.log-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  height: 28px;
  font-size: var(--font-body);
  color: var(--text-primary);
}

.log-time {
  width: 92px;
  flex: none;
  font-size: var(--font-caption);
  color: var(--text-secondary);
}

.log-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
