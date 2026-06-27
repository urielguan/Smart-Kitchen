<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { forceChangePassword } from '@/api/modules/auth'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const form = reactive({
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)

// 与个人中心修改密码保持一致的校验规则
const rules: FormRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20位', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        if (!/[A-Z]/.test(value)) {
          callback(new Error('密码必须包含至少一个大写字母'))
          return
        }
        if (!/[a-z]/.test(value)) {
          callback(new Error('密码必须包含至少一个小写字母'))
          return
        }
        if (!/[0-9]/.test(value)) {
          callback(new Error('密码必须包含至少一个数字'))
          return
        }
        if (!/[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(value)) {
          callback(new Error('密码必须包含至少一个特殊符号'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await forceChangePassword({ newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请重新登录')
    userStore.clearAuth()
    userStore.mustChangePassword = false
    router.push('/login')
  } catch (error: any) {
    console.error('修改密码失败:', error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">修改密码</h2>
      <p class="login-subtitle">首次登录需要修改密码后才能继续使用</p>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleSubmit">
        <el-form-item prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="请输入新密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <div class="password-tips">
          <p>密码要求：</p>
          <ul>
            <li>长度8-20位</li>
            <li>包含大写字母（A-Z）</li>
            <li>包含小写字母（a-z）</li>
            <li>包含数字（0-9）</li>
            <li>包含特殊符号（如 !@#$%^&* 等）</li>
          </ul>
        </div>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleSubmit"
            style="width: 100%"
          >
            确认修改
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.login-title {
  text-align: center;
  margin-bottom: 8px;
  font-size: 24px;
  color: #303133;
}

.login-subtitle {
  text-align: center;
  margin-bottom: 30px;
  font-size: 14px;
  color: #909399;
}

.password-tips {
  background: #f4f4f5;
  border-radius: 4px;
  padding: 12px 16px;
  margin-bottom: 18px;

  p {
    font-size: 13px;
    color: #606266;
    margin: 0 0 8px 0;
    font-weight: 500;
  }

  ul {
    margin: 0;
    padding-left: 20px;
    font-size: 12px;
    color: #909399;
    line-height: 1.8;
  }
}
</style>
