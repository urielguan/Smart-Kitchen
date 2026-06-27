<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import notificationIcon from '@/assets/images/notification-icon.png'
import { ElMessage } from 'element-plus'
import { sysNotificationApi, type SysNotificationVO } from '@/api/modules/sys-notification'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 当前页面标题
const currentPageTitle = computed(() => {
  return (route.meta.title as string) || ''
})

// 面包屑数据
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(item => item.meta && item.meta.title)
  const result: { path: string; title?: string }[] = [{ path: '/'}]

  const parentBreadcrumb: Record<string, string> = {
    '/video-playback': '视频监控管理',
    '/violation': '视频监控管理',
    '/behavior-analysis': '视频监控管理',
  }

  matched.forEach(item => {
    if (item.meta.group) {
      result.push({ path: '', title: item.meta.group as string })
    }
    const parent = parentBreadcrumb[item.path]
    if (parent) {
      result.push({ path: '', title: parent })
    }
    result.push({ path: item.path, title: item.meta.title as string })
  })

  return result
})

// 用户信息
const userAvatar = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.userName || '用户'
  return name.charAt(0)
})

// 语言选择
const currentLang = ref('中文')

const handleLanguageChange = (lang: string) => {
  currentLang.value = lang
}

const handleLogout = async () => {
  await userStore.logout()
  router.push('/login')
}

// 跳转到个人中心
const goToProfile = () => {
  router.push('/profile')
}

// ========== 通知相关 ==========
const unreadCount = ref(0)
const notifications = ref<SysNotificationVO[]>([])
const loading = ref(false)
const pollingInterval = ref<ReturnType<typeof setInterval> | null>(null)
const notificationServiceUnavailable = ref(false)

const isNotificationEndpointUnavailable = (error: unknown) => {
  const normalizedError = error as Error & { backendPayload?: { code?: string } }
  const message = typeof normalizedError?.message === 'string' ? normalizedError.message : ''
  return normalizedError?.backendPayload?.code === 'NOT_FOUND'
    || message.includes('接口不存在')
    || message.includes('请求的资源不存在')
}

const disableNotificationFeature = () => {
  unreadCount.value = 0
  notifications.value = []
  notificationServiceUnavailable.value = true
  stopPolling()
}

// 获取未读数量
const fetchUnreadCount = async () => {
  if (notificationServiceUnavailable.value) {
    return
  }
  try {
    const res = await sysNotificationApi.getUnreadCount({ silentError: true })
    if (res.code === 'SUCCESS' && res.data) {
      unreadCount.value = res.data.count || 0
    }
  } catch (error) {
    if (isNotificationEndpointUnavailable(error)) {
      disableNotificationFeature()
      return
    }
    console.error('获取未读数量失败:', error)
  }
}

