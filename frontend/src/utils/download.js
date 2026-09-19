/**
 * 文件下载。对应需求报告 R-004 阶段 3（CSV 导出）。
 *
 * 为什么不用 `window.open(url)`：导出接口要带 token，而 `window.open` 不会带上
 * Authorization 头（token 存在 localStorage 里，不是 Cookie）。所以只能走 axios 取回
 * Blob，再用一个临时链接触发下载。
 */

/**
 * 把 Content-Disposition 里的文件名解析出来。
 *
 * 后端同时给了两个名字：`filename="houses_20260919.csv"`（ASCII 兜底）与
 * `filename*=UTF-8''…`（RFC 5987，中文名走这个）。优先取后者，取不到再退回前者。
 *
 * @returns {string} 解析不出时返回空串，由调用方决定兜底名字
 */
export function parseFileName(disposition) {
  if (!disposition) {
    return ''
  }

  const utf8 = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)
  if (utf8) {
    try {
      return decodeURIComponent(utf8[1].trim())
    } catch {
      // 编码坏了就退回 ASCII 名，不要让下载整个失败
    }
  }

  /* 注意 (?!\*)：若不排除，`filename*=UTF-8''xxx` 里的 `filename` 会先被当成
   * 普通名字抓出来，结果拿到一段 `*=UTF-8''…` 这种东西。
   * 两种写法都认：带引号的 `filename="a.csv"` 与裸的 `filename=a.csv`。 */
  const plain = /filename(?!\*)\s*=\s*("([^"]*)"|([^;]*))/i.exec(disposition)
  if (plain) {
    return (plain[2] ?? plain[3] ?? '').trim()
  }
  return ''
}

/** 用 Blob 触发一次浏览器下载 */
export function downloadBlob(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || 'download'
  // 必须挂进文档再点：Firefox 对游离节点的 click() 不触发下载
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
