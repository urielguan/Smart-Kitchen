<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import defaultIcon from '@/assets/images/menu-icon.png'
import activeIcon from '@/assets/images/menu-icon-active.png'
import hoverIcon from '@/assets/images/menu-icon-hover.png'

interface MenuItem {
  label: string
  page: string
  icon: string
  badge?: number
}

interface MenuGroup {
  group: string
  icon: string
  items: MenuItem[]
  defaultOpen?: boolean
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 菜单配置 */
const menuConfig: MenuGroup[] = [
  {
    group: '数据概览',
    icon: 'DataAnalysis',
    items: [{ label: '数据看板', page: 'dashboard', icon: 'DataLine' }]
  },
  {
    group: '采购管理',
    icon: 'ShoppingCart',
    items: [
      { label: '供应商管理', page: 'supplier', icon: 'OfficeBuilding' },
      { label: '采购计划', page: 'purchase-plan', icon: 'Document' },
      { label: '采购订单', page: 'purchase', icon: 'List' }
    ]
  },
  {
    group: '仓储管理',
    icon: 'Box',
    items: [
      { label: '仓库信息管理', page: 'warehouse', icon: 'House' },
      { label: '物料信息管理', page: 'material', icon: 'Goods' },
      { label: '库存汇总', page: 'inventory', icon: 'DataBoard' },
      { label: '入库管理', page: 'inbound', icon: 'Download' },
      { label: '出库管理', page: 'outbound', icon: 'Upload' },
      { label: '盘点管理', page: 'stocktake', icon: 'EditPen' }
    ]
  },
  {
    group: '菜谱营养',
    icon: 'Bowl',
    items: [
      { label: '菜谱库管理', page: 'recipe', icon: 'Food' },
      { label: '菜谱计划', page: 'plan', icon: 'Calendar' },
      { label: '菜谱计划调整管理', page: 'plan-adjustment', icon: 'EditPen' }
    ]
  },
  {
    group: '后厨管理',
    icon: 'Bowl',
    items: [
      { label: '烹饪记录', page: 'cook', icon: 'Dish' },
      { label: '留样管理', page: 'sample', icon: 'IceCreamRound' },
      { label: '智能人脸晨检', page: 'morning-check', icon: 'Cpu' },
      { label: '视频监控管理', page: 'video-monitor', icon: 'VideoCamera' },
      { label: '设备管理', page: 'device', icon: 'SetUp' },
      { label: '告警管理', page: 'alert', icon: 'Bell' }
    ]
  },
  {
    group: '系统管理',
    icon: 'Setting',
    items: [
      { label: '组织管理', page: 'org', icon: 'OfficeBuilding' },
      { label: '员工管理', page: 'employee', icon: 'User' },
      { label: '字典分类维护', page: 'dict-category', icon: 'CollectionTag' },
      { label: '角色权限管理', page: 'role', icon: 'Lock' },
      { label: '评价管理', page: 'evaluation', icon: 'Star' },
      { label: '消息中心', page: 'notification', icon: 'Bell' },
      { label: '第三方接入管理', page: 'integration-management', icon: 'Connection' }
    ]
  }
]

/** 展开的分组 */
const expandedGroups = ref<Set<string>>(new Set(menuConfig.map(group => group.group)))

/** 按权限过滤后的菜单 */
const visibleMenuConfig = computed(() => {
  return menuConfig
    .map(group => ({
      ...group,
      items: group.items.filter(item => userStore.canAccessRoute(`/${item.page}`))
    }))
    .filter(group => group.items.length > 0)
})

/** 当前激活路径 */
const activePath = computed(() => route.path)

/** 子页面映射到父级菜单 */
const childToParent: Record<string, string> = {
  'video-playback': 'video-monitor',
  'violation': 'video-monitor',
  'behavior-analysis': 'video-monitor',
  'purchase-plan/purchase-demand-forecast': 'purchase-plan',
}

/** 判断是否激活 */
const isActive = (page: string) => {
  const resolved = childToParent[activePath.value.slice(1)] || activePath.value.slice(1)
  return resolved === page
}

const activeGroup = computed(() => {
  return visibleMenuConfig.value.find(group =>
    group.items.some(item => isActive(item.page))
  )?.group || ''
})

watch(
  visibleMenuConfig,
  () => {
    if (activeGroup.value) {
      expandedGroups.value.add(activeGroup.value)
    }
  },
  { immediate: true, deep: true }
)

watch(
  () => route.path,
  () => {
    if (activeGroup.value) {
      expandedGroups.value.add(activeGroup.value)
    }
  },
  { immediate: true }
)

/** 切换页面 */
const switchPage = async (page: string) => {
  const targetPath = `/${page}`
  if (route.path === targetPath) {
    return
  }
  try {
    await router.push(targetPath)
  } catch (error) {
    console.error('Sidebar route switch failed:', error)
  }
}

/** 分组颜色 */
const groupColors: Record<string, string> = {
  '数据概览': '#4A90E2',
  '采购管理': '#FDAD00',
  '仓储管理': '#67C23A',
  '菜谱营养': '#F56C6C',
  '后厨管理': '#E6A23C',
  '系统管理': '#909399',
}

/** 切换分组展开 */
const toggleGroup = (group: string) => {
  if (expandedGroups.value.has(group)) {
    expandedGroups.value.delete(group)
  } else {
    expandedGroups.value.add(group)
  }
}
</script>

<template>
  <aside class="sidebar">
    <!-- Logo -->
    <div class="sidebar-header">
      <img src="@/assets/images/logo.png" alt="logo" class="header-logo" />
      <span class="header-title">智慧食安</span>
    </div>

