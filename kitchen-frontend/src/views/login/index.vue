<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/modules/app'

const router = useRouter()
const appStore = useAppStore()
const { t } = useI18n()

const loading = ref(false)
const form = reactive({
  username: '',
  password: ''
})

const handleLogin = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }

  loading.value = true
  try {
    await appStore.loginWithPassword(form.username, form.password)
    const role = appStore.currentRole
    router.push(role === 'supervisor' ? '/dashboard' : '/kds')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-page__card panel-card">
      <h1>{{ t('login.title') }}</h1>
      <p>{{ t('login.subtitle') }}</p>
      <el-form class="login-page__form" @submit.prevent>
        <el-form-item>
          <el-input v-model="form.username" size="large" :placeholder="t('login.username')" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" size="large" type="password" show-password :placeholder="t('login.password')" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button class="touch-primary-button" type="warning" :loading="loading" @click="handleLogin">
            {{ t('login.submit') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: radial-gradient(circle at top, #fff4e5, #f4f6f8 60%);
}

.login-page__card {
  width: min(460px, 100%);
  padding: 28px;
}

.login-page__card h1 {
  font-size: 32px;
}

.login-page__card p {
  margin-top: 10px;
  color: $text-secondary;
}

.login-page__form {
  margin-top: 24px;
}

.login-page__form :deep(.el-input__wrapper) {
  min-height: 56px;
}

.login-page__form :deep(.el-button) {
  width: 100%;
}
</style>
