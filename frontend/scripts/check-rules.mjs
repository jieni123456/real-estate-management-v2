/**
 * 纯逻辑自检。对应需求报告 R-004 阶段 2。
 *
 * 前端这一层不引测试框架（Vitest 会带进一整套依赖），但「筛选规则」和
 * 「数值格式化」这两块是纯函数，值得单独验证 —— 它们的错误不会让页面白屏，
 * 只会让用户搜不到东西、或者看到被四舍五入过的面积，属于最难发现的那一类。
 *
 * 用法：npm run check
 *
 * 断言清单刻意与桌面版的 test/DisplayTest.java 对齐（同一套规则，两端各验一遍）。
 */

import { isBlank, matches } from '../src/utils/search.js'
import { formatArea, nowDateTimeText } from '../src/utils/format.js'
import { parseFileName } from '../src/utils/download.js'

let passed = 0
const failures = []

function check(label, actual, expected) {
  if (Object.is(actual, expected)) {
    passed++
  } else {
    failures.push(`${label}\n    期望: ${expected}\n    实际: ${actual}`)
  }
}

console.log('\n=== 关键字匹配（与 core 的 SearchMatcher 同语义）===')

check('空关键字一律匹配', matches('', '任意内容'), true)
check('null 关键字一律匹配', matches(null, '任意内容'), true)
check('全空白关键字一律匹配', matches('   ', '任意内容'), true)
check('isBlank 认识空白串', isBlank('\t '), true)
check('isBlank 不误判正常内容', isBlank('房源'), false)

check('单关键字命中 ID', matches('1010', '1010', '两居', '中山路'), true)
check('包含而非相等', matches('101', '1010', '两居', '中山路'), true)
check('命中地址中段', matches('中山', '1010', '两居', '中山路'), true)
check('大小写不敏感', matches('abc', '1010', 'ABC户型'), true)
check('未命中返回 false', matches('不存在的词', '1010', '两居'), false)

check('多关键字 AND —— 全部命中', matches('1010 两居', '1010', '两居', '中山路'), true)
check('多关键字 AND —— 只中一个则失败', matches('1010 别墅', '1010', '两居', '中山路'), false)
check('多关键字跨字段命中', matches('两居 中山', '1010', '两居', '中山路'), true)
check('多空白分隔不产生空关键字', matches('1010   两居', '1010', '两居'), true)

check('字段为 null 时不匹配非空关键字', matches('x', null, null), false)
check('字段为 null 且关键字为空则匹配', matches('', null, null), true)

console.log('=== 面积格式化（与 core 的 Formats.area 同语义）===')

check('整数去掉 .0', formatArea(128.0), '128')
check('一位小数保留', formatArea(89.5), '89.5')
check('两位小数不做四舍五入', formatArea(89.25), '89.25')
check('零', formatArea(0), '0')
check('非数字回退为破折号', formatArea('不是数字'), '—')
check('null 回退为破折号', formatArea(null), '—')

console.log('=== 下载文件名解析（Content-Disposition）===')

const BOTH =
  'attachment; filename="houses_20260919.csv"; ' +
  "filename*=UTF-8''%E6%88%BF%E5%B1%8B%E5%88%97%E8%A1%A8_20260919.csv"
check('优先取 RFC 5987 的中文名', parseFileName(BOTH), '房屋列表_20260919.csv')

check(
  '只有 ASCII 名时用它',
  parseFileName('attachment; filename="houses_20260919.csv"'),
  'houses_20260919.csv'
)
check('裸文件名（无引号）也认', parseFileName('attachment; filename=houses.csv'), 'houses.csv')
check('null 返回空串', parseFileName(null), '')
check('空串返回空串', parseFileName(''), '')

// 中文字符编码坏掉时，不能把 `*=UTF-8''…` 这段当文件名返回，要退回 ASCII 名
check(
  '编码损坏时退回 ASCII 名',
  parseFileName('attachment; filename*=UTF-8\'\'%ZZbad; filename="fallback.csv"'),
  'fallback.csv'
)
check('只有损坏的 filename* 时返回空串', parseFileName("attachment; filename*=UTF-8''%ZZ"), '')

/* ---- nowDateTimeText：格式必须与 core 的 DATE_TIME_SECONDS_PATTERN 一致 ----
 * 这一个最容易写错的不是「对不对」，而是「后端的解析器认不认」：
 * 格式只要有半点偏差（少个零、用 T 分隔），接口层就会回「格式不正确」。 */
check('个位数补零到两位', nowDateTimeText(new Date(2026, 0, 5, 3, 7, 9)), '2026-01-05 03:07:09')
check('月末年末不出错', nowDateTimeText(new Date(2026, 11, 31, 23, 59, 59)), '2026-12-31 23:59:59')
check(
  '整体形状与后端约定的模式一致（yyyy-MM-dd HH:mm:ss）',
  /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(nowDateTimeText()),
  true
)

console.log(`\n通过 ${passed} 项，失败 ${failures.length} 项`)

if (failures.length > 0) {
  console.log('\n失败明细：')
  failures.forEach((f) => console.log('  · ' + f))
  process.exit(1)
}

console.log('全部通过\n')
