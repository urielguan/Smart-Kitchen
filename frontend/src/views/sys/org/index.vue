<script setup lang="ts">
import { computed, ref, onActivated, onMounted, watch } from 'vue'
import { useOrgStore } from '@/stores/modules/org'
import { ORG_STATUS_OPTIONS } from '@/constants/org'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { mapDictOptions } from '@/utils/dict-category'
import OrgStatistics from '@/components/business/org/OrgStatistics.vue'
import OrgTreeList from '@/components/business/org/OrgTreeList.vue'
import OrgForm from '@/components/business/org/OrgForm.vue'
import OrgDetail from '@/components/business/org/OrgDetail.vue'
import OrgMemberDialog from '@/components/business/org/OrgMemberDialog.vue'
import OrgImportDialog from '@/components/business/org/OrgImportDialog.vue'
import OrgImportResultDialog from '@/components/business/org/OrgImportResultDialog.vue'
import type { Organization, OrgType, OrgStatus, OrgTreeNode } from '@/types/org'
import { ORG_PERMISSIONS } from '@/constants/permission'

const orgStore = useOrgStore()
const dictCategoryStore = useDictCategoryStore()
const orgActivatedOnce = ref(false)

/** 搜索表单 */
const searchForm = ref({
  keyword: orgStore.searchParams.keyword || '',
  orgType: orgStore.searchParams.orgType as OrgType | undefined,
  status: orgStore.searchParams.status as OrgStatus | undefined,
  includeChildren: !!orgStore.searchParams.includeChildren
})

/** 当前编辑的组织 */
const currentOrg = ref<Organization | null>(null)

/** 新增子组织时的预设父组织ID */
const presetParentId = ref<number | null>(null)

const orgTypeOptions = computed(() => mapDictOptions(dictCategoryStore.getCachedOptions('org_type')))

/** 字典缓存被清除后自动重新拉取（同时刷新启用项和全量项缓存） */
const dictCached = computed(() => dictCategoryStore.getCachedOptions('org_type', true))
watch(dictCached, (val, oldVal) => {
  if (oldVal && oldVal.length > 0 && val.length === 0) {
    dictCategoryStore.fetchOptions('org_type')
    dictCategoryStore.fetchOptions('org_type', true)
  }
})

/** 成员弹窗 */
const memberDialogVisible = ref(false)
const selectedOrgId = ref<number | null>(null)
const selectedOrgName = ref('')

const refreshOrgPage = async () => {
  await Promise.all([
    dictCategoryStore.fetchOptions('org_type', false, true),
    dictCategoryStore.fetchOptions('org_type', true, true),
    orgStore.fetchList(),
    orgStore.fetchStatistics(),
    orgStore.fetchTree(),
    orgStore.fetchAllTree()
  ])
}

/** 初始化 */
onMounted(async () => {
  await Promise.all([
    dictCategoryStore.fetchOptions('org_type', false, true),
    dictCategoryStore.fetchOptions('org_type', true, true),
    orgStore.init()
  ])
})

onActivated(async () => {
  if (!orgActivatedOnce.value) {
    orgActivatedOnce.value = true
    return
  }
  await refreshOrgPage()
})

/** 搜索 */
const handleSearch = async () => {
  await orgStore.search(searchForm.value)
}

/** 重置 */
const handleReset = async () => {
  searchForm.value = {
    keyword: '',
    orgType: undefined,
    status: undefined,
    includeChildren: false
  }
  await orgStore.resetSearch()
}

/** 新增 */
const handleAdd = () => {
  currentOrg.value = null
  presetParentId.value = null
  orgStore.openForm(null)
}

/** 新增子组织 */
const handleAddChild = (node: OrgTreeNode) => {
  currentOrg.value = null
  presetParentId.value = node.id
  orgStore.openForm(null)
}

/** 查看成员 */
const handleViewMembers = (node: OrgTreeNode) => {
  selectedOrgId.value = node.id
  selectedOrgName.value = node.orgName
  memberDialogVisible.value = true
}

/** 编辑 */
const handleEdit = async (node: OrgTreeNode) => {
  presetParentId.value = null
  // 从后端获取完整的组织详情
  const orgDetail = await orgStore.getOrgDetail(node.id)
  if (orgDetail) {
    currentOrg.value = JSON.parse(JSON.stringify(orgDetail))
  } else {
    // 如果获取失败，使用树节点数据
    const org: Organization = {
      id: node.id,
      orgCode: node.orgCode,
      orgName: node.orgName,
      orgType: node.orgType,
      parentId: node.parentId,
      level: 0,
      path: '',
      leaderName: node.leaderName || '',
      contactPhone: '',
      address: '',
      status: node.status || 'active',
      sortOrder: 0,
      tenantId: null,
      createdAt: '',
      updatedAt: ''
    }
    currentOrg.value = org
  }
  orgStore.openForm(node.id)
}

/** 删除 */
const handleDelete = (node: OrgTreeNode) => {
  orgStore.deleteOrg(node.id)
}

