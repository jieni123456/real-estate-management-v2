/**
 * 权限点与角色的前端侧定义。对应需求报告 R-001 与 G-024。
 *
 * 这些字符串必须与 realestate-core/src/main/java/util/Permissions.java 一致 ——
 * 但请记住：**前端拿权限只决定「显示什么」，真正的拦截在后端**
 * （core 的 controller 里那道 Session.can(...)）。
 * 界面上藏起一个按钮从来不算安全措施，这条规矩从 R-001 起就没变过。
 */

export const PERMISSION = {
  HOUSE_VIEW: 'house:view',
  HOUSE_ADD: 'house:add',
  HOUSE_DELETE: 'house:delete',
  CUSTOMER_VIEW: 'customer:view',
  CUSTOMER_ADD: 'customer:add',
  CUSTOMER_DELETE: 'customer:delete',
  VIEWING_VIEW: 'viewing:view',
  VIEWING_ADD: 'viewing:add',
  VIEWING_DELETE: 'viewing:delete'
}

export const ROLE = {
  ADMIN: 'ADMIN',
  AGENT: 'AGENT'
}

/** 房屋状态。与 core 的 model/House.java 保持一致 */
export const HOUSE_STATUS = {
  VACANT: '空置',
  RENTED: '已租出'
}

export const HOUSE_STATUS_OPTIONS = [
  { label: '全部状态', value: '' },
  { label: HOUSE_STATUS.VACANT, value: HOUSE_STATUS.VACANT },
  { label: HOUSE_STATUS.RENTED, value: HOUSE_STATUS.RENTED }
]

/**
 * 表单里可选的状态（不含筛选用的那个空选项）。
 *
 * 取值必须与 core 的 model/House.java 里 STATUSES 完全一致——后端有
 * isValidStatus 再校验一次，前端这里只是把可选项摆出来。
 */
export const HOUSE_STATUS_CHOICES = [
  { label: HOUSE_STATUS.VACANT, value: HOUSE_STATUS.VACANT },
  { label: HOUSE_STATUS.RENTED, value: HOUSE_STATUS.RENTED }
]

/** 房东下拉里表示「不是选已有房东，而是录入一个新的」的哨兵值 */
export const NEW_LANDLORD = '__new_landlord__'
