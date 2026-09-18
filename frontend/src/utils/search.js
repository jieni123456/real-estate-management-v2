/**
 * 关键字匹配。对应需求报告 G-004，与桌面版的
 * realestate-core/src/main/java/util/SearchMatcher.java 保持**同一套语义**：
 *
 *   · 关键字为空（或全空白）→ 一律视为匹配，即「没有筛选条件」
 *   · 多个关键字用空白分隔，**全部命中才算匹配**（AND 语义）
 *   · 大小写不敏感
 *   · 「命中」是包含，不是相等 —— 搜 1010 能命中 1010 与 10101
 *
 * 刻意写成不依赖 Vue、不依赖浏览器 API 的纯函数：这样它可以脱离页面单独验证
 * （见 scripts/check-rules.mjs），两端的筛选规则也不会各漂各的。
 */

/** 是否处于「未筛选」状态，供界面判断要不要显示条数提示 */
export function isBlank(keyword) {
  return keyword == null || String(keyword).trim() === ''
}

export function matches(keyword, ...fields) {
  if (isBlank(keyword)) {
    return true
  }

  const terms = String(keyword).trim().toLowerCase().split(/\s+/)

  // every：每个关键字都要被命中；some：任意一个字段命中即可
  return terms.every((term) =>
    fields.some((field) => field != null && String(field).toLowerCase().includes(term))
  )
}