// 获取通知列表（最近10条）
const fetchNotifications = async () => {
  if (notificationServiceUnavailable.value) {
    return
  }
  loading.value = true
  try {
    const res = await sysNotificationApi.getList({ pageNum: 1, pageSize: 10 }, { silentError: true })
    if (res.code === 'SUCCESS' && res.data) {
      notifications.value = res.data.list || []
    }
  } catch (error) {
    if (isNotificationEndpointUnavailable(error)) {
      disableNotificationFeature()
      return
    }
    console.error('获取通知列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 定时刷新
const startPolling = () => {
  if (notificationServiceUnavailable.value) {
    return
  }
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
  }
  pollingInterval.value = setInterval(() => {
    fetchUnreadCount()
  }, 60000)
}

const stopPolling = () => {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
    pollingInterval.value = null
  }
}

// 显示通知面板
const showPanel = () => {
  if (notificationServiceUnavailable.value) {
    return
  }
  fetchNotifications()
}

// 分类标签类型
const getCategoryTagType = (category: string) => {
  const map: Record<string, string> = {
    food_safety_alert: 'danger',
    approval_todo: 'warning',
    system_notice: 'info',
    security_risk: 'danger',
    platform_announcement: ''
  }
  return map[category] || 'info'
}

// 标记已读
const handleMarkAsRead = async (item: SysNotificationVO) => {
  try {
    const res = await sysNotificationApi.markAsRead(item.id)
    if (res.code === 'SUCCESS') {
      ElMessage.success('已标记为已读')
      fetchNotifications()
      fetchUnreadCount()
    }
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

// 批量已读
const handleBatchMarkAsRead = async () => {
  const unreadIds = notifications.value
    .filter(n => n.readStatus === 'unread')
    .map(n => n.id)
  if (unreadIds.length === 0) {
    ElMessage.warning('没有可标记的通知')
    return
  }
  try {
    const res = await sysNotificationApi.batchMarkAsRead(unreadIds)
    if (res.code === 'SUCCESS') {
      ElMessage.success('批量标记成功')
      fetchNotifications()
      fetchUnreadCount()
    }
  } catch (error) {
    console.error('批量标记失败:', error)
  }
}

// 查看全部
const showAll = () => {
  router.push('/notification')
}

// 生命周期钩子
onMounted(() => {
  fetchUnreadCount()
  if (!notificationServiceUnavailable.value) {
    startPolling()
  }
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <header class="header">
    <!-- 标题行 -->
    <div class="header-top">
      <div class="page-title">{{ currentPageTitle }}</div>
      <div class="header-actions">
        <!-- 通知和用户信息 -->
        <div class="user-section">
      <!-- 通知图标 -->
      <el-dropdown trigger="click" @visible-change="(visible: boolean) => { if (visible) showPanel() }">
        <span class="notification-trigger">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="notification-badge">
            <img :src="notificationIcon" alt="" class="notification-icon" :class="{ 'has-unread': unreadCount > 0 }" />
          </el-badge>
        </span>
        <template #dropdown>
          <!-- 通知面板 -->
          <div class="notification-panel">
            <div class="panel-header">
              <h3>系统通知</h3>
              <div class="panel-actions">
                <el-button link type="primary" size="small" @click="showAll">查看全部</el-button>
                <el-button link type="success" size="small" @click="handleBatchMarkAsRead">全部已读</el-button>
              </div>
            </div>

            <!-- 通知列表 -->
            <div class="notification-list" v-loading="loading">
              <div v-if="notifications.length === 0" class="notification-empty">
                <el-empty description="暂无通知" />
              </div>
              <template v-else>
                <div
                  v-for="item in notifications"
                  :key="item.id"
                  class="notification-item"
                  :class="{ unread: item.readStatus === 'unread' }"
                >
                  <div class="item-header">
                    <div class="item-left">
                      <el-tag :type="getCategoryTagType(item.category)" size="small">
                        {{ item.categoryName }}
                      </el-tag>
                    </div>
                    <div class="item-right">
                      <span class="item-time">{{ item.timeDisplay }}</span>
                    </div>
                  </div>
                  <div class="item-body">
                    <div class="item-title">{{ item.title }}</div>
                    <div v-if="item.summary" class="item-content">{{ item.summary }}</div>
                  </div>
                  <div class="item-actions">
                    <el-button v-if="item.readStatus === 'unread'" size="small" @click="handleMarkAsRead(item)">标记已读</el-button>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </template>
      </el-dropdown>

      <!-- 语言选择 -->
      <el-dropdown trigger="click" @command="handleLanguageChange">
        <div class="language-selector">
          <span class="language-text">{{ currentLang }}</span>
          <span class="language-arrow"></span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="中文" :class="{ 'is-active': currentLang === '中文' }">中文</el-dropdown-item>
            <el-dropdown-item command="English" :class="{ 'is-active': currentLang === 'English' }">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 用户信息 -->
      <el-dropdown trigger="click">
        <div class="user-info">
          <el-avatar :size="32" class="avatar" :src="userStore.userInfo?.avatar || undefined">{{ userStore.userInfo?.avatar ? '' : userAvatar }}</el-avatar>
          <span class="user-name">{{ userStore.userInfo?.realName || userStore.userInfo?.userName || '管理员' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goToProfile">个人中心</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
        </div>
      </div>
    </div>
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <img src="@/assets/images/home-icon.png" alt="" class="breadcrumb-home" />
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
          <router-link v-if="item.path" :to="item.path">{{ item.title }}</router-link>
          <span v-else>{{ item.title }}</span>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
  </header>
</template>

<style lang="scss" scoped>
.header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 14px 21px;
  gap: 10px;
  background: #FFFFFF;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.page-title {
  font-family: 'Poppins', sans-serif;
  font-size: 20px;
  font-weight: 500;
  line-height: 30px;
  color: #45464E;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.breadcrumb {
  font-size: 14px;
  padding-top: 10px;
  border-top: 1px solid #F1F3F9;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;

  .breadcrumb-home {
    width: 16px;
    height: 16px;
    flex-shrink: 0;
  }

  a {
    color: #666;
    font-weight: normal;

    &:hover {
      color: #333;
    }
  }
}

.user-section {
  display: flex;
  align-items: center;
  gap: 10px;
}

.language-selector {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 5px 12px;
  width: 92px;
  height: 32px;
  background: #FEF5EA;
  border-radius: 8px;
  cursor: pointer;
  gap: 4px;
  transition: background 0.2s;

  &:hover {
    background: #FDE8CC;
  }

  .language-text {
    font-size: 13px;
    color: #333;
    font-weight: 500;
  }

  .language-arrow {
    font-size: 12px;
    color: #1C1D22;
    border: 2px solid #1C1D22;
    border-top: none;
    border-left: none;
    border-radius: 0 0 2px 0;
    width: 6px;
    height: 6px;
    transform: rotate(45deg);
    margin-top: -3px;
  }
}

.notification-badge {
  cursor: pointer;

  .notification-icon {
    width: 20px;
    height: 20px;
    transition: transform 0.2s;

    &.has-unread {
      animation: bell-shake 0.5s ease-in-out;
    }
  }
}

.notification-panel {
  width: 400px;
  max-height: 500px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  padding: 16px;
  margin-top: 8px;

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 12px;
    border-bottom: 1px solid #ebeef5;

    h3 {
      font-size: 15px;
      font-weight: 600;
      margin: 0;
    }

    .panel-actions {
      display: flex;
      gap: 8px;
    }
  }

  .notification-list {
    max-height: 350px;
    overflow-y: auto;
    padding: 0 4px;
  }

  .notification-section {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-top: 12px;

    &:first-child {
      padding-top: 0;
    }
  }

  .section-title {
    padding: 4px 2px 0;
    font-size: 13px;
    font-weight: 600;
    color: #606266;
  }

  .notification-empty {
    text-align: center;
    padding: 32px 16px;
    color: #909399;
  }

  .notification-item {
    padding: 12px 16px;
    background: #fff;
    border-radius: 8px;
    margin-bottom: 8px;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: #f5f7fa;
    }

    &.unread {
      background: #fff8e8;
    }

    &.supplier-alert-item {
      background: #fff4f4;
      border: 1px solid #fbd3d3;
    }

    .item-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .item-left {
        display: flex;
        gap: 8px;
      }

      .item-right {
        .item-time {
          font-size: 12px;
          color: #909399;
        }
      }
    }

    .item-body {
      .item-title {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
        margin-bottom: 4px;
      }

      .item-content {
        font-size: 13px;
        color: #606266;
        line-height: 1.4;
      }
    }

    .recommended-recipes {
      margin-top: 8px;
      padding: 8px;
      background: #f5f7fa;
      border-radius: 4px;

      .recipes-label {
        font-size: 12px;
        color: #909399;
        font-weight: 500;
      }

      .recipes-list {
        display: flex;
        flex-wrap: wrap;
        gap: 8px;
        margin-top: 4px;

        .recipe-tag {
          padding: 4px 8px;
          background: #409eff;
          color: #fff;
          border-radius: 4px;
          font-size: 12px;
          cursor: pointer;

          &:hover {
            opacity: 0.8;
          }
        }
      }
    }

    .item-actions {
      display: flex;
      gap: 8px;
      margin-top: 8px;

      .el-button {
        padding: 4px 8px;
      }
    }
  }
}

.qualification-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  cursor: pointer;

  .avatar {
    background: #409eff;
    color: #fff;
    font-size: 14px;
    flex-shrink: 0;
    border-radius: 8px;
  }

  .user-name {
    font-family: 'Poppins', sans-serif;
    font-weight: 600;
    font-size: 16px;
    line-height: 24px;
    color: #333F4E;
    white-space: nowrap;
  }
}

@keyframes bell-shake {
  0%, 100% {
    transform: rotate(0);
  }
  10%, 30% {
    transform: rotate(-10deg);
  }
  20%, 40%, 60%, 80% {
    transform: rotate(10deg);
  }
  50%, 70%, 90% {
    transform: rotate(-10deg);
  }
}
</style>
