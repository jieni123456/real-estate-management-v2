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

  /* 释放要**延后**，不能紧跟 click 之后。
   *
   * 下载是异步开始的：click 返回时浏览器只是"记下要做这件事"，真正读取 Blob 可能在
   * 之后的某个 tick。规范与 MDN 都建议等下载开始后再释放对象 URL，立刻 revoke 属于
   * 潜在的竞态（Blob URL 提前失效 → 文件落不下来）。
   *
   * 注：阶段 4 排错时曾怀疑这里是「导出提示成功但文件没出现」的原因，最终查明根因
   * 在测试侧（CDP 的下载路径格式，见需求报告 5.15）。此处保留改动，因为它本来就
   * 更稳妥，代价也可以忽略——几十 KB 的内存多留一秒而已。
   */
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
