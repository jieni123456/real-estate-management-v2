<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useSessionStore } from '@/store/session'

/**
 * 登录页。对应需求报告 R-002 第 4 轮结论（左右分栏）。
 *
 * 左侧是主色品牌区，放系统名与三条功能说明；右侧是登录表单。
 * 之所以分栏而不是居中小卡片：这是个有九字名称的系统，单卡片方案里
 * 它只能当个标题挤在中间，分栏才装得下信息层次。
 *
 * 注意：页面上**不出现任何预设账号口令**。本项目的规矩是「可读文本里不出现口令」
 * （源码会进版本库、也会随页面发到浏览器），所以只提示「使用系统分配的账号登录」。
 */
const route = useRoute()
const router = useRouter()
const session = useSessionStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorText = ref('')

const features = ['房源管理', '客户管理', '一站完成']

/** 只接受站内的跳转地址，避免被 ?redirect=//evil.com 之类利用 */
function safeRedirect() {
  const target = route.query.redirect
  if (typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')) {
    return target
  }
  return { name: 'houses' }
}

async function submit() {
  errorText.value = ''

  const username = form.username.trim()
  const password = form.password

  // 前端这层判空只是省一次往返；后端同样会校验，两边的提示文案保持一致
  if (!username || !password) {
    errorText.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    await session.login(username, password)
    ElMessage.success('登录成功')
    router.replace(safeRedirect())
  } catch (error) {
    // 失败原因直接来自后端的 message（用户名或密码错误 / 数据库连不上…），
    // 不再笼统地说「登录失败」——否则用户不知道是打错了还是服务没起来
    errorText.value = error.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 左：品牌区 -->
      <section class="panel-brand">
        <h1 class="brand-title">二手房中介管理系统</h1>
        <ul class="brand-features">
          <li v-for="item in features" :key="item">{{ item }}</li>
        </ul>
      </section>

      <!-- 右：登录表单 -->
      <section class="panel-form">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-subtitle">请使用系统分配的账号登录</p>

        <el-alert
          v-if="errorText"
          class="form-error"
          :title="errorText"
          type="error"
          show-icon
          :closable="false"
        />

        <el-form label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名">
            <el-input
              v-model="form.username"
              size="large"
              placeholder="请输入用户名"
              :prefix-icon="User"
              autocomplete="username"
              @keyup.enter="submit"
            />
          </el-form-item>

          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>

          <el-button
            class="submit-btn"
            type="primary"
            size="large"
            :loading="loading"
            @click="submit"
          >
            登 录
          </el-button>
        </el-form>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 24px;
  background: var(--page-bg);
}

.login-card {
  display: grid;
  grid-template-columns: 45% 55%;
  width: 100%;
  max-width: 760px;
  min-height: 460px;
  background: var(--surface);
  border-radius: var(--radius);
  box-shadow: var(--shadow-float);
  overflow: hidden;
}

/* -------------------------------------------------------- 左侧品牌区 */
.panel-brand {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
  padding: 40px 32px;
  background: var(--accent);
  color: var(--text-on-accent);
}

.brand-title {
  margin: 0;
  font-size: var(--font-brand);
  font-weight: var(--weight-bold);
  line-height: 1.4;
  letter-spacing: 1px;
}

.brand-features {
  margin: 0;
  padding: 0;
  list-style: none;
}

.brand-features li {
  position: relative;
  padding-left: 16px;
  margin-bottom: 10px;
  font-size: var(--font-body);
  /* 用提亮色而不是纯白，和标题拉开层次 */
  color: var(--accent-light);
}

.brand-features li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent-light);
}

/* -------------------------------------------------------- 右侧表单区 */
.panel-form {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 40px 44px;
}

.form-title {
  margin: 0 0 6px;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-heading);
}

.form-subtitle {
  margin: 0 0 20px;
  font-size: var(--font-caption);
  color: var(--text-secondary);
}

.form-error {
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 4px;
}

/* 窄屏时改为上下排列，否则两栏会被挤扁 */
@media (max-width: 640px) {
  .login-card {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .panel-brand {
    padding: 24px;
  }

  .panel-form {
    padding: 24px;
  }
}
</style>
