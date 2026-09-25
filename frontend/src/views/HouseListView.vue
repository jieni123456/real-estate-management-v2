<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  createHouse,
  deleteHouse,
  exportHouses,
  fetchDeletionInfo,
  importHouses,
  listHouses,
  listLandlords,
  updateHouse
} from '@/api/house'
import { downloadBlob } from '@/utils/download'
import { formatArea } from '@/utils/format'
import { matches } from '@/utils/search'
import {
  HOUSE_STATUS,
  HOUSE_STATUS_CHOICES,
  HOUSE_STATUS_OPTIONS,
  NEW_LANDLORD,
  PERMISSION
} from '@/utils/permissions'
import { useSessionStore } from '@/store/session'
import { useUiStore } from '@/store/ui'

/**
 * 房屋管理。对应需求报告 R-004 阶段 2（列表）与阶段 3（增 / 改 / 删 / 导出）。
 *
 * 筛选放在前端做：本系统的数据量是「一个中介门店」级别，全量取回后在内存里过滤，
 * 打字零延迟。规则由 utils/search.js 提供，与 core 的 SearchMatcher 是同一套语义
 * ——导出时后端会用 core 的那一份重算，两边必须一致，这一点由 HouseQueryTest 钉住。
 *
 * 权限的两层结构（R-001）：这里按权限决定按钮**禁用与否**，真正的拦截在后端
 * （core 的 HouseController + 接口层的 requireView）。界面禁用只是体验。
 */
const session = useSessionStore()
const ui = useUiStore()

// ---------------------------------------------------------------- 列表状态

const houses = ref([])
const loading = ref(false)
/** 非空表示这次加载失败了。与「查到了 0 条」严格区分 —— 后者是正常结果 */
const loadError = ref('')

const keyword = ref('')
const statusFilter = ref('')

const tableRef = ref(null)
/** 当前选中行。编辑与删除都作用在它身上 */
const currentRow = ref(null)

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

