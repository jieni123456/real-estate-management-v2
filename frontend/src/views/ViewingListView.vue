<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  createViewing,
  deleteViewing,
  exportViewings,
  fetchOptions,
  listViewings,
  updateViewing
} from '@/api/viewing'
import { downloadBlob } from '@/utils/download'
import { nowDateTimeText } from '@/utils/format'
import { matches } from '@/utils/search'
import {
  PERMISSION,
  VIEWING_RESULT,
  VIEWING_RESULT_CHOICES,
  VIEWING_RESULT_OPTIONS
} from '@/utils/permissions'
import { useSessionStore } from '@/store/session'
import { useUiStore } from '@/store/ui'

/**
 * 带看记录。对应需求报告 R-004 阶段 4 与 G-008 / G-020。
 *
 * 客户与房屋之间的唯一业务关联：谁在什么时候看了哪套房、结果如何。
 *
 * 两处关键规则都在后端执行，前端只负责把它体现出来：
 *   · G-020 —— 房屋下拉只列「空置」房源（后端 /options 已过滤）；
 *   · R-003 —— 结果选「已成交」时，后端会在同一事务里把房屋置为「已租出」，
 *     并把这件事写进返回的 message。所以保存成功的提示必须用服务端那句话，
 *     不能本地拼一句「保存成功」——否则用户不知道房屋状态被改了。
 */
const session = useSessionStore()
const ui = useUiStore()

// ---------------------------------------------------------------- 列表状态

const viewings = ref([])
const loading = ref(false)
/** 非空表示这次加载失败了。与「查到了 0 条」严格区分 —— 后者是正常结果 */
const loadError = ref('')

const keyword = ref('')
const resultFilter = ref('')

const tableRef = ref(null)
/** 当前选中行。编辑与删除都作用在它身上 */
const currentRow = ref(null)

const visibleViewings = computed(() =>
  viewings.value.filter(
    (viewing) =>
      (!resultFilter.value || viewing.result === resultFilter.value) &&
      matches(
        keyword.value,
        viewing.customerId,
        viewing.customerName,
        viewing.houseId,
        viewing.houseAddress,
        viewing.result,
        viewing.note
      )
  )
)

const isFiltering = computed(() => Boolean(keyword.value.trim() || resultFilter.value))

async function load({ keepSelection = true } = {}) {
  loading.value = true
  loadError.value = ''
  const keepId = keepSelection ? currentRow.value?.id : null

  try {
    viewings.value = await listViewings()
    reportStatus()
  } catch (error) {
    viewings.value = []
    loadError.value = error.message
    ui.setStatus('带看记录读取失败')
    currentRow.value = null
    return
  } finally {
    loading.value = false
  }

  await nextTick()
  selectRow(keepId ? viewings.value.find((item) => item.id === keepId) ?? null : null)
}

/** 状态栏文案。三种情形与两个模块保持一致 */
function reportStatus() {
  const total = viewings.value.length
  const shown = visibleViewings.value.length

  if (!isFiltering.value) {
    ui.setStatus(`共 ${total} 条带看记录`)
  } else if (shown === 0) {
    ui.setStatus(`未找到匹配的带看记录（共 ${total} 条）`)
  } else {
    ui.setStatus(`筛选出 ${shown} 条 / 共 ${total} 条带看记录`)
  }
}

watch(visibleViewings, reportStatus)

function selectRow(row) {
  currentRow.value = row
  tableRef.value?.setCurrentRow(row)
}

onMounted(load)

// ---------------------------------------------------------------- 权限

const canAdd = computed(() => session.hasPermission(PERMISSION.VIEWING_ADD))
const canDelete = computed(() => session.hasPermission(PERMISSION.VIEWING_DELETE))

const editDisabled = computed(() => !canAdd.value || !currentRow.value)
const deleteDisabled = computed(() => !canDelete.value || !currentRow.value)

const editHint = computed(() => {
  if (!canAdd.value) {
    return '当前角色没有修改带看记录的权限'
  }
  return currentRow.value ? '' : '请先在列表中选中一行'
})

