<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  createCustomer,
  deleteCustomer,
  exportCustomers,
  fetchDeletionInfo,
  listCustomers,
  updateCustomer
} from '@/api/customer'
import { downloadBlob } from '@/utils/download'
import { matches } from '@/utils/search'
import { PERMISSION } from '@/utils/permissions'
import { useSessionStore } from '@/store/session'
import { useUiStore } from '@/store/ui'

/**
 * 客户管理。对应需求报告 R-004 阶段 4。
 *
 * 结构与 HouseListView 同构——阶段 3 把房屋那条链路走通并验证过，客户照它复制。
 * 与房屋相比少两样东西：没有状态列（客户无状态属性），对话框不需要分组
 * （只有 4 个字段，凑不成两组）。
 *
 * 筛选放在前端做（全量取回后在内存里过滤，打字零延迟）；导出时把同一套条件交给
 * 后端重算，规则由 utils/search.js 与 core 的 SearchMatcher 各持一份、语义一致。
 */
const session = useSessionStore()
const ui = useUiStore()

// ---------------------------------------------------------------- 列表状态

const customers = ref([])
const loading = ref(false)
/** 非空表示这次加载失败了。与「查到了 0 条」严格区分 —— 后者是正常结果 */
const loadError = ref('')

const keyword = ref('')
const tableRef = ref(null)
/** 当前选中行。编辑与删除都作用在它身上 */
const currentRow = ref(null)

const visibleCustomers = computed(() =>
  customers.value.filter((customer) =>
    matches(
      keyword.value,
      customer.id,
      customer.name,
      customer.phone,
      customer.requirements
    )
  )
)

const isFiltering = computed(() => Boolean(keyword.value.trim()))

async function load({ keepSelection = true } = {}) {
  loading.value = true
  loadError.value = ''
  const keepId = keepSelection ? currentRow.value?.id : null

  try {
    customers.value = await listCustomers()
    reportStatus()
  } catch (error) {
    // 读失败时清空列表：留着上一次的数据会让人以为是最新的
    customers.value = []
    loadError.value = error.message
    ui.setStatus('客户数据读取失败')
    currentRow.value = null
    return
  } finally {
    loading.value = false
  }

  // 刷新会换掉整批对象，之前选中的那一行要按 ID 重新找回
  await nextTick()
  selectRow(keepId ? customers.value.find((item) => item.id === keepId) ?? null : null)
}

/** 状态栏文案。三种情形与桌面版 CustomerView 一致 */
function reportStatus() {
  const total = customers.value.length
  const shown = visibleCustomers.value.length

  if (!isFiltering.value) {
    ui.setStatus(`共 ${total} 条客户记录`)
  } else if (shown === 0) {
    ui.setStatus(`未找到匹配的客户（共 ${total} 条）`)
  } else {
    ui.setStatus(`筛选出 ${shown} 条 / 共 ${total} 条客户记录`)
  }
}

watch(visibleCustomers, reportStatus)

function selectRow(row) {
  currentRow.value = row
  tableRef.value?.setCurrentRow(row)
}

onMounted(load)

// ---------------------------------------------------------------- 权限

const canAdd = computed(() => session.hasPermission(PERMISSION.CUSTOMER_ADD))
const canDelete = computed(() => session.hasPermission(PERMISSION.CUSTOMER_DELETE))

/** 编辑沿用「写」权限：两个角色都有。见需求报告 R-001 的权限矩阵 */
const editDisabled = computed(() => !canAdd.value || !currentRow.value)
const deleteDisabled = computed(() => !canDelete.value || !currentRow.value)

/** 按钮为何不可点——禁用而不说原因，用户只会以为界面坏了 */
const editHint = computed(() => {
  if (!canAdd.value) {
    return '当前角色没有修改客户数据的权限'
  }
  return currentRow.value ? '' : '请先在列表中选中一行'
})

const deleteHint = computed(() => {
  if (!canDelete.value) {
    return '需要管理员权限（当前角色不能删除客户）'
  }
  return currentRow.value ? '' : '请先在列表中选中一行'
})

// ---------------------------------------------------------------- 新增 / 编辑

const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref(null)

const form = reactive({
  id: '',
  name: '',
  phone: '',
  requirements: ''
})

/* 客户端只做「必填」这类即时反馈，**规则以后端为准**：
 * 电话格式、长度上限、ID 是否已被占用，全部由 core 的 Validators +
 * CustomerController 判定，失败原因会原样回显。校验规则只有一份实现。 */
const rules = {
  id: [{ required: true, message: '请填写客户ID', trigger: 'blur' }],
  name: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请填写电话', trigger: 'blur' }]
}

