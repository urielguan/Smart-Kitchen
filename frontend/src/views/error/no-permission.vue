<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'

const router = useRouter()
const userStore = useUserStore()

const firstRoute = computed(() => userStore.getFirstAccessibleRoute())

const goFirstRoute = async () => {
  if (firstRoute.value) {
    await router.replace(firstRoute.value)
    return
  }
  await router.replace('/login')
}

const goLogin = async () => {
  userStore.clearAuth()
  await router.replace('/login')
}
</script>

<template>
  <div class="no-permission-page">
    <div class="no-permission-card">
      <div class="status-code">403</div>
      <h1>当前账号无可访问页面</h1>
      <p>菜单点击无响应，通常是因为当前跳转目标没有菜单权限，或权限回跳落到了未定义页面。</p>
      <div class="actions">
        <el-button type="primary" @click="goFirstRoute">
          返回可访问页面
        </el-button>
        <el-button @click="goLogin">
          重新登录
        </el-button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.no-permission-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(circle at top, rgba(231, 160, 96, 0.18), transparent 40%),
    linear-gradient(180deg, #f8f4ee 0%, #f1ebe2 100%);
}

.no-permission-card {
  width: min(520px, 100%);
  padding: 40px 36px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(199, 91, 57, 0.12);
  box-shadow: 0 24px 60px rgba(92, 66, 45, 0.12);
  text-align: center;

  h1 {
    margin: 0 0 12px;
    font-size: 28px;
    color: #3e2d22;
  }

  p {
    margin: 0;
    line-height: 1.8;
    color: #7c6b61;
  }
}

.status-code {
  margin-bottom: 18px;
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  color: #c75b39;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 28px;
}
</style>
