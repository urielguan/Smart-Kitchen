<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoleStore } from '@/stores/modules/role'
import RoleCard from '@/components/business/role/RoleCard.vue'
import RoleGroupForm from '@/components/business/role/RoleGroupForm.vue'
import RoleForm from '@/components/business/role/RoleForm.vue'
import RoleDetail from '@/components/business/role/RoleDetail.vue'
import RoleEmployees from '@/components/business/role/RoleEmployees.vue'
import { ROLE_PERMISSIONS } from '@/constants/permission'

const roleStore = useRoleStore()

/** 搜索关键字 */
const searchKeyword = ref(roleStore.keyword)

/** 初始化 */
onMounted(() => {
  roleStore.init()
})

/** 组件卸载时清理状态 */
onUnmounted(() => {
  roleStore.closeEmployees()
  roleStore.closeDetail()
  roleStore.closeForm()
})

/** 搜索 */
const handleSearch = () => {
  roleStore.search(searchKeyword.value)
}

/** 重置 */
const handleReset = () => {
  searchKeyword.value = ''
  roleStore.search('')
}

/** 新增分组 */
const handleAddGroup = () => {
  roleStore.openGroupForm(null)
}

/** 新增角色 */
const handleAddRole = () => {
  roleStore.openForm(null, roleStore.selectedGroupId)
}

/** 编辑分组 */
const handleEditGroup = (id: number) => {
  roleStore.openGroupForm(id)
}

/** 删除分组 */
const handleDeleteGroup = (id: number) => {
  roleStore.deleteGroup(id)
}

/** 选择分组 */
const handleSelectGroup = (id: number | null) => {
  roleStore.selectGroup(id)
}

/** 编辑角色 */
const handleEdit = (id: number) => {
  roleStore.openForm(id)
}

/** 删除角色 */
const handleDelete = (id: number) => {
  roleStore.deleteRole(id)
}

/** 查看详情 */
const handleDetail = (id: number) => {
  roleStore.openDetail(id)
}

/** 查看关联成员 */
const handleViewMembers = (id: number) => {
  roleStore.openEmployees(id)
}

/** 格式化分组角色数量 */
const formatCount = (count: number) => {
  return count > 99 ? '99+' : String(count)
}
</script>

<template>
  <div class="role-page">
    <!-- 左侧侧边栏 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <el-button class="btn-add" v-permission="ROLE_PERMISSIONS.CREATE" @click="handleAddRole">新增角色</el-button>
        <el-button class="btn-add-group" v-permission="ROLE_PERMISSIONS.GROUP_CREATE" @click="handleAddGroup">新增分组</el-button>
      </div>

      <div class="sidebar-menu">
        <!-- 全部角色 -->
        <div
          class="menu-item"
          :class="{ active: roleStore.selectedGroupId === null }"
          @click="handleSelectGroup(null)"
        >
          <span class="menu-name">全部权限</span>
          <span class="menu-count">（{{ formatCount(roleStore.roles.length) }}）</span>
          <span class="menu-name"></span>
        </div>

        <!-- 各分组 -->
        <div
          v-for="group in roleStore.normalizedGroups"
          :key="group.id"
          class="menu-item"
          :class="{ active: roleStore.selectedGroupId === group.id }"
          @click="handleSelectGroup(group.id)"
        >
          <span class="menu-name">{{ group.groupName }}</span>
          <span class="menu-count">（{{ formatCount(roleStore.groupRoleCountMap.get(group.id) || 0) }}）</span>
          <div class="menu-actions" v-if="group.id !== -1" @click.stop>
            <span class="menu-action-btn" v-permission="ROLE_PERMISSIONS.GROUP_EDIT" @click="handleEditGroup(group.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z" fill="currentColor"/></svg>
            </span>
            <span class="menu-action-btn danger" v-permission="ROLE_PERMISSIONS.GROUP_DELETE" @click="handleDeleteGroup(group.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" fill="currentColor"/></svg>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- 右侧主内容区 -->
    <div class="main-content">
      <!-- 搜索工具栏 -->
      <div class="toolbar">
        <el-col :span="4">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索角色名称或编码"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
        </el-col>
        <el-col :span="20" style="text-align: right">
          <el-button class="btn-search" @click="handleSearch">查询</el-button>
          <el-button class="btn-reset" @click="handleReset">重置</el-button>
        </el-col>
      </div>

      <!-- 角色卡片网格 + 分页 -->
      <div class="content-wrapper">
        <div class="card-grid">
          <div v-if="roleStore.pagedRoles.length" class="card-grid-inner">
            <RoleCard
              v-for="role in roleStore.pagedRoles"
              :key="role.id"
              :role="role"
              :member-count="role.memberCount || 0"
              @detail="handleDetail"
              @edit="handleEdit"
              @delete="handleDelete"
              @view-members="handleViewMembers"
            />
          </div>
          <div v-else class="empty-tip">
            <template v-if="searchKeyword">未找到与「{{ searchKeyword }}」匹配的角色</template>
            <template v-else>暂无角色数据，点击「新增角色」创建</template>
          </div>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="roleStore.filteredTotal > 0">
        <span class="total">共 {{ roleStore.filteredTotal }} 项数据</span>
        <el-pagination
          :current-page="roleStore.pageNum"
          :page-size="roleStore.pageSize"
          :page-sizes="[10, 20, 40]"
          :total="roleStore.filteredTotal"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="roleStore.changePage"
          @size-change="roleStore.changePageSize"
        />
      </div>
      </div>
    </div>

    <!-- 弹窗组件 -->
    <RoleGroupForm />
    <RoleForm />
    <RoleDetail />
    <RoleEmployees />
  </div>