function openCreate() {
  editing.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit() {
  const customer = currentRow.value
  if (!customer) {
    return
  }
  editing.value = true
  resetForm()
  form.id = customer.id
  form.name = customer.name
  form.phone = customer.phone
  form.requirements = customer.requirements
  dialogVisible.value = true
}

function resetForm() {
  form.id = ''
  form.name = ''
  form.phone = ''
  form.requirements = ''
  formRef.value?.clearValidate()
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  saving.value = true
  try {
    const payload = {
      id: form.id.trim(),
      name: form.name.trim(),
      phone: form.phone.trim(),
      requirements: form.requirements.trim()
    }
    const result = editing.value
      ? await updateCustomer(form.id, payload)
      : await createCustomer(payload)

    const savedId = payload.id
    dialogVisible.value = false
    ElMessage.success(result.message || (editing.value ? '客户已更新' : '客户添加成功'))
    await load()
    if (!editing.value) {
      await nextTick()
      selectRow(customers.value.find((item) => item.id === savedId) ?? null)
    }
  } catch (error) {
    /* 失败必须打断：校验不过、ID 冲突这些原因要用户看清并修改，
     * 所以对话框不关、原因用模态提示（R-002 第 9 轮：错误提示保留模态框） */
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
const deletionInfo = ref(null)

const deleteMessage = computed(() => {
  const target = deletionTarget.value
  return target
    ? `确定要删除客户「${target.name}」（ID: ${target.id}）吗？此操作不可撤销。`
    : ''
})

/** 连带后果逐条列出。级联删除不能是隐形的（G-008） */
const deleteWarnings = computed(() => {
  const info = deletionInfo.value
  if (!info || info.viewingCount <= 0) {
    return []
  }
  return [`该客户有 ${info.viewingCount} 条带看记录，将一并删除。`]
})

async function openDelete() {
  const target = currentRow.value
  if (!target) {
    return
  }

  try {
    // 先问清后果，再弹确认框——顺序反了就成了「先问要不要删、再说会删掉什么」
    deletionInfo.value = await fetchDeletionInfo(target.id)
  } catch (error) {
    await ElMessageBox.alert(error.message, '无法删除', {
      type: 'error',
      confirmButtonText: '知道了'
    })
    return
  }

  deletionTarget.value = target
  confirmVisible.value = true
}

async function confirmDelete() {
  const target = deletionTarget.value
  if (!target) {
    return
  }

  confirmLoading.value = true
  try {
    const result = await deleteCustomer(target.id)
    confirmVisible.value = false
    ElMessage.success(result.message || '客户删除成功')
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
    deletionInfo.value = null
  }
}

// ---------------------------------------------------------------- 导出

const exporting = ref(false)

async function onExport() {
  exporting.value = true
  try {
    const { blob, fileName } = await exportCustomers({ keyword: keyword.value })
    downloadBlob(blob, fileName || '客户列表.csv')
    ElMessage.success(`已导出 ${visibleCustomers.value.length} 条记录`)
  } catch (error) {
    await ElMessageBox.alert(error.message, '导出失败', {
      type: 'error',
      confirmButtonText: '知道了'
    })
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h2 class="page-title">客户管理</h2>
    </header>

    <div class="card">
      <div class="toolbar">
        <div class="toolbar-actions">
          <el-tooltip content="新增一位客户" placement="top" :disabled="canAdd">
            <span class="btn-wrap">
              <el-button type="primary" :disabled="!canAdd" @click="openCreate">
                添加客户
              </el-button>
            </span>
          </el-tooltip>

          <el-tooltip :content="editHint" placement="top" :disabled="!editHint">
            <span class="btn-wrap">
              <el-button :disabled="editDisabled" @click="openEdit">编辑客户</el-button>
            </span>
          </el-tooltip>

          <!-- R-002 第 7 轮：删除用红字红边，与主操作区分开；
               禁用态一眼可辨，用户不会以为按钮坏了 -->
          <el-tooltip :content="deleteHint" placement="top" :disabled="!deleteHint">
            <span class="btn-wrap">
              <el-button
                type="danger"
                plain
                :disabled="deleteDisabled"
                @click="openDelete"
              >
                删除客户
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
            placeholder="搜索 ID / 姓名 / 电话 / 需求"
            :prefix-icon="Search"
            clearable
          />
        </div>
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
        <el-button class="retry" size="small" @click="load()">重试</el-button>
      </el-alert>

      <el-table
        v-else
        ref="tableRef"
        v-loading="loading"
        class="customer-table"
        :data="visibleCustomers"
        :row-style="{ height: '38px' }"
        highlight-current-row
        border
        @current-change="currentRow = $event"
      >
        <el-table-column prop="id" label="ID" width="120" />
        <el-table-column prop="name" label="姓名" width="130" />
        <el-table-column prop="phone" label="电话" width="160" />
        <el-table-column prop="requirements" label="需求描述" min-width="240" show-overflow-tooltip />

        <template #empty>
          <!-- 有数据但被筛掉了 / 本来就没有数据，是两回事，提示也要不同 -->
          <el-empty
            :description="
              isFiltering && customers.length > 0
                ? '没有符合筛选条件的客户'
                : '暂无客户数据'
            "
            :image-size="80"
          />
        </template>
      </el-table>
    </div>

    <!-- 添加 / 编辑共用同一个对话框。客户只有 4 个字段，凑不成两组，因此不分组 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑客户' : '添加客户'"
      width="560px"
      align-center
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        class="customer-form"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="客户ID" prop="id">
              <el-input
                v-model="form.id"
                :disabled="editing"
                placeholder="唯一标识，编辑时不可改"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" class="phone-input" placeholder="11 位手机号" />
        </el-form-item>

        <el-form-item label="需求描述" prop="requirements">
          <el-input
            v-model="form.requirements"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="如：两居、预算 3000 以内、近地铁（可留空）"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 危险操作确认框：默认焦点在「取消」，连带后果逐条列出 -->
    <ConfirmDialog
      v-model="confirmVisible"
      title="确认删除"
      :message="deleteMessage"
      :warnings="deleteWarnings"
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

/* 禁用的按钮不接收鼠标事件，tooltip 会失效。
   套一层 span 把事件接住，这样「为什么不能点」才提示得出来 */
.btn-wrap {
  display: inline-flex;
}

.search {
  width: 260px;
}

.load-error {
  margin-bottom: 12px;
}

.retry {
  margin-top: 8px;
}

.customer-table {
  flex: 1;
  min-height: 0;
}

.customer-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.phone-input {
  width: 220px;
}
</style>