/** 更新状态 */
const handleUpdateStatus = (node: OrgTreeNode, status: 'active' | 'inactive') => {
  orgStore.updateStatus(node.id, status)
}

/** 表单提交成功 */
const handleFormSuccess = async () => {
  await Promise.all([orgStore.fetchList(), orgStore.fetchStatistics(), orgStore.fetchTree(), orgStore.fetchAllTree()])
}

/** 详情页编辑 */
const handleDetailEdit = (org: Organization) => {
  currentOrg.value = JSON.parse(JSON.stringify(org))
  orgStore.closeDetail()
  orgStore.openForm(org.id)
}

/** 导入 */
const handleImport = () => {
  orgStore.openImportDialog()
}

/** 导出loading */
const exporting = ref(false)

/** 导出 */
const handleExport = async () => {
  exporting.value = true
  try {
    const params = orgStore.searchParams
    await orgStore.handleExport({
      orgType: params.orgType as OrgType | undefined,
      status: params.status as OrgStatus | undefined,
      keyword: params.keyword?.trim() || undefined,
      includeChildren: !!params.includeChildren
    })
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <div class="org-page">
    <!-- 统计卡片 -->
    <OrgStatistics />

    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="4">
          <el-input
            v-model="searchForm.keyword"
            placeholder="组织名称/编码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="3">
          <el-select
            v-model="searchForm.orgType"
            placeholder="全部类型"
            clearable
          >
            <el-option
              v-for="item in orgTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
          >
            <el-option
              v-for="item in ORG_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="3" style="text-align: left">
          <el-checkbox v-model="searchForm.includeChildren">包含下级</el-checkbox>
        </el-col>
        <el-col :span="11" style="text-align: right">
          <el-button class="btn-search" @click="handleSearch">查询</el-button>
          <el-button class="btn-reset" @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 树形列表 -->
    <div class="table-wrapper">
      <div class="table-header">
        <el-button class="btn-add" v-permission="ORG_PERMISSIONS.CREATE" @click="handleAdd">+ 新增组织</el-button>
        <el-button class="btn-import" v-permission="ORG_PERMISSIONS.IMPORT" @click="handleImport">导入</el-button>
        <el-button class="btn-export" v-permission="ORG_PERMISSIONS.EXPORT" :loading="exporting" @click="handleExport">导出</el-button>
      </div>
      <OrgTreeList
        :data="orgStore.filteredTreeData"
        :loading="orgStore.loading"
        :expanded-keys="orgStore.expandedKeys"
        @toggle="orgStore.toggleExpand"
        @add-child="handleAddChild"
        @view-members="handleViewMembers"
        @edit="handleEdit"
        @delete="handleDelete"
        @update-status="handleUpdateStatus"
      />
    </div>

    <!-- 详情弹窗 -->
    <OrgDetail
      v-model="orgStore.detailVisible"
      :org-id="orgStore.currentOrgId"
      @edit="handleDetailEdit"
    />

    <!-- 新增/编辑弹窗 -->
    <OrgForm
      v-model="orgStore.formVisible"
      :org-id="orgStore.currentOrgId"
      :org-data="currentOrg"
      :preset-parent-id="presetParentId"
      @success="handleFormSuccess"
    />

    <!-- 成员弹窗 -->
    <OrgMemberDialog
      v-model="memberDialogVisible"
      :org-id="selectedOrgId"
      :org-name="selectedOrgName"
    />

    <!-- 导入弹窗 -->
    <OrgImportDialog />

    <!-- 导入结果弹窗 -->
    <OrgImportResultDialog />
  </div>
</template>

<style lang="scss" scoped>
.org-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.toolbar {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  margin-bottom: 20px;
  box-shadow: $box-shadow-base;
  flex-shrink: 0;

  .el-input,
  .el-select {
    width: 100%;
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
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.9);

    &:hover {
      background: #E3E7EF;
      border-color: #E3E7EF;
      color: rgba(0, 0, 0, 0.9);
    }
  }

  .btn-import {
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;

    &:hover {
      background: #EEF1FF;
      border-color: #5C75E8;
      color: #5C75E8;
    }
  }

  .btn-export {
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      border-color: #7288FA;
      color: #7288FA;
    }
  }
}

.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  flex: 1;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-header {
  display: flex;
  justify-content: flex-start;
  gap: 8px;
  padding: 16px;
  flex-shrink: 0;

  .btn-add {
    width: 110px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
    color: #fff;

    &:hover {
      background: #5C75E8;
      border-color: #5C75E8;
      color: #fff;
    }
  }

  .btn-import {
    height: 32px;
    width: 58px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;

    &:hover {
      background: #EEF1FF;
      border-color: #5C75E8;
      color: #5C75E8;
    }
  }

  .btn-export {
    height: 32px;
    width: 58px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    &:hover {
      border-color: #7288FA;
      color: #7288FA;
    }
  }
}
</style>