async function load({ keepSelection = true } = {}) {
  loading.value = true
  loadError.value = ''
  const keepId = keepSelection ? currentRow.value?.id : null

  try {
    houses.value = await listHouses()
    reportStatus()
  } catch (error) {
    // 读失败时清空列表：留着上一次的数据会让人以为是最新的
    houses.value = []
    loadError.value = error.message
    ui.setStatus('房屋数据读取失败')
    currentRow.value = null
    return
  } finally {
    loading.value = false
  }

  // 刷新会换掉整批对象，之前选中的那一行要按 ID 重新找回，
  // 并用 setCurrentRow 让表格的高亮跟着走（否则视觉上还停在旧位置）
  await nextTick()
  selectRow(keepId ? houses.value.find((house) => house.id === keepId) ?? null : null)
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

function selectRow(row) {
  currentRow.value = row
  tableRef.value?.setCurrentRow(row)
}

onMounted(load)

// ---------------------------------------------------------------- 权限

const canAdd = computed(() => session.hasPermission(PERMISSION.HOUSE_ADD))
const canDelete = computed(() => session.hasPermission(PERMISSION.HOUSE_DELETE))

/** 编辑沿用「写」权限：两个角色都有。见需求报告 R-001 的权限矩阵 */
const editDisabled = computed(() => !canAdd.value || !currentRow.value)
const deleteDisabled = computed(() => !canDelete.value || !currentRow.value)

/** 按钮为何不可点——禁用而不说原因，用户只会以为界面坏了 */
const editHint = computed(() => {
  if (!canAdd.value) {
    return '当前角色没有修改房屋数据的权限'
  }
  return currentRow.value ? '' : '请先在列表中选中一行'
})

const deleteHint = computed(() => {
  if (!canDelete.value) {
    return '需要管理员权限（当前角色不能删除房屋）'
  }
  return currentRow.value ? '' : '请先在列表中选中一行'
})

// ---------------------------------------------------------------- 新增 / 编辑

const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const formRef = ref(null)
const landlords = ref([])

const form = reactive({
  id: '',
  type: '',
  area: null,
  address: '',
  status: HOUSE_STATUS.VACANT,
  landlordChoice: '',
  landlordId: '',
  landlordName: '',
  landlordContact: ''
})

const NEW_LANDLORD_LABEL = '＋ 新建房东'

/** 选了已有房东时，三个字段随之填充并锁住——避免把共有房东的资料改掉 */
const landlordReadonly = computed(() => form.landlordChoice !== NEW_LANDLORD)

/* 客户端只做「必填 / 范围」这类即时反馈，**规则以后端为准**：
 * 长度、电话格式、ID 是否已被占用、状态取值是否合法，全部由 core 的
 * Validators + HouseController 判定，失败原因会原样回显给用户。
 * 这样校验规则只有一份实现，不会出现「界面放过了、后端拒绝」的困惑。 */
const rules = {
  id: [{ required: true, message: '请填写房屋ID', trigger: 'blur' }],
  type: [{ required: true, message: '请填写户型', trigger: 'blur' }],
  address: [{ required: true, message: '请填写地址', trigger: 'blur' }],
  area: [
    {
      validator: (rule, value, callback) => {
        if (value === null || value === undefined || value === '') {
          return callback(new Error('请填写面积'))
        }
        if (!(Number(value) > 0)) {
          return callback(new Error('面积必须大于 0'))
        }
        return callback()
      },
      trigger: 'blur'
    }
  ],
  landlordChoice: [{ required: true, message: '请选择房东', trigger: 'change' }],
  landlordId: [{ required: true, message: '请填写房东ID', trigger: 'blur' }],
  landlordName: [{ required: true, message: '请填写房东姓名', trigger: 'blur' }],
  landlordContact: [{ required: true, message: '请填写房东电话', trigger: 'blur' }]
}

async function openCreate() {
  editing.value = false
  resetForm()
  await ensureLandlords()
  dialogVisible.value = true
}

async function openEdit() {
  const house = currentRow.value
  if (!house) {
    return
  }

  editing.value = true
  resetForm()
  form.id = house.id
  form.type = house.type
  form.area = house.area
  form.address = house.address
  form.status = house.status
  form.landlordId = house.landlordId
  form.landlordName = house.landlordName
  form.landlordContact = house.landlordContact
  // 该房屋的房东必然存在于库里，因此默认就能在下拉里选中它
  form.landlordChoice = house.landlordId

  await ensureLandlords()
  dialogVisible.value = true
}

function resetForm() {
  form.id = ''
  form.type = ''
  form.area = null
  form.address = ''
  form.status = HOUSE_STATUS.VACANT
  form.landlordChoice = ''
  form.landlordId = ''
  form.landlordName = ''
  form.landlordContact = ''
  formRef.value?.clearValidate()
}

/** 房东列表只取一次，之后复用（下拉的选择与显示都靠它） */
async function ensureLandlords() {
  if (landlords.value.length > 0) {
    return
  }
  try {
    landlords.value = await listLandlords()
  } catch (error) {
    // 取不到房东列表不该挡住整个对话框：选「新建房东」照样能录入
    landlords.value = []
    ElMessage.warning(`房东列表读取失败：${error.message}`)
  }
}

function onLandlordChange(value) {
  if (value === NEW_LANDLORD) {
    form.landlordId = ''
    form.landlordName = ''
    form.landlordContact = ''
    return
  }
  const picked = landlords.value.find((landlord) => landlord.id === value)
  if (picked) {
    form.landlordId = picked.id
    form.landlordName = picked.name
    form.landlordContact = picked.contact
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    const result = editing.value
      ? await updateHouse(form.id, payload)
      : await createHouse(payload)

    const savedId = payload.id
    dialogVisible.value = false
    // 服务端的说明比本地拼的更有信息量（例如「房东已存在，沿用其原有信息」）
    ElMessage.success(result.message || (editing.value ? '房屋已更新' : '房屋添加成功'))
    await load()
    if (!editing.value) {
      await nextTick()
      selectRow(houses.value.find((house) => house.id === savedId) ?? null)
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

function buildPayload() {
  return {
    id: form.id.trim(),
    type: form.type.trim(),
    area: form.area === null || form.area === '' ? null : Number(form.area),
    address: form.address.trim(),
    status: form.status,
    landlordId: form.landlordId.trim(),
    landlordName: form.landlordName.trim(),
    landlordContact: form.landlordContact.trim()
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
    ? `确定要删除房屋「${target.id}」（${target.address}）吗？此操作不可撤销。`
    : ''
})

/** 连带后果逐条列出。级联删除不能是隐形的（G-008 / G-018） */
const deleteWarnings = computed(() => {
  const info = deletionInfo.value
  if (!info) {
    return []
  }
  const list = []
  if (info.viewingCount > 0) {
    list.push(`该房屋有 ${info.viewingCount} 条带看记录，将一并删除。`)
  }
  if (info.landlordWillBeRemoved) {
    list.push(
      `房东「${info.landlordId} · ${info.landlordName}」名下只有这一套房屋，` +
        '删除后其房东记录也会被一并清理。'
    )
  }
  return list
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
    const result = await deleteHouse(target.id)
    confirmVisible.value = false
    ElMessage.success(result.message || '房屋删除成功')
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
    // 把当前筛选条件一并交给后端，由它按同一套规则重算——
    // 这样「导出的就是屏幕上看到的」，而不是靠前端把行数据传回去
    const { blob, fileName } = await exportHouses({
      keyword: keyword.value,
      status: statusFilter.value
    })
    downloadBlob(blob, fileName || '房屋列表.csv')
    ElMessage.success(`已导出 ${visibleHouses.value.length} 条记录`)
  } catch (error) {
    await ElMessageBox.alert(error.message, '导出失败', {
      type: 'error',
      confirmButtonText: '知道了'
    })
  } finally {
    exporting.value = false
  }
}

// ---------------------------------------------------------------- 导入

const canImport = computed(() => session.hasPermission(PERMISSION.HOUSE_IMPORT))
const importing = ref(false)
const importInput = ref(null)

/** 导入报告。非空即弹出对话框把逐条失败原因摆出来 */
const importReport = ref(null)
const importDialogVisible = ref(false)

/** 按钮为何不可点。禁用而不说原因，用户只会以为界面坏了 */
const importHint = computed(() =>
  canImport.value ? '' : '需要管理员权限（批量导入会一次写入大量数据）'
)

/** 打开文件选择框。先清空 value，否则连着选同一个文件不会触发 change */
function pickImportFile() {
  if (!importInput.value) {
    return
  }
  importInput.value.value = ''
  importInput.value.click()
}

async function onImportFile(event) {
  const file = event.target.files?.[0]
  if (!file) {
    return
  }

  /* 扩展名只是提前拦一道，真正的判据是表头，那在服务端 ——
     拿客户表、带看表来导，会在写库之前被整批拒绝并说明原因。 */
  if (!file.name.toLowerCase().endsWith('.csv')) {
    await ElMessageBox.alert(
      '只能导入 CSV 文件。可以先用「导出 CSV」得到一份，在 Excel 里编辑后另存为 CSV 再导入。',
      '文件格式不对',
      { type: 'warning', confirmButtonText: '知道了' }
    )
    return
  }

  importing.value = true
  try {
    const { report } = await importHouses(file)
    importReport.value = report
    importDialogVisible.value = true
    // 报告只说「写成功了多少条」，列表得重新拉一次才与它相符
    await load({ keepSelection: false })
  } catch (error) {
    await ElMessageBox.alert(error.message, '导入失败', {
      type: 'error',
      confirmButtonText: '知道了'
    })
  } finally {
    importing.value = false
  }
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h2 class="page-title">房屋管理</h2>
    </header>

    <div class="card">
      <div class="toolbar">
        <div class="toolbar-actions">
          <el-tooltip content="新增一套房屋" placement="top" :disabled="canAdd">
            <span class="btn-wrap">
              <el-button type="primary" :disabled="!canAdd" @click="openCreate">
                添加房屋
              </el-button>
            </span>
          </el-tooltip>

          <el-tooltip :content="editHint" placement="top" :disabled="!editHint">
            <span class="btn-wrap">
              <el-button :disabled="editDisabled" @click="openEdit">编辑房屋</el-button>
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
                删除房屋
              </el-button>
            </span>
          </el-tooltip>

          <el-button plain :loading="exporting" @click="onExport">导出 CSV</el-button>

          <!-- R-006：与导出并排 —— 两者是对称的一对操作，导出的文件应当能原样导回来 -->
          <el-tooltip :content="importHint" placement="top" :disabled="!importHint">
            <span class="btn-wrap">
              <el-button
                plain
                :disabled="!canImport"
                :loading="importing"
                @click="pickImportFile"
              >
                导入 CSV
              </el-button>
            </span>
          </el-tooltip>
          <input
            ref="importInput"
            class="file-input"
            type="file"
            accept=".csv,text/csv"
            @change="onImportFile"
          />

          <el-button :icon="Refresh" :loading="loading" @click="load()">刷新数据</el-button>
        </div>

        <div class="toolbar-filters">
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
        class="house-table"
        :data="visibleHouses"
        :row-style="{ height: '38px' }"
        highlight-current-row
        border
        @current-change="currentRow = $event"
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

    <!-- 添加 / 编辑共用同一个对话框（R-002 第 7 轮：分组表单） -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑房屋' : '添加房屋'"
      width="620px"
      align-center
      :close-on-click-modal="false"
      class="house-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        class="house-form"
      >
        <div class="group-title">房屋信息</div>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="房屋ID" prop="id">
              <el-input
                v-model="form.id"
                :disabled="editing"
                placeholder="唯一标识，编辑时不可改"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="户型" prop="type">
              <el-input v-model="form.type" placeholder="如 两居 / 三居" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="面积(m²)" prop="area">
              <el-input-number
                v-model="form.area"
                class="full-width"
                :min="0"
                :controls="false"
                placeholder="如 88"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="地址" prop="address">
              <el-input v-model="form.address" placeholder="如 阳光路 8 号" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 状态是下拉，与网格里的文本框对不齐，因此独占一行 -->
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" class="status-pick">
            <el-option
              v-for="choice in HOUSE_STATUS_CHOICES"
              :key="choice.value"
              :label="choice.label"
              :value="choice.value"
            />
          </el-select>
        </el-form-item>

        <div class="group-title">房东信息</div>

        <el-form-item label="房东" prop="landlordChoice">
          <el-select
            v-model="form.landlordChoice"
            class="full-width"
            placeholder="从已有房东中选择，或选「新建房东」"
            @change="onLandlordChange"
          >
            <el-option :value="NEW_LANDLORD" :label="NEW_LANDLORD_LABEL" />
            <el-option
              v-for="landlord in landlords"
              :key="landlord.id"
              :value="landlord.id"
              :label="`${landlord.id} · ${landlord.name}`"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="房东ID" prop="landlordId">
              <el-input v-model="form.landlordId" :disabled="landlordReadonly" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="landlordName">
              <el-input v-model="form.landlordName" :disabled="landlordReadonly" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="电话" prop="landlordContact">
          <el-input v-model="form.landlordContact" :disabled="landlordReadonly" />
        </el-form-item>

        <p v-if="landlordReadonly && form.landlordChoice" class="form-hint">
          已选用已有房东，本次保存不会覆盖其原有资料。
        </p>
      </el-form>

      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果。行号是 CSV 文件里的行号，用户能直接回到文件定位 -->
    <el-dialog v-model="importDialogVisible" title="导入结果" width="680px">
      <template v-if="importReport">
        <p class="import-summary">
          共 {{ importReport.total }} 条，成功
          <span class="import-ok">{{ importReport.success }}</span>
          条，失败
          <span :class="importReport.failureCount ? 'import-bad' : 'import-ok'">
            {{ importReport.failureCount }}
          </span>
          条。
        </p>

        <p v-if="importReport.failureCount === 0" class="form-hint">
          全部导入成功，列表已刷新。
        </p>

        <template v-else>
          <p class="form-hint">
            下面这些行没有写进数据库，其余 {{ importReport.success }} 条已经正常入库。
            行号对应 CSV 文件里的行号（第 1 行是表头）。
          </p>
          <el-table :data="importReport.failures" max-height="320" size="small" border>
            <el-table-column prop="line" label="行号" width="76" align="center" />
            <el-table-column label="房屋ID" width="120">
              <template #default="{ row }">
                {{ row.houseId || '（空）' }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="未能导入的原因" min-width="360" />
          </el-table>
        </template>
      </template>

      <template #footer>
        <el-button type="primary" @click="importDialogVisible = false">知道了</el-button>
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
  width: 240px;
}

.status-select {
  width: 128px;
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

.group-title:not(:first-child) {
  margin-top: 8px;
}

.house-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.full-width {
  width: 100%;
}

.status-pick {
  width: 160px;
}

.form-hint {
  margin: 0 0 4px;
  font-size: var(--font-caption);
  color: var(--text-secondary);
}

/* ---- 导入 ---- */

/* 原生文件选择框只作为「被点」的载体，界面上不出现 */
.file-input {
  display: none;
}

.import-summary {
  margin: 0 0 8px;
  font-size: var(--font-body);
  color: var(--text-heading);
}

.import-ok {
  font-weight: var(--weight-bold);
  color: var(--success);
}

.import-bad {
  font-weight: var(--weight-bold);
  color: var(--danger);
}
</style>
