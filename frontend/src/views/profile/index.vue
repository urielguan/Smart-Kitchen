<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Edit, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'
import * as authApi from '@/api/modules/auth'

const router = useRouter()
const userStore = useUserStore()

// 用户信息
const userInfo = computed(() => userStore.userInfo)

// 加载状态
const loading = ref(false)

// 修改密码相关
const passwordDialogVisible = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordLoading = ref(false)

// 编辑个人信息相关
const profileDialogVisible = ref(false)
const profileFormRef = ref<FormInstance>()
const profileForm = reactive({
  email: '',
  phone: '',
  gender: undefined as number | undefined,
  avatarUrl: ''
})
const profileLoading = ref(false)

// 头像上传
const avatarUploading = ref(false)

// 个人信息保存按钮状态（上传头像时也显示加载中）
const profileSubmitLoading = computed(() => profileLoading.value || avatarUploading.value)

/** 处理头像上传 */
const handleAvatarChange = async (file: File) => {
  const isImage = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }

  try {
    avatarUploading.value = true
    const res = await authApi.uploadAvatar(file)
    if (res.code === 'SUCCESS' && res.data) {
      profileForm.avatarUrl = res.data.avatarUrl
      ElMessage.success('头像上传成功')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '头像上传失败')
  } finally {
    avatarUploading.value = false
  }
  return false
}

// 密码表单校验规则
const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20位', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!value) {
          callback()
          return
        }
        // 至少一个大写字母
        if (!/[A-Z]/.test(value)) {
          callback(new Error('密码必须包含至少一个大写字母'))
          return
        }
        // 至少一个小写字母
        if (!/[a-z]/.test(value)) {
          callback(new Error('密码必须包含至少一个小写字母'))
          return
        }
        // 至少一个数字
        if (!/[0-9]/.test(value)) {
          callback(new Error('密码必须包含至少一个数字'))
          return
        }
        // 至少一个特殊符号
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
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 个人信息表单校验规则
const profileRules: FormRules = {
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' },
    { max: 100, message: '邮箱长度不能超过100个字符', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ]
}

// 格式化日期
const formatDateTime = (dateStr?: string): string => {
  if (!dateStr) return '-'
  try {
    const date = new Date(dateStr)
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  } catch {
    return dateStr
  }
}

// 角色显示
const roleNames = computed(() => {
  return userInfo.value?.roles?.join('、') || '-'
})

// 性别显示
const genderText = computed(() => {
  const gender = userInfo.value?.gender
  if (gender === undefined || gender === null) return '-'
  const map: Record<number, string> = { 0: '未知', 1: '男', 2: '女' }
  return map[gender] || '-'
})

// 状态显示
const statusText = computed(() => {
  const status = userInfo.value?.status
  if (!status) return '-'
  const map: Record<string, string> = { active: '正常', inactive: '禁用', locked: '锁定' }
  return map[status] || status
})

// 状态标签类型
const statusType = computed(() => {
  const status = userInfo.value?.status
  if (status === 'active') return 'success'
  if (status === 'inactive') return 'danger'
  if (status === 'locked') return 'warning'
  return 'info'
})

// 打开修改密码对话框
const openPasswordDialog = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 打开编辑个人信息对话框
const openProfileDialog = () => {
  profileForm.email = userInfo.value?.email || ''
  profileForm.phone = userInfo.value?.phone || ''
  profileForm.gender = userInfo.value?.gender
  profileForm.avatarUrl = userInfo.value?.avatar || ''
  profileDialogVisible.value = true
}

// 提交修改密码
const submitPasswordChange = async () => {
  if (!passwordFormRef.value) return

  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  passwordLoading.value = true
  try {
    const res = await authApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })

    if (res.code === 'SUCCESS') {
      ElMessage.success('密码修改成功，请重新登录')
      passwordDialogVisible.value = false
      // 清除登录状态，跳转到登录页
      userStore.clearAuth()
      router.push('/login')
    }
  } catch (error: any) {
    console.error('修改密码失败:', error.message)
  } finally {
    passwordLoading.value = false
  }
}

// 提交修改个人信息
const submitProfileChange = async () => {
  if (!profileFormRef.value) return

  try {
    await profileFormRef.value.validate()
  } catch {
    return
  }

  profileLoading.value = true
  try {
    const res = await authApi.updateProfile({
      // 个人中心支持清空邮箱：空字符串也要提交给后端执行覆盖更新
      email: profileForm.email?.trim() ?? '',
      phone: profileForm.phone || undefined,
      gender: profileForm.gender,
      avatarUrl: profileForm.avatarUrl || undefined
    })

    if (res.code === 'SUCCESS') {
      ElMessage.success('个人信息修改成功')
      profileDialogVisible.value = false
      // 刷新用户信息
      await userStore.fetchUserInfo()
    }
  } catch (error: any) {
    console.error('修改个人信息失败:', error.message)
  } finally {
    profileLoading.value = false
  }
}

