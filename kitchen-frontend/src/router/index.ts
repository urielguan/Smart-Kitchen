import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'

const resolveDefaultRoute = (role: 'supervisor' | 'chef') => (role === 'supervisor' ? '/dashboard' : '/home')

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hiddenLayout: true }
  },
  {
    path: '/',
    redirect: () => {
      const appStore = useAppStore()
      return resolveDefaultRoute(appStore.currentRole)
    }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/index.vue'),
    meta: { title: '后厨主管总控台', hiddenLayout: true }
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: '首页看板', hiddenLayout: true }
  },
  {
    path: '/kds',
    name: 'Kds',
    component: () => import('@/views/kds/index.vue'),
    meta: { title: 'KDS 后厨操作台', hiddenLayout: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || '智慧厨房后厨端'} - 智慧厨房后厨端`

  const appStore = useAppStore()
  if (!appStore.isAuthenticated && to.path !== '/login') {
    next('/login')
    return
  }

  if (appStore.isAuthenticated && to.path === '/login') {
    next(resolveDefaultRoute(appStore.currentRole))
    return
  }

  next()
})

export default router
