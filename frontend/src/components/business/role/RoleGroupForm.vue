<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoleStore } from '@/stores/modules/role'
import type { FormInstance, FormRules } from 'element-plus'

const roleStore = useRoleStore()

const formRef = ref<FormInstance>()
const formData = ref({
  groupName: '',
  sortOrder: 1,
  remark: ''
})

const isEdit = computed(() => !!roleStore.currentGroupId)
const title = computed(() => isEdit.value ? '编辑角色分组' : '新增角色分组')

const rules: FormRules = {
  groupName: [
    { required: true, message: '请输入分组名称', trigger: 'blur' }
  ]
}

watch(() => roleStore.groupFormVisible, (visible) => {
  if (visible) {
    const group = roleStore.getCurrentGroup()
    if (group) {
      formData.value = {
        groupName: group.groupName,
        sortOrder: group.sortOrder,
        remark: group.remark || ''
      }
    } else {
      formData.value = {
        groupName: '',
        sortOrder: roleStore.groups.length + 1,
        remark: ''
      }
    }
  }
})

const handleClose = () => {
  formRef.value?.resetFields()
  roleStore.closeGroupForm()
}

const handleSubmit = async () => {
  const valid = await formRef.value?.validate()
  if (!valid) return

  if (isEdit.value && roleStore.currentGroupId) {
    await roleStore.updateGroup(roleStore.currentGroupId, formData.value)
  } else {
    await roleStore.createGroup(formData.value)
  }
}
</script>

<template>
  <el-dialog
    :model-value="roleStore.groupFormVisible"
    width="500px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="role-group-form-dialog"
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

    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="100px"
      label-suffix="："
    >
      <el-row :gutter="24">
        <el-col :span="18">
          <el-form-item label="分组名称" prop="groupName">
            <el-input
              v-model="formData.groupName"
              placeholder="请输入分组名称"
              maxlength="50"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="24">
        <el-col :span="12">
          <el-form-item label="排序">
            <el-input-number
              v-model="formData.sortOrder"
              :min="0"
              :max="999"
              :step="1"
              :precision="0"
              step-strictly
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="备注">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="2"
          placeholder="请输入备注"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>

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
.role-group-form-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
}

.role-group-form-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.role-group-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px 16px;
}

.role-group-form-dialog.el-dialog .el-dialog__footer {
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