    <!-- 菜单 -->
    <div class="sidebar-menu">
      <div v-for="group in visibleMenuConfig" :key="group.group" class="menu-group">
        <div class="menu-group-title" @click="toggleGroup(group.group)">
          <span class="group-dot" :style="{ background: groupColors[group.group] }"></span>
          <span class="group-label">{{ group.group }}</span>
          <span class="group-arrow" :class="{ expanded: expandedGroups.has(group.group) }"></span>
        </div>
        <transition name="collapse">
          <div v-show="expandedGroups.has(group.group)" class="menu-group-items">
            <div
              v-for="item in group.items"
              :key="item.page"
              class="nav-item"
              :class="{ active: isActive(item.page) }"
              @click="switchPage(item.page)"
            >
              <span class="nav-item-text">
                <img
                  :src="isActive(item.page) ? activeIcon : defaultIcon"
                  alt=""
                  class="nav-icon default-icon"
                />
                <img
                  :src="hoverIcon"
                  alt=""
                  class="nav-icon hover-icon"
                />
                <img
                  :src="activeIcon"
                  alt=""
                  class="nav-icon active-icon"
                />
                {{ item.label }}
              </span>
              <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </aside>
</template>

<style lang="scss" scoped>
.sidebar {
  width: $sidebar-width;
  background: $sidebar-bg;
  color: $text-primary;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid $border-light;
}

.sidebar-header {
  height: $header-height;
  position: relative;

  .header-logo {
    position: absolute;
    width: 44.97px;
    height: 45.7px;
    left: 29.85px;
    top: 12px;
  }

  .header-title {
    position: absolute;
    top: 20px;
    left: 85px;
    width: 80px;
    height: 30px;
    display: flex;
    align-items: center;
    font-size: 16px;
    font-weight: bold;
    color: $text-primary;
  }
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.menu-group {
  margin-bottom: 4px;

  & + .menu-group {
    border-top: 1px solid #F2F4F8;
    padding-top: 12px;
    margin-top: 8px;
  }
}

.menu-group-title {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: $text-secondary;
  transition: color 0.15s;
  user-select: none;

  &:hover {
    color: $text-primary;
  }

  .group-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
    margin-right: 8px;
  }

  .group-label {
    flex: 1;
  }

  .group-arrow {
    width: 12px;
    height: 12px;
    position: relative;
    flex-shrink: 0;

    &::before,
    &::after {
      content: '';
      position: absolute;
      background: #C0C5CA;
      transition: opacity 0.2s ease;
    }

    /* 横线（−），始终显示 */
    &::before {
      width: 10px;
      height: 2px;
      left: calc(50% - 5px);
      top: calc(50% - 1px);
    }

    /* 竖线（|），收起时显示，展开时隐藏 */
    &::after {
      width: 2px;
      height: 10px;
      left: calc(50% - 1px);
      top: calc(50% - 5px);
      opacity: 1;
    }

    /* 展开时隐藏竖线，只留横线 = "−" */
    &.expanded::after {
      opacity: 0;
    }
  }
}

.menu-group-items {
  overflow: hidden;
}

.nav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  height: 40px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
  color: $text-regular;
  font-size: 14px;
  margin: 0 20px;

  &:hover {
    color: #7288FA;

    .default-icon {
      display: none;
    }

    .hover-icon {
      display: block;
    }
  }

  &.active {
    background: #7288FA;
    color: #fff;
    box-shadow: 0px 4px 24px rgba(65, 89, 214, 0.3);
    border-radius: 12px;

    .default-icon {
      display: none;
    }

    .hover-icon {
      display: none;
    }

    .active-icon {
      display: block;
    }
  }

  &.active:hover {
    color: #fff;
  }
}

.nav-item-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-icon {
  width: 20px;
  height: 20px;
}

.hover-icon,
.active-icon {
  display: none;
}

.nav-badge {
  background: $danger-color;
  color: #fff;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 折叠动画 */
.collapse-enter-active,
.collapse-leave-active {
  transition: all 0.2s ease;
  max-height: 500px;
}

.collapse-enter-from,
.collapse-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
