/**
 * 展示用的格式化。与桌面版 realestate-core/src/main/java/util/Formats.java 对应。
 */

/**
 * 面积：128.0 显示为 128，89.5 保持 89.5。
 *
 * 刻意**不做四舍五入** —— 表格里显示的数值将来会被「编辑」对话框原样回填，
 * 若这里把 89.25 显示成 89.3，用户保存后就会把库里的值改成 89.3，属于静默的数据变更。
 *
 * JS 的 number 本身没有 ".0" 这种表示（128.0 就是 128），所以 String(num)
 * 天然满足「去掉多余 .0」这一条，不需要额外判断。
 *
 * 为什么开头要单独挡 null 与空串：JS 的 Number(null) 与 Number('') 都是 0，
 * 不挡住的话「没有值」会被静默显示成「0 m²」——把缺失当成零，属于会误导人的错误
 * （Java 那边形参是 double，不存在这个问题；这是语言差异带来的坑）。
 */
export function formatArea(value) {
  if (value == null || (typeof value === 'string' && value.trim() === '')) {
    return '—'
  }

  const num = Number(value)
  if (!Number.isFinite(num)) {
    return '—'
  }
  return String(num)
}

/**
 * 当前时间，格式与 core 的 `Formats.DATE_TIME_SECONDS_PATTERN` 完全一致
 * （`yyyy-MM-dd HH:mm:ss`）。
 *
 * 用在「登记带看」对话框的默认值上：把带看时间预填成「现在」，用户只在实际补录时
 * 才需要改。格式必须与后端收发的模式一致，否则会被接口层判为「格式不正确」。
 *
 * 为什么手写而不引 dayjs：项目里只有这一处需要格式化时间，为它引入一个日期库不划算。
 * dayjs 虽然随 element-plus 一起装进了 node_modules，但**直接依赖传递依赖是隐患**
 * ——上游哪天换了实现，这里就会被牵连。
 *
 * 参数故意留出 `date` 而不是只读 `new Date()`：这样它是个纯函数，能直接断言
 * （见 scripts/check-rules.mjs）。
 */
export function nowDateTimeText(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}
