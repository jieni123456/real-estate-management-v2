<script setup>
/**
 * 根组件。只做一件事：把路由匹配到的页面渲染出来。
 *
 * 登录页与主框架是两个不同的布局，它们的切换由路由表负责
 * ——所以这里不需要任何条件判断。
 */
</script>

<template>
  <router-view />
</template>