</template>

<style lang="scss" scoped>
.role-page {
  height: 100%;
  min-height: 0;
  display: flex;
  overflow: hidden;
  gap: 8px;
}

/* ---- 左侧侧边栏 ---- */
.sidebar {
  width: 200px;
  flex-shrink: 0;
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-header {
  padding: 8px;
  display: flex;
  gap: 0px;
  flex-shrink: 0;
  border-bottom: 1px solid #E7E7E7;

  .btn-add {
    flex: 1;
    height: 32px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    color: #fff;
    font-size: 13px;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-add-group {
    flex: 1;
    height: 32px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    color: #7288FA;
    font-size: 13px;

    &:hover {
      background: #EEF1FF;
      border-color: #5C75E8;
      color: #5C75E8;
    }
  }
}

.sidebar-menu {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;

  &:hover {
    background: #F5F9FF;

    .menu-actions {
      opacity: 1;
    }
  }

  &.active {
    background: #F5F9FF;

    .menu-name {
      color: #7288FA;
      font-weight: 500;
    }
  }
}

.menu-name {
  flex: 1;
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-count {
  font-size: 12px;
  color: #F93C00;
  margin-left: 4px;
  flex-shrink: 0;
}

.menu-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
  margin-left: auto;
  padding-left: 12px;
}

.menu-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  color: #909399;
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: #EBF2FF;
    color: #5570F1;
  }

  &.danger:hover {
    background: #FFF0ED;
    color: #FF7474;
  }
}

/* ---- 右侧主内容区 ---- */
.main-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.toolbar {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  padding: 20px;
  margin-bottom: 8px;
  flex-shrink: 0;
  display: flex;
  gap: 8px;
  align-items: center;

  :deep(.el-button) {
    margin-left: 0;
  }

  .btn-reset {
    margin-left: 12px;
    margin-right: 12px;
  }

  .btn-search {
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-reset {
    height: 32px;
    padding: 5px 16px;
    background: #F2F4F8;
    border-color: #F2F4F8;
    border-radius: 6px;
    color: rgba(0, 0, 0, 0.9);

    &:hover {
      background: #E3E7EF;
      border-color: #E3E7EF;
      color: rgba(0, 0, 0, 0.9);
    }
  }
}

.card-grid {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 20px 20px 0;
}

.card-grid-inner {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 16px;
}

.empty-tip {
  text-align: center;
  color: #909399;
  padding: 80px 20px;
  font-size: 14px;
}

/* ---- 内容区容器（卡片+分页合并） ---- */
.content-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* ---- 分页 ---- */
.pagination {
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    color: rgba(0, 0, 0, 0.6);
  }

  :deep(.el-pagination .el-pager) {
    gap: 4px;
  }

  :deep(.el-pagination .is-active) {
    width: 32px;
    height: 32px;
    background: #7288FA;
    border-radius: 3px;
    color: #fff;
  }

  :deep(.el-pagination .el-pager li:not(.is-active)) {
    width: 32px;
    height: 32px;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: rgba(0, 0, 0, 0.6);
  }
}
</style>
