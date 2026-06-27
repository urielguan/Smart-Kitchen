<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import Sidebar from './Sidebar.vue'
import Header from './Header.vue'

const router = useRouter()
const userStore = useUserStore()

// 获取用户信息（从后端获取）
onMounted(async () => {
  // 如果已有 token 但没有用户信息，从后端获取
  if (userStore.token && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch (error) {
      console.error('获取用户信息失败:', error)
      // token过期/刷新失败：axios拦截器已处理跳转，此处不再clearAuth
      // 其他错误（如网络问题）：保留token，用户可手动刷新页面重试
    }
  }
})
</script>

<template>
  <div class="app-layout">
    <!-- 左侧导航栏 -->
    <Sidebar />

    <!-- 右侧内容区 -->
    <div class="main-container">
      <!-- 顶部栏 -->
      <Header />

      <!-- 内容区 -->
      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.app-layout {
  display: flex;
  height: 100vh;
  width: 100%;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: $bg-base;
}

.content {
  flex: 1;
  height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 16px;
  display: flex;
  flex-direction: column;
}
</style>
