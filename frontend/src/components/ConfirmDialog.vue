<script setup>
import { nextTick, ref, watch } from 'vue'

/**
 * 危险操作确认框。对应需求报告 R-002 第 9 轮结论。
 *
 * 为什么不用 `ElMessageBox.confirm`：那条路把焦点行为交给了组件内部，而 R-002 定下的
 * 规矩是**默认焦点必须在「取消」上** —— 删除不可逆，用户依习惯敲回车不该把数据删掉。
 * 自己写一个（不到 80 行）才能把这件事钉死，也才能在确认框里摆下「连带后果」清单
 * （级联删除的带看记录、会被一并清理的房东 —— G-008 / G-018 要求这两件事不能是隐形的）。
 *
 * 复用点：阶段 4 的客户与带看记录删除可以直接用它。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '请确认' },
  message: { type: String, default: '' },
  /** 逐条的后果说明，例如「该房屋有 2 条带看记录，将一并删除」 */
  warnings: { type: Array, default: () => [] },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  /** 确认按钮是否用危险色（删除类操作为真） */
  danger: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const cancelRef = ref(null)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      return
    }
    await nextTick()
    // 弹窗有出场动画，等一帧再聚焦，避免按钮还没进入可聚焦状态
    requestAnimationFrame(focusCancel)
  }
)

/** el-button 暴露了 focus()，但拿不到时退回到它的根元素，保证不会静默失败 */
function focusCancel() {
  const instance = cancelRef.value
  if (!instance) {
    return
  }
  const element = instance.$el ?? instance
  if (typeof element.focus === 'function') {
    element.focus()
  }
}

function close() {
  if (props.loading) {
    return
  }
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="460px"
    align-center
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    class="confirm-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="confirm-message">{{ message }}</p>

    <ul v-if="warnings.length" class="confirm-warnings">
      <li v-for="(item, index) in warnings" :key="index">{{ item }}</li>
    </ul>

    <template #footer>
      <!-- 默认焦点：见上方 watch。放在前面也让 Tab 顺序自然 -->
      <el-button ref="cancelRef" :disabled="loading" @click="close">
        {{ cancelText }}
      </el-button>
      <el-button
        :type="danger ? 'danger' : 'primary'"
        :loading="loading"
        @click="emit('confirm')"
      >
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.confirm-message {
  margin: 0;
  font-size: var(--font-body);
  line-height: 1.7;
  color: var(--text-primary);
}

/* 连带后果用一块浅红底单独框出来：这是「点之前必须看到」的信息，
   不能和正文混在一起被扫过去 */
.confirm-warnings {
  margin: 12px 0 0;
  padding: 10px 12px 10px 28px;
  background: color-mix(in srgb, var(--danger) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--danger) 35%, transparent);
  border-radius: var(--radius-sm);
  font-size: var(--font-caption);
  line-height: 1.8;
  color: var(--danger);
}
</style>
