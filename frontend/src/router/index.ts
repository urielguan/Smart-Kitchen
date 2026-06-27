import { h, KeepAlive } from 'vue'
import { createRouter, createWebHistory, RouteRecordRaw, RouterView } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import { isDevPreviewMockEnabled, seedDevPreviewAuth } from '@/dev-preview/env'
import { isDevBypassLoginEnabled } from '@/utils/dev-auth'

const NestedKeepAliveRouteView = {
  render: () =>
    h(RouterView, null, {
      default: ({ Component }: { Component: any }) =>
        Component ? h(KeepAlive, null, [h(Component)]) : null
    })
}

/** 路由配置 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hiddenLayout: true }
  },
  {
    path: '/force-change-password',
    name: 'ForceChangePassword',
    component: () => import('@/views/login/force-change-password.vue'),
    meta: { title: '修改密码', hiddenLayout: true }
  },
  {
    path: '/no-permission',
    name: 'NoPermission',
    component: () => import('@/views/error/no-permission.vue'),
    meta: { title: '无权限访问', hiddenLayout: true }
  },
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '数据看板', icon: '📊', group: '数据概览' }
  },
  // 采购管理
  {
    path: '/supplier',
    name: 'Supplier',
    component: () => import('@/views/scm/supplier/index.vue'),
    meta: { title: '供应商管理', icon: '🏢', group: '采购管理' }
  },
  {
    path: '/purchase-plan',
    component: NestedKeepAliveRouteView,
    meta: { title: '采购计划', icon: '📑', group: '采购管理' },
    children: [
      {
        path: '',
        name: 'PurchasePlan',
        component: () => import('@/views/scm/purchase-plan/index.vue')
      },
      {
        path: 'purchase-demand-forecast',
        name: 'PurchaseDemandForecast',
        component: () => import('@/views/scm/purchase-demand-forecast/index.vue'),
        meta: { title: '采购需求预测' }
      }
    ]
  },
  {
    path: '/purchase-demand-forecast',
    redirect: '/purchase-plan/purchase-demand-forecast',
    meta: { hidden: true }
  },
  {
    path: '/purchase',
    name: 'Purchase',
    component: () => import('@/views/scm/purchase/index.vue'),
    meta: { title: '采购订单', icon: '📋', group: '采购管理' }
  },
  // 仓储管理
  {
    path: '/warehouse',
    name: 'Warehouse',
    component: () => import('@/views/wms/warehouse/index.vue'),
    meta: { title: '仓库信息管理', icon: '🏭', group: '仓储管理' }
  },
  {
    path: '/material',
    name: 'Material',
    component: () => import('@/views/wms/material/index.vue'),
    meta: { title: '物料信息管理', icon: '📦', group: '仓储管理' }
  },
  {
    path: '/inventory',
    name: 'Inventory',
    component: () => import('@/views/wms/inventory/index.vue'),
    meta: { title: '库存汇总', icon: '📊', group: '仓储管理' }
  },
  {
    path: '/inbound',
    name: 'Inbound',
    component: () => import('@/views/wms/inbound/index.vue'),
    meta: { title: '入库管理', icon: '📥', group: '仓储管理' }
  },
  {
    path: '/outbound',
    name: 'Outbound',
    component: () => import('@/views/wms/outbound/index.vue'),
    meta: { title: '出库管理', icon: '📤', group: '仓储管理' }
  },
  {
    path: '/stocktake',
    name: 'Stocktake',
    component: () => import('@/views/wms/stocktake/index.vue'),
    meta: { title: '盘点管理', icon: '📝', group: '仓储管理' }
  },
  {
    path: '/stocktake/create',
    name: 'StocktakeCreate',
    component: () => import('@/views/wms/stocktake/edit.vue'),
    meta: { title: '新增盘点单', hidden: true }
  },
  {
    path: '/stocktake/:id/edit',
    name: 'StocktakeEdit',
    component: () => import('@/views/wms/stocktake/edit.vue'),
    meta: { title: '编辑盘点单', hidden: true }
  },
  // 菜谱营养
  {
    path: '/recipe',
    name: 'Recipe',
    component: () => import('@/views/cook/recipe/index.vue'),
    meta: { title: '菜谱库管理', icon: '🍽️', group: '菜谱营养' }
  },
  {
    path: '/plan',
    name: 'Plan',
    component: () => import('@/views/cook/plan/index.vue'),
    meta: { title: '菜谱计划', icon: '📅', group: '菜谱营养' }
  },
  {
    path: '/plan-adjustment',
    name: 'PlanAdjustment',
    component: () => import('@/views/cook/plan/adjustment.vue'),
    meta: { title: '菜谱计划调整管理', icon: '📝', group: '菜谱营养' }
  },
  // 后厨管理
  {
    path: '/cook',
    name: 'Cook',
    component: () => import('@/views/cook/record/index.vue'),
    meta: { title: '烹饪记录', icon: '👨‍🍳', group: '后厨管理' }
  },
  {
    path: '/sample',
    name: 'Sample',
    component: () => import('@/views/cook/sample/index.vue'),
    meta: { title: '留样管理', icon: '🧪', group: '后厨管理' }
  },
  {
    path: '/morning-check',
    name: 'MorningCheck',
    component: () => import('@/views/cook/morning-check/index.vue'),
    meta: { title: '智能人脸晨检', icon: '🤖', group: '后厨管理' }
  },
  {
    path: '/video-monitor',
    name: 'VideoMonitor',
    component: () => import('@/views/cook/video-monitor/index.vue'),
    meta: { title: '视频监控管理', icon: '📷', group: '后厨管理' }
  },
  {
    path: '/video-playback',
    name: 'VideoPlayback',
    component: () => import('@/views/cook/video-playback/index.vue'),
    meta: { title: '视频回放', icon: '📼', group: '后厨管理' }
  },
  {
    path: '/violation',
    name: 'Violation',
    component: () => import('@/views/cook/violation/index.vue'),
    meta: { title: 'AI违规识别', icon: '🚨', group: '后厨管理' }
  },
  {
    path: '/behavior-analysis',
    name: 'BehaviorAnalysis',
    component: () => import('@/views/cook/behavior-analysis/index.vue'),
    meta: { title: 'AI人员行为分析', icon: '📊', group: '后厨管理' }
  },
  {
    path: '/device',
    name: 'Device',
    component: () => import('@/views/cook/device/index.vue'),
    meta: { title: '设备管理', icon: '🔧', group: '后厨管理' }
  },
  {
    path: '/alert',
    name: 'Alert',
    component: () => import('@/views/cook/alert/index.vue'),
    meta: { title: '告警管理', icon: '🚨', group: '后厨管理' }
  },
  // 系统管理
  {
    path: '/org',
    name: 'Org',
    component: () => import('@/views/sys/org/index.vue'),
    meta: { title: '组织管理', icon: '🏗️', group: '系统管理' }
  },
  {
    path: '/employee',
    name: 'Employee',
    component: () => import('@/views/sys/employee/index.vue'),
    meta: { title: '员工管理', icon: '👤', group: '系统管理' }
  },
  {
    path: '/dict-category',
    name: 'DictCategory',
    component: () => import('@/views/sys/dict-category/index.vue'),
    meta: { title: '字典分类维护', icon: '🗂️', group: '系统管理' }
  },
  {
    path: '/role',
    name: 'Role',
    component: () => import('@/views/sys/role/index.vue'),
    meta: { title: '角色权限管理', icon: '🔐', group: '系统管理' }
  },
  {
    path: '/evaluation',
    name: 'Evaluation',
    component: () => import('@/views/sys/evaluation/index.vue'),
    meta: { title: '评价管理', icon: '⭐', group: '系统管理' }
  },
  {
    path: '/notification',
    name: 'Notification',
    component: () => import('@/views/sys/notification/index.vue'),
    meta: { title: '消息中心', icon: '🔔', group: '系统管理' }
  },
  {
    path: '/ai-config',
    redirect: { path: '/integration-management', query: { tab: 'ai-config' } },
    meta: { title: 'AI接口管理', hidden: true }
  },
  {
    path: '/integration-management',
    name: 'IntegrationManagement',
    component: () => import('@/views/sys/integration/index.vue'),
    meta: { title: '第三方接入管理', icon: '🔌', group: '系统管理' }
  },
  // 个人中心
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/index.vue'),
    meta: { title: '个人中心', icon: '👤', hidden: true }
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 路由守卫 */
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  document.title = `${to.meta.title || '智慧食安管理平台'} - 智慧食安管理平台`

  if (!localStorage.getItem('token') && isDevPreviewMockEnabled()) {
    seedDevPreviewAuth()
  }

  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    next('/login')
    return
  }

  const userStore = useUserStore()
  if (isDevBypassLoginEnabled()) {
    if (!userStore.userInfo) {
      await userStore.fetchUserInfo()
    }
    if (to.path === '/login') {
      next('/dashboard')
      return
    }
    next()
    return
  }

  if (token && !userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      userStore.clearAuth()
      next('/login')
      return
    }
  }

  // 首次登录强制改密：只允许访问改密页（放在 fetchUserInfo 之后，确保 userInfo 已加载）
  if (token && userStore.mustChangePassword) {
    if (to.path !== '/force-change-password') {
      next('/force-change-password')
      return
    }
    next()
    return
  }

  if (token && to.path === '/login') {
    const firstRoute = userStore.getFirstAccessibleRoute()
    if (firstRoute) {
      next(firstRoute)
    } else {
      ElMessage.warning('当前用户没有任何页面权限，请联系管理员')
      next('/no-permission')
    }
    return
  }

  if (token && to.path !== '/no-permission' && !userStore.canAccessRoute(to.path)) {
    const firstRoute = userStore.getFirstAccessibleRoute()
    if (firstRoute) {
      next(firstRoute)
    } else {
      ElMessage.warning('当前用户没有任何页面权限，请联系管理员')
      next('/no-permission')
    }
    return
  }

  next()
})

export default router
