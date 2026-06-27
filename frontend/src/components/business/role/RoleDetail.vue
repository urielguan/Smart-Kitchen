<script setup lang="ts">
import { computed } from 'vue'
import { useRoleStore } from '@/stores/modules/role'
import { STATUS_MAP, DATA_SCOPE_MAP } from '@/constants/role'
import { ROLE_PERMISSIONS } from '@/constants/permission'
import type { PermissionNode, OrgTreeNode } from '@/types/role'

const roleStore = useRoleStore()

const role = computed(() => roleStore.getCurrentRole())

const orgMetaMap = computed(() => {
  const map = new Map<number, { name: string; status?: string }>()
  const walk = (nodes: OrgTreeNode[]) => {
    nodes.forEach((node) => {
      map.set(node.id, {
        name: node.orgName,
        status: node.status
      })
      if (node.children?.length) {
        walk(node.children)
      }
    })
  }
  walk(roleStore.orgTree)
  walk(roleStore.orgTreeAll)
  return map
})

const statusInfo = computed(() => role.value ? STATUS_MAP[role.value.status] : null)
const dataScopeLabel = computed(() => role.value ? DATA_SCOPE_MAP[role.value.dataScope] : '-')
const dataScopeOrgDisplay = computed(() => {
  const orgIds = role.value?.dataScopeOrgIds || []
  return orgIds
    .map((id) => {
      const org = orgMetaMap.value.get(id)
      if (!org) {
        return String(id)
      }
      return org.status && org.status !== 'active' ? `${org.name}（已停用）` : org.name
    })
    .join('、')
})
const permissionMetaMap = computed(() => {
  const map = new Map<string, { name: string; order: number }>()
  let order = 0

  const walk = (nodes: PermissionNode[]) => {
    nodes.forEach((node) => {
      if (node.permissionCode && node.permissionType !== 'module') {
        map.set(node.permissionCode, {
          name: node.permissionName,
          order: order++
        })
      }
      if (node.children?.length) {
        walk(node.children)
      }
    })
  }

  walk(roleStore.permissionTree)
  return map
})

const permissionDisplays = computed(() => {
  const codes = role.value?.funcPermissions || []
  const detailNameMap = role.value?.funcPermissionNameMap || {}

  return codes
    .map((code, index) => {
      const meta = permissionMetaMap.value.get(code)
      return {
        code,
        name: meta?.name || detailNameMap[code] || code,
        order: meta?.order ?? Number.MAX_SAFE_INTEGER,
        index
      }
    })
    .sort((a, b) => (a.order - b.order) || (a.index - b.index))
})

const permCount = computed(() => permissionDisplays.value.length)

const handleClose = () => {
  roleStore.closeDetail(!roleStore.formVisible)
}

const handleEdit = () => {
  if (role.value) {
    const roleId = role.value.id
    roleStore.openForm(roleId, null, role.value)
    roleStore.closeDetail(false)
  }
}
</script>

<template>
  <el-dialog
    :model-value="roleStore.detailVisible"
    width="600px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="role-detail-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">角色详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-if="role" class="detail-body">
      <!-- 基础信息 -->
      <div class="detail-section">
        <div class="section-title">
          <span class="title-bar" />
          基础信息
        </div>
        <div class="info-table">
          <div class="info-label">角色名称</div>
          <div class="info-value">{{ role.roleName }}</div>
          <div class="info-label">角色编码</div>
          <div class="info-value">{{ role.roleCode }}</div>

          <div class="info-label">所属分组</div>
          <div class="info-value">{{ role.groupName }}</div>
          <div class="info-label">状态</div>
          <div class="info-value">
            <el-tag :type="statusInfo?.type" size="small">{{ statusInfo?.label }}</el-tag>
          </div>

          <div class="info-label">已授权模块</div>
          <div class="info-value">{{ permCount }} 个</div>
          <div class="info-label">创建时间</div>
          <div class="info-value">{{ role.createdAt }}</div>

          <div class="info-label">备注</div>
          <div class="info-value info-value--span3">{{ role.remark || '-' }}</div>
        </div>
      </div>

      <!-- 数据权限 -->
      <div class="detail-section">
        <div class="section-title">
          <span class="title-bar" />
          数据权限
        </div>
        <div class="data-scope-info">
          <el-tag type="primary">{{ dataScopeLabel }}</el-tag>
          <template v-if="role.dataScope !== 'all' && role.dataScopeOrgIds?.length">
            <span class="org-scope">
              限定范围：{{ dataScopeOrgDisplay }}
            </span>
          </template>
        </div>
      </div>

      <!-- 功能权限 -->
      <div class="detail-section">
        <div class="section-title">
          <span class="title-bar" />
          功能权限（共 {{ permCount }} 项）
        </div>
        <div class="permission-list">
          <el-tag
            v-for="perm in permissionDisplays"
            :key="perm.code"
            size="small"
            effect="plain"
          >
            {{ perm.name }}
          </el-tag>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">关闭</el-button>
        <el-button class="btn-save" v-permission="ROLE_PERMISSIONS.EDIT" @click="handleEdit">编辑</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.role-detail-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.role-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.role-detail-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  height: 480px;
  overflow-y: auto;
}

.role-detail-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
/* ---- 头部 ---- */
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 32px;
}

.dialog-title {
  font-family: 'Poppins', 'PingFang SC', sans-serif;
  font-weight: 500;
  font-size: 20px;
  line-height: 30px;
  color: #000000;
}

.close-btn {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 32px;
  height: 32px;
  background: #FFF2E2;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: #FFE8CC;
  }
}

/* ---- 底部 ---- */
.dialog-footer {
  display: flex;
  align-items: center;
  gap: 9px;
  justify-content: flex-end;
  padding: 12px 24px 16px;
}

.btn-cancel {
  width: 58px;
  height: 32px;
  background: #FFFFFF;
  border: 1px solid #BEC0CA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.016);
  color: #53545C;
  font-size: 13px;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-save {
  width: 58px;
  height: 32px;
  background: #7288FA;
  border-color: #7288FA;
  border-radius: 6px;
  color: #fff;
  font-size: 13px;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #fff;
  }
}

/* ---- 详情内容 ---- */
.detail-body {
  // scroll handled by unscoped dialog body
}

.detail-section {
  margin-bottom: 20px;

  &:last-child {
    margin-bottom: 0;
  }
}

.section-title {
  display: flex;
  align-items: center;
  font-weight: 500;
  font-size: 16px;
  color: #303133;
  margin-bottom: 12px;
}

.title-bar {
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

/* ---- Info Table ---- */
.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  padding: 8px 12px;
  font-size: 14px;
  color: #333333;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  display: flex;
  align-items: center;
  min-height: 40px;
}

.info-value {
  padding: 8px 12px;
  font-size: 14px;
  color: #333333;
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  display: flex;
  align-items: center;
  min-height: 40px;
}

.info-value--span3 {
  grid-column: span 3;
  white-space: pre-wrap;
  word-break: break-all;
}

/* ---- 数据权限 ---- */
.data-scope-info {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;

  .org-scope {
    font-size: 13px;
    color: #606266;
  }
}

/* ---- 功能权限标签 ---- */
.permission-list {
  max-height: 200px;
  overflow-y: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

:deep(.el-tag--success) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--warning) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--info) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--primary) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-tag--danger) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}
</style>
