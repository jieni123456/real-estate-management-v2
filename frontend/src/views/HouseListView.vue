<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listHouses } from '@/api/house'
import { formatArea } from '@/utils/format'
import { matches } from '@/utils/search'
import { HOUSE_STATUS, HOUSE_STATUS_OPTIONS } from '@/utils/permissions'
import { useUiStore } from '@/store/ui'

/**
 * 房屋列表。对应需求报告 G-024（阶段 2 的落地页）与 G-004（查询筛选）。
 *
 * 筛选放在前端做：本系统的数据量是「一个中介门店」级别，全量取回后在内存里过滤，
 * 打字零延迟，也省掉一次往返。桌面版用的也是同一套思路（见 HouseView.applyFilter）。
 * 筛选规则由 utils/search.js 提供，与 core 的 SearchMatcher 是**同一套语义**。
 *
 * 数据量真的大起来（几万条）再改成服务端分页 + 条件查询，那时接口要加参数，
 * 是另一个阶段的取舍 —— 现在提前做属于过早优化。
 */
const ui = useUiStore()

const houses = ref([])
const loading = ref(false)
/** 非空表示这次加载失败了。与「查到了 0 条」严格区分 —— 后者是正常结果 */
const loadError = ref('')

const keyword = ref('')
const statusFilter = ref('')

const visibleHouses = computed(() =>
  houses.value.filter(
    (house) =>
      (!statusFilter.value || house.status === statusFilter.value) &&
      matches(
        keyword.value,
        house.id,
        house.type,
        house.address,
        house.landlordId,
        house.landlordName,
        house.landlordContact
      )
  )
)

const isFiltering = computed(() => Boolean(keyword.value.trim() || statusFilter.value))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    houses.value = await listHouses()
    reportStatus()
  } catch (error) {
    // 读失败时清空列表：留着上一次的数据会让人以为是最新的
    houses.value = []
    loadError.value = error.message
    ui.setStatus('房屋数据读取失败')
  } finally {
    loading.value = false
  }
}

/** 状态栏文案。与桌面版 HouseView.reportStatus 的三种情形一致 */
function reportStatus() {
  const total = houses.value.length
  const shown = visibleHouses.value.length

  if (!isFiltering.value) {
    ui.setStatus(`共 ${total} 条房屋记录`)
  } else if (shown === 0) {
    ui.setStatus(`未找到匹配的房屋（共 ${total} 条）`)
  } else {
    ui.setStatus(`筛选出 ${shown} 条 / 共 ${total} 条房屋记录`)
  }
}

watch(visibleHouses, reportStatus)

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h2 class="page-title">房屋管理</h2>
    </header>

    <div class="card">
      <!-- 工具栏：搜索 / 状态筛选 / 刷新。增删改在阶段 3 接入 -->
      <div class="toolbar">
        <el-input
          v-model="keyword"
          class="search"
          placeholder="搜索 ID / 户型 / 地址 / 房东"
          :prefix-icon="Search"
          clearable
        />
        <el-select v-model="statusFilter" class="status-select" placeholder="状态">
          <el-option
            v-for="option in HOUSE_STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
      </div>

      <!-- 读失败：给明确原因 + 重试入口，而不是显示一张空表 -->
      <el-alert
        v-if="loadError"
        class="load-error"
        type="error"
        show-icon
        :closable="false"
        :title="loadError"
      >
        <el-button class="retry" size="small" @click="load">重试</el-button>
      </el-alert>

      <el-table
        v-else
        v-loading="loading"
        class="house-table"
        :data="visibleHouses"
        :row-style="{ height: '38px' }"
        border
      >
        <el-table-column prop="id" label="ID" width="110" />
        <el-table-column prop="type" label="户型" width="100" />
        <el-table-column label="面积(m²)" width="100" align="right">
          <template #default="{ row }">{{ formatArea(row.area) }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <span
              class="status-tag"
              :class="row.status === HOUSE_STATUS.RENTED ? 'rented' : 'vacant'"
            >
              {{ row.status }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="landlordId" label="房东ID" width="100" />
        <el-table-column prop="landlordName" label="房东姓名" width="110" />
        <el-table-column prop="landlordContact" label="房东电话" min-width="130" />

        <template #empty>
          <!-- 有数据但被筛掉了 / 本来就没有数据，是两回事，提示也要不同 -->
          <el-empty
            :description="
              isFiltering && houses.length > 0
                ? '没有符合筛选条件的房屋'
                : '暂无房屋数据'
            "
            :image-size="80"
          />
        </template>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

.page-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-heading);
}

.card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding: 14px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.search {
  width: 260px;
}

.status-select {
  width: 130px;
}

.load-error {
  margin-bottom: 12px;
}

.retry {
  margin-top: 8px;
}

.house-table {
  flex: 1;
  min-height: 0;
}

/* 状态标签。取值与桌面版的 StatusCellRenderer 一致：
   「已租出」用主色实底 + 白字，「空置」用浅灰底 + 次级文字色 ——
   两者的视觉重量刻意不同，扫一眼就能分出哪些房子还在手上。 */
.status-tag {
  display: inline-block;
  min-width: 56px;
  height: 20px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: var(--font-caption);
  line-height: 20px;
}

.status-tag.rented {
  background: var(--accent);
  color: var(--text-on-accent);
}

.status-tag.vacant {
  background: var(--disabled-bg);
  color: var(--text-secondary);
}
</style>
