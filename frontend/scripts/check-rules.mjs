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
import { formatArea } from '../src/utils/format.js'

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

console.log(`\n通过 ${passed} 项，失败 ${failures.length} 项`)

if (failures.length > 0) {
  console.log('\n失败明细：')
  failures.forEach((f) => console.log('  · ' + f))
  process.exit(1)
}

console.log('全部通过\n')
