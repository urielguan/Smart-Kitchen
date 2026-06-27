<script setup lang="ts">
import { computed } from 'vue'
import type { Role } from '@/types/role'
import { STATUS_MAP, DATA_SCOPE_MAP } from '@/constants/role'
import { ROLE_PERMISSIONS } from '@/constants/permission'

const props = defineProps<{
  role: Role
  memberCount: number
}>()

const emit = defineEmits<{
  (e: 'detail', id: number): void
  (e: 'edit', id: number): void
  (e: 'delete', id: number): void
  (e: 'viewMembers', id: number): void
}>()

const statusInfo = computed(() => STATUS_MAP[props.role.status])
const dataScopeLabel = computed(() => DATA_SCOPE_MAP[props.role.dataScope])
const funcCount = computed(() => props.role.funcPermissions?.length || 0)
</script>

<template>
  <div class="role-card">
    <!-- 顶部：角色名 + 查看详情 -->
    <div class="role-card__header">
      <span class="role-card__name">{{ role.roleName }}</span>
      <span class="role-card__detail-btn" @click="emit('detail', role.id)">查看详情 ></span>
    </div>
    <div class="role-card__divider"></div>

    <!-- 中间：左侧信息 + 右侧状态/模块 -->
    <div class="role-card__body">
      <div class="role-card__info">
        <div class="info-row"><span class="info-label">编码：</span>{{ role.roleCode }}</div>
        <div class="info-row"><span class="info-label">关联人员：</span><span>{{ memberCount }} 人</span></div>
        <div class="info-row"><span class="info-label">数据权限：</span><span>{{ dataScopeLabel }}</span></div>
        <div class="info-row info-remark"><span class="info-label">备注：</span><span>{{ role.remark || '-' }}</span></div>
      </div>
      <div class="role-card__badge">
        <div class="badge-left">
          <span class="badge-value" :class="role.status === 'active' ? 'is-active' : 'is-disabled'">{{ statusInfo.label }}</span>
          <span class="badge-sub">状态</span>
        </div>
        <span class="badge-divider"></span>
        <div class="badge-right">
          <span class="badge-value badge-count">{{ funcCount }}个</span>
          <span class="badge-sub">功能模块</span>
        </div>
      </div>
    </div>

    <!-- 底部：操作按钮 -->
    <div class="role-card__footer">
      <el-button class="btn-action btn-primary" @click="emit('viewMembers', role.id)">查看关联人员</el-button>
      <el-button class="btn-action btn-primary" v-permission="ROLE_PERMISSIONS.EDIT" @click="emit('edit', role.id)">编辑角色</el-button>
      <el-button class="btn-action btn-danger" v-permission="ROLE_PERMISSIONS.DELETE" @click="emit('delete', role.id)">删除角色</el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.role-card {
  height: 224px;
  border: 1px solid #E7E7E7;
  border-radius: 8px;
  padding: 0;
  background: #F6F8FC;
  display: flex;
  flex-direction: column;
  min-width: 0;
  transition: all 0.2s;

  &:hover {
    background: #FFFFFF;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  }

  /* ---- 顶部 ---- */
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px 0;
    min-height: 36px;
  }

  &__name {
    font-weight: 500;
    font-size: 16px;
    color: #303133;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  &__detail-btn {
    font-size: 14px;
    color: #7288FA;
    cursor: pointer;
    flex-shrink: 0;
    margin-left: 8px;

    &:hover {
      color: #5C75E8;
    }
  }

  &__divider {
    height: 1px;
    background: #E7E7E7;
    margin: 8px 0 0;
  }

  /* ---- 中间 ---- */
  &__body {
    display: flex;
    padding: 10px 16px 0;
    min-height: 0;
  }

  &__info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  &__badge {
    flex-shrink: 0;
    margin-left: 12px;
    margin-top: 8px;
    display: flex;
    align-items: stretch;
    background: #ECF4FF;
    border-radius: 6px;
    padding: 6px 12px;
    height: fit-content;
    gap: 12px;
    align-self: flex-start;
  }

  &__footer {
    padding: 16px 16px 10px;
    display: flex;
    gap: 8px;
    flex-shrink: 0;

    :deep(.el-button) {
      margin-left: 0;
    }
  }
}

.info-row {
  font-size: 14px;
  color: #8B8D97;
  line-height: 1.6;
  display: flex;
  min-width: 0;

  .info-label {
    flex-shrink: 0;
    color: #33343A;
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.info-remark {
  span:last-child {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.badge-left {
  width: 50px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.badge-right {
  width: 60px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.badge-value {
  font-size: 16px;
  font-weight: 500;
  line-height: 1.2;

  &.is-active {
    color: #008858;
  }

  &.is-disabled {
    color: #D4A017;
  }
}

.badge-count {
  color: #7288FA;
}

.badge-sub {
  font-size: 14px;
  color: #8B8D97;
  line-height: 1.2;
}

.badge-divider {
  width: 1px;
  align-self: stretch;
  background: #D9D9D9;
}

/* ---- 底部按钮 ---- */
.btn-action {
  height: 32px;
  padding: 0 12px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 400;
  border: none;

  &.btn-primary {
    background: #7288FA;
    color: #fff;

    &:hover,
    &:focus {
      background: #5C75E8;
      color: #fff;
    }
  }

  &.btn-danger {
    background: #FF7474;
    color: #fff;

    &:hover,
    &:focus {
      background: #FF3D3D;
      color: #fff;
    }
  }
}
</style>