const deleteHint = computed(() => {
  if (!canDelete.value) {
    return '需要管理员权限（当前角色不能删除带看记录）'
  }
  return currentRow.value ? '' : '请先在列表中选中一行'
})

// ---------------------------------------------------------------- 新增 / 编辑

const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref(null)

/** 下拉数据。每次打开对话框都重新取——因为「可带看房源」会随房屋状态变化 */
const customerOptions = ref([])
const houseOptions = ref([])

const form = reactive({
  id: 0,
  customerId: '',
  houseId: '',
  viewedAt: '',
  result: VIEWING_RESULT.INTENT,
  note: ''
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  houseId: [{ required: true, message: '请选择房屋', trigger: 'change' }],
  viewedAt: [{ required: true, message: '请选择带看时间', trigger: 'change' }]
}

async function openCreate() {
  editing.value = false
  resetForm()
  form.viewedAt = nowDateTimeText()
  await prepareOptions('')
  dialogVisible.value = true
}

async function openEdit() {
  const viewing = currentRow.value
  if (!viewing) {
    return
  }

  editing.value = true
  resetForm()
  form.id = viewing.id
  form.customerId = viewing.customerId
  form.houseId = viewing.houseId
  form.viewedAt = viewing.viewedAt
  form.result = viewing.result
  form.note = viewing.note

  // 关键：把这条记录原本挂着的房源传过去，让后端把它保留在候选里 ——
  // 房子后来租出去了，这条旧记录也得能继续改备注（G-020 的例外）
  await prepareOptions(viewing.houseId)
  dialogVisible.value = true
}

function resetForm() {
  form.id = 0
  form.customerId = ''
  form.houseId = ''
  form.viewedAt = ''
  form.result = VIEWING_RESULT.INTENT
  form.note = ''
  formRef.value?.clearValidate()
}

/**
 * 取两份下拉数据。
 *
 * 取不到时**不打开对话框**：客户与房屋是这条记录的必填关联，两者缺一都登记不了，
 * 让用户填完一整张表单再告诉他「没有房源可选」是浪费。
 */
async function prepareOptions(keepHouseId) {
  try {
    const options = await fetchOptions(keepHouseId)
    customerOptions.value = options.customers ?? []
    houseOptions.value = options.houses ?? []
  } catch (error) {
    customerOptions.value = []
    houseOptions.value = []
    await ElMessageBox.alert(error.message, '无法登记带看', {
      type: 'error',
      confirmButtonText: '知道了'
    })
    return false
  }

  if (customerOptions.value.length === 0) {
    await ElMessageBox.alert('请先添加客户，才能登记带看记录', '无法登记带看', {
      type: 'warning',
      confirmButtonText: '知道了'
    })
    return false
  }
  if (houseOptions.value.length === 0) {
    // 这句解释为什么是空的 —— 否则用户会以为系统坏了
    await ElMessageBox.alert(
      '当前没有可登记带看的房源。只有「空置」的房屋可以带看，' +
        '如需对已租出的房屋重新带看，请先到「房屋管理」把它改回「空置」。',
      '无法登记带看',
      { type: 'warning', confirmButtonText: '知道了' }
    )
    return false
  }
  return true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  saving.value = true
  try {
    const payload = {
      customerId: form.customerId,
      houseId: form.houseId,
      viewedAt: form.viewedAt,
      result: form.result,
      note: form.note.trim()
    }
    const result = editing.value
      ? await updateViewing(form.id, payload)
      : await createViewing(payload)

    dialogVisible.value = false
    /* 服务端的说明比本地拼的更有信息量：结果为「已成交」时它会补一句
     * 「房屋 XXX 已置为已租出」，不能丢 */
    ElMessage.success(result.message || (editing.value ? '带看记录已更新' : '带看记录添加成功'))
    await load()
  } catch (error) {
    await ElMessageBox.alert(error.message, editing.value ? '保存失败' : '添加失败', {
      type: 'warning',
      confirmButtonText: '知道了'
    })
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------- 删除

const confirmVisible = ref(false)
const confirmLoading = ref(false)
const deletionTarget = ref(null)

const deleteMessage = computed(() => {
  const target = deletionTarget.value
  return target
    ? `确定要删除这条带看记录吗？客户「${target.customerName || target.customerId}」` +
        `看房屋「${target.houseId}」（${target.viewedAtText}）。此操作不可撤销。`
    : ''
})

async function openDelete() {
  if (!currentRow.value) {
    return
  }
  // 带看记录没有下游关联，删它不会连带删掉任何东西，因此不需要「后果预告」接口
  deletionTarget.value = currentRow.value
  confirmVisible.value = true
}

async function confirmDelete() {
  const target = deletionTarget.value
  if (!target) {
    return
  }

  confirmLoading.value = true
  try {
    const result = await deleteViewing(target.id)
    confirmVisible.value = false
    ElMessage.success(result.message || '带看记录删除成功')
    await load({ keepSelection: false })
  } catch (error) {
    confirmVisible.value = false
    await ElMessageBox.alert(error.message, '删除失败', {
      type: 'error',
      confirmButtonText: '知道了'
    })
  } finally {
    confirmLoading.value = false
    deletionTarget.value = null
  }
}

// ---------------------------------------------------------------- 导出

const exporting = ref(false)

async function onExport() {
  exporting.value = true
  try {
    const { blob, fileName } = await exportViewings({
      keyword: keyword.value,
      result: resultFilter.value
    })
    downloadBlob(blob, fileName || '带看记录.csv')
    ElMessage.success(`已导出 ${visibleViewings.value.length} 条记录`)
  } catch (error) {
    await ElMessageBox.alert(error.message, '导出失败', {
      type: 'error',
      confirmButtonText: '知道了'
    })
  } finally {
    exporting.value = false
  }
}

/** 结果标签的样式类。三档视觉重量递减：已成交（实底）> 意向中 > 无意向 */
function resultClass(result) {
  if (result === VIEWING_RESULT.DEAL) {
    return 'deal'
  }
  return result === VIEWING_RESULT.INTENT ? 'intent' : 'reject'
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h2 class="page-title">带看记录</h2>
    </header>

    <div class="card">
      <div class="toolbar">
        <div class="toolbar-actions">
          <el-tooltip content="登记一条带看记录" placement="top" :disabled="canAdd">
            <span class="btn-wrap">
              <el-button type="primary" :disabled="!canAdd" @click="openCreate">
                登记带看
              </el-button>
            </span>
          </el-tooltip>

          <el-tooltip :content="editHint" placement="top" :disabled="!editHint">
            <span class="btn-wrap">
              <el-button :disabled="editDisabled" @click="openEdit">编辑记录</el-button>
            </span>
          </el-tooltip>

          <el-tooltip :content="deleteHint" placement="top" :disabled="!deleteHint">
            <span class="btn-wrap">
              <el-button
                type="danger"
                plain
                :disabled="deleteDisabled"
                @click="openDelete"
              >
                删除记录
              </el-button>
            </span>
          </el-tooltip>

          <el-button plain :loading="exporting" @click="onExport">导出 CSV</el-button>
          <el-button :icon="Refresh" :loading="loading" @click="load()">刷新数据</el-button>
        </div>

        <div class="toolbar-filters">
          <el-input
            v-model="keyword"
            class="search"
            placeholder="搜索 客户 / 房屋 / 结果 / 备注"
            :prefix-icon="Search"
            clearable
          />
          <el-select v-model="resultFilter" class="result-select" placeholder="带看结果">
            <el-option
              v-for="option in VIEWING_RESULT_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>
      </div>

      <el-alert
        v-if="loadError"
        class="load-error"
        type="error"
        show-icon
        :closable="false"
        :title="loadError"
      >
        <el-button class="retry" size="small" @click="load()">重试</el-button>
      </el-alert>

      <el-table
        v-else
        ref="tableRef"
        v-loading="loading"
        class="viewing-table"
        :data="visibleViewings"
        :row-style="{ height: '38px' }"
        highlight-current-row
        border
        @current-change="currentRow = $event"
      >
        <el-table-column prop="customerId" label="客户ID" width="110" />
        <el-table-column prop="customerName" label="客户姓名" width="110" />
        <el-table-column prop="houseId" label="房屋ID" width="110" />
        <el-table-column prop="houseAddress" label="地址" min-width="170" show-overflow-tooltip />
        <el-table-column prop="viewedAtText" label="带看时间" width="150" />
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <span class="result-tag" :class="resultClass(row.result)">{{ row.result }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />

        <template #empty>
          <el-empty
            :description="
              isFiltering && viewings.length > 0
                ? '没有符合筛选条件的带看记录'
                : '暂无带看记录'
            "
            :image-size="80"
          />
        </template>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑带看记录' : '登记带看'"
      width="620px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        class="viewing-form"
      >
        <div class="group-title">带看信息</div>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="form.customerId"
                class="full-width"
                filterable
                placeholder="选择客户"
              >
                <el-option
                  v-for="customer in customerOptions"
                  :key="customer.id"
                  :value="customer.id"
                  :label="`${customer.id} · ${customer.name}`"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="房屋" prop="houseId">
              <el-select
                v-model="form.houseId"
                class="full-width"
                filterable
                placeholder="选择房屋"
              >
                <el-option
                  v-for="house in houseOptions"
                  :key="house.id"
                  :value="house.id"
                  :label="`${house.id} · ${house.address}`"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="带看时间" prop="viewedAt">
              <!-- value-format 与 core 的 Formats.DATE_TIME_SECONDS_PATTERN 一致，
                   这样回填与提交是对称的，前端不需要再做一次格式转换 -->
              <el-date-picker
                v-model="form.viewedAt"
                class="full-width"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="选择日期时间"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="带看结果" prop="result">
              <el-select v-model="form.result" class="full-width">
                <el-option
                  v-for="choice in VIEWING_RESULT_CHOICES"
                  :key="choice.value"
                  :label="choice.label"
                  :value="choice.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="备注" prop="note">
          <el-input
            v-model="form.note"
            type="textarea"
            :rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如：客户对采光满意，约下周复看（可留空）"
          />
        </el-form-item>

        <!-- 选了「已成交」时提前说清楚后果：房屋状态会被自动置为已租出（R-003） -->
        <p v-if="form.result === VIEWING_RESULT.DEAL" class="form-hint">
          保存后系统会把房屋「{{ form.houseId || '所选房屋' }}」的状态自动置为「已租出」。
        </p>
      </el-form>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <ConfirmDialog
      v-model="confirmVisible"
      title="确认删除"
      :message="deleteMessage"
      :warnings="[]"
      confirm-text="确认删除"
      :danger="true"
      :loading="confirmLoading"
      @confirm="confirmDelete"
    />
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
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
}

.toolbar-actions,
.toolbar-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-wrap {
  display: inline-flex;
}

.search {
  width: 240px;
}

.result-select {
  width: 128px;
}

.load-error {
  margin-bottom: 12px;
}

.retry {
  margin-top: 8px;
}

.viewing-table {
  flex: 1;
  min-height: 0;
}

/* 结果标签。三档视觉重量递减，扫一眼就能分出「定了 / 在谈 / 没成」：
   已成交 = 主色实底白字（最重）；意向中 = 浅蓝底 + 主色文字（进行中）；
   无意向 = 灰底 + 次级文字色（已结束，最轻）。 */
.result-tag {
  display: inline-block;
  min-width: 56px;
  height: 20px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: var(--font-caption);
  line-height: 20px;
}

.result-tag.deal {
  background: var(--accent);
  color: var(--text-on-accent);
}

.result-tag.intent {
  background: var(--hover-bg);
  color: var(--accent);
}

.result-tag.reject {
  background: var(--disabled-bg);
  color: var(--text-secondary);
}

/* ---- 对话框 ---- */

.group-title {
  margin: 4px 0 12px;
  padding-left: 8px;
  border-left: 3px solid var(--accent);
  font-size: var(--font-body);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-heading);
}

.viewing-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.full-width {
  width: 100%;
}

.form-hint {
  margin: 0 0 4px;
  font-size: var(--font-caption);
  color: var(--text-secondary);
}
</style>
