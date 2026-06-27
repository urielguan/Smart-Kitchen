<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoleStore } from '@/stores/modules/role'
import { STATUS_OPTIONS } from '@/constants/role'
import PermissionTree from './PermissionTree.vue'
import DataScopeSelector from './DataScopeSelector.vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { RoleStatus, DataScope } from '@/types/role'

const roleStore = useRoleStore()

const formRef = ref<FormInstance>()
const activeTab = ref('base')
const formData = ref({
  roleName: '',
  roleCode: '',
  groupId: null as number | null,
  status: 'active' as RoleStatus,
  dataScope: 'all' as DataScope,
  dataScopeOrgIds: [] as number[],
  funcPermissions: [] as string[],
  remark: ''
})

const isEdit = computed(() => !!roleStore.currentRoleId)
const title = computed(() => isEdit.value ? '编辑角色' : '新增角色')
const hasUnauthorizedCurrentGroup = computed(() => {
  const currentRole = roleStore.currentRole
  return !!(isEdit.value && currentRole && currentRole.groupVisible === false)
})

const rules: FormRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' }
  ],
  roleCode: [
    { required: true, message: '请输入角色编码', trigger: 'blur' }
  ],
  groupId: [
    { required: true, message: '请选择所属分组', trigger: 'change' }
  ]
}

watch(() => roleStore.formVisible, async (visible) => {
  if (visible) {
    await Promise.all([
      roleStore.fetchPermissionTree(),
      roleStore.fetchOrgTree()
    ])
    activeTab.value = 'base'
    const role = roleStore.getCurrentRole()
    if (role) {
      const editableGroupId = role.groupVisible === false ? null : role.groupId
      formData.value = {
        roleName: role.roleName,
        roleCode: role.roleCode,
        groupId: editableGroupId,
        status: role.status,
        dataScope: role.dataScope,
        dataScopeOrgIds: role.dataScopeOrgIds || [],
        funcPermissions: role.funcPermissions || [],
        remark: role.remark || ''
      }
    } else {
      formData.value = {
        roleName: '',
        roleCode: '',
        groupId: roleStore.defaultGroupId,
        status: 'active',
        dataScope: 'all',
        dataScopeOrgIds: [],
        funcPermissions: [],
        remark: ''
      }
    }
  }
})

const handleClose = () => {
  formRef.value?.resetFields()
  activeTab.value = 'base'
  roleStore.closeForm()
}

const handleSubmit = async () => {
  // 验证基础信息
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    activeTab.value = 'base'
    return
  }

  if (isEdit.value && roleStore.currentRoleId) {
    await roleStore.updateRole(roleStore.currentRoleId, formData.value)
  } else {
    await roleStore.createRole(formData.value)
  }
}

const handlePermissionChange = (val: string[]) => {
  formData.value.funcPermissions = val
}

const handleDataScopeChange = (val: DataScope) => {
  formData.value.dataScope = val
}

const handleOrgIdsChange = (val: number[]) => {
  formData.value.dataScopeOrgIds = val
}
</script>

<template>
  <el-dialog
    :model-value="roleStore.formVisible"
    width="680px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="role-form-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ title }}</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <!-- Tab 导航（卡片式） -->
    <el-tabs v-model="activeTab" type="card" class="form-tabs">
      <!-- 基础信息 Tab -->
      <el-tab-pane label="基础信息" name="base">
        <el-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          label-width="100px"
          label-suffix="："
          class="form-content"
        >
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="角色名称" prop="roleName">
                <el-input
                  v-model="formData.roleName"
                  placeholder="请输入角色名称"
                  maxlength="50"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="角色编码" prop="roleCode">
                <el-input
                  v-model="formData.roleCode"
                  placeholder="如 PURCHASER"
                  maxlength="50"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="24">
            <el-col :span="12">
              <el-form-item label="所属分组" prop="groupId">
                <el-select
                  v-model="formData.groupId"
                  placeholder="请选择分组"
                  style="width: 100%"
                >
                  <el-option
                    v-for="group in roleStore.groups"
                    :key="group.id"
                    :label="group.groupName"
                    :value="group.id"
                    :disabled="group.id === -1"
                  />
                </el-select>
              </el-form-item>
              <div v-if="hasUnauthorizedCurrentGroup" class="group-warning">
                当前角色原所属分组已无权限访问，请重新选择可见分组后再保存。
              </div>
            </el-col>
            <el-col :span="12">
              <el-form-item label="状态">
                <el-select v-model="formData.status" style="width: 100%">
                  <el-option
                    v-for="item in STATUS_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="备注">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="2"
              placeholder="请输入角色说明"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 功能权限 Tab -->
      <el-tab-pane label="功能权限" name="func">
        <div class="form-content">
          <PermissionTree
            v-model="formData.funcPermissions"
            @update:model-value="handlePermissionChange"
          />
        </div>
      </el-tab-pane>

      <!-- 数据权限 Tab -->
      <el-tab-pane label="数据权限" name="data">
        <div class="form-content">
          <DataScopeSelector
            :model-value="formData.dataScope"
            :org-ids="formData.dataScopeOrgIds"
            @update:model-value="handleDataScopeChange"
            @update:org-ids="handleOrgIdsChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">取消</el-button>
        <el-button class="btn-save" @click="handleSubmit">确认保存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.role-form-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  max-height: 80vh;
}

.role-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.role-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.role-form-dialog.el-dialog .el-dialog__footer {
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
  width: 90px;
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

/* ---- Tab 卡片式样式 ---- */
.form-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-tabs__header) {
    flex-shrink: 0;
    margin: 0;
    border-bottom: 1px solid #E1E2E9;
  }

  :deep(.el-tabs__nav-wrap) {
    margin-bottom: -6px;
    overflow: visible !important;
  }

  :deep(.el-tabs__nav-scroll) {
    overflow: visible !important;
  }

  :deep(.el-tabs__nav) {
    border: none;
    overflow: visible !important;
  }

  :deep(.el-tabs__item) {
    font-family: 'PingFang SC', sans-serif;
    font-size: 14px;
    color: #606266;
    height: 36px;
    line-height: 36px;
    padding: 0 20px;
    margin-right: 4px;
    background: #FAFAFA;
    border: 1px solid #F0F0F0 !important;
    border-bottom: 1px solid #E1E2E9 !important;
    border-radius: 0;

    &:hover {
      color: #7288FA;
    }

    &.is-active {
      color: #7288FA;
      background: #FFFFFF;
      border-bottom-color: #FFFFFF !important;
    }

    &.is-disabled {
      color: #C0C4CC;
      cursor: not-allowed;
      background: #FAFAFA;
    }
  }

  :deep(.el-tabs__content) {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 16px 0px 16px 0px;
  }
}

.form-content {
  padding: 0 0px;
  min-height: 240px;
}

.group-warning {
  margin-top: -6px;
  color: #ED8A40;
  font-size: 12px;
}

/* ---- 表单 ---- */
:deep(.el-input__wrapper) {
  height: 32px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #D9D9D9 inset;

  &:hover {
    box-shadow: 0 0 0 1px #D9D9D9 inset;
  }

  &.is-focus {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }
}

:deep(.el-select .el-input__wrapper) {
  height: 32px;
}

:deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #FF4D4F inset !important;
}


/* ---- 文本域 ---- */
:deep(.el-textarea__inner) {
  border: 1px solid #D9D9D9;
  border-radius: 2px;
  font-size: 14px;
  padding: 5px 12px;

  &:hover {
    border-color: #7288FA;
  }

  &:focus {
    border-color: #7288FA;
    box-shadow: none;
  }
}
</style>