// 返回上一页
const goBack = () => {
  router.back()
}
</script>

<template>
  <div class="profile-page">
    <div class="page-header">
      <el-page-header @back="goBack">
        <template #content>
          <span class="page-title">个人中心</span>
        </template>
      </el-page-header>
    </div>

    <div class="profile-content" v-loading="loading">
      <!-- 基本信息卡片 -->
      <el-card class="info-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon><User /></el-icon>
            <span>基本信息</span>
            <el-button type="primary" :icon="Edit" size="small" @click="openProfileDialog" style="margin-left: auto;">
              编辑信息
            </el-button>
          </div>
        </template>

        <div class="avatar-section">
          <el-avatar :size="72" :src="userInfo?.avatar">
            <el-icon :size="32"><User /></el-icon>
          </el-avatar>
          <div class="avatar-info">
            <div class="avatar-name">{{ userInfo?.realName || userInfo?.userName || '-' }}</div>
            <div class="avatar-role">{{ roleNames }}</div>
          </div>
        </div>

        <el-descriptions :column="2" border style="margin-top: 16px;">
          <el-descriptions-item label="用户名">
            {{ userInfo?.userName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="真实姓名">
            {{ userInfo?.realName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="手机号">
            {{ userInfo?.phone || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">
            {{ userInfo?.email || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="性别">
            {{ genderText }}
          </el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag :type="statusType" size="small">
              {{ statusText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="所属组织">
            {{ userInfo?.orgName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="角色">
            {{ roleNames }}
          </el-descriptions-item>
          <el-descriptions-item label="最后登录时间" :span="2">
            {{ formatDateTime(userInfo?.lastLoginAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 安全设置卡片 -->
      <el-card class="security-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon><Lock /></el-icon>
            <span>安全设置</span>
          </div>
        </template>

        <div class="security-item">
          <div class="security-info">
            <div class="security-title">登录密码</div>
            <div class="security-desc">定期修改密码可以提高账号安全性</div>
          </div>
          <el-button type="primary" :icon="Edit" @click="openPasswordDialog">
            修改密码
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="100px"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            placeholder="请输入原密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>

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

      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPasswordChange" :loading="passwordLoading">
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑个人信息对话框 -->
    <el-dialog
      v-model="profileDialogVisible"
      title="编辑个人信息"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="profileFormRef"
        :model="profileForm"
        :rules="profileRules"
        label-width="80px"
      >
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="profileForm.phone" placeholder="请输入手机号" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="profileForm.email" placeholder="请输入邮箱" maxlength="100" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="profileForm.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="头像">
          <div class="avatar-upload-wrapper">
            <el-upload
              class="avatar-uploader"
              action="#"
              :show-file-list="false"
              :before-upload="handleAvatarChange"
              accept="image/*"
            >
              <div v-if="profileForm.avatarUrl" class="avatar-preview">
                <img :src="profileForm.avatarUrl" alt="头像" />
                <div class="avatar-overlay">
                  <el-icon><Plus /></el-icon>
                  <span>更换头像</span>
                </div>
              </div>
              <div v-else class="avatar-placeholder">
                <el-icon :size="24"><Plus /></el-icon>
                <span>上传头像</span>
              </div>
            </el-upload>
            <div class="avatar-tip">支持 JPG、PNG、GIF、WebP 格式，最大 5MB</div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitProfileChange"
          :loading="profileSubmitLoading"
          :disabled="profileSubmitLoading"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.profile-page {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 60px);
}

.page-header {
  margin-bottom: 20px;

  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

.profile-content {
  max-width: 900px;
}

.info-card,
.security-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
}

.security-info {
  .security-title {
    font-size: 14px;
    font-weight: 500;
    color: #303133;
    margin-bottom: 4px;
  }

  .security-desc {
    font-size: 12px;
    color: #909399;
  }
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.avatar-info {
  .avatar-name {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }

  .avatar-role {
    font-size: 13px;
    color: #909399;
    margin-top: 4px;
  }
}

.avatar-upload-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.avatar-uploader {
  :deep(.el-upload) {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    overflow: hidden;
    transition: border-color 0.3s;

    &:hover {
      border-color: #409eff;
    }
  }
}

.avatar-preview {
  position: relative;
  width: 100px;
  height: 100px;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .avatar-overlay {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.3s;
    color: #fff;
    font-size: 12px;
    gap: 4px;
  }

  &:hover .avatar-overlay {
    opacity: 1;
  }
}

.avatar-placeholder {
  width: 100px;
  height: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  font-size: 12px;
  gap: 4px;
}

.avatar-tip {
  font-size: 12px;
  color: #909399;
}

.password-tips {
  background: #f4f4f5;
  border-radius: 4px;
  padding: 12px 16px;
  margin-top: 10px;

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
