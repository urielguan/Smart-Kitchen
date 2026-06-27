<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import type { Organization } from '@/types/org'
import { ORG_TYPE_MAP, ORG_STATUS_MAP } from '@/constants/org'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { buildDictLabelMap } from '@/utils/dict-category'
import { ORG_PERMISSIONS } from '@/constants/permission'

interface Props {
  data: Organization[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})
const dictCategoryStore = useDictCategoryStore()

const emit = defineEmits<{
  detail: [org: Organization]
  edit: [org: Organization]
  delete: [org: Organization]
  'update-status': [org: Organization, status: 'active' | 'inactive']
}>()

/** 表格容器引用 */
const tableContainerRef = ref<HTMLElement | null>(null)

/** 表格高度 */
const tableHeight = ref<number | undefined>(undefined)

/** ResizeObserver 实例 */
let resizeObserver: ResizeObserver | null = null

/** 计算表格高度 */
const updateTableHeight = () => {
  if (tableContainerRef.value) {
    tableHeight.value = tableContainerRef.value.clientHeight
  }
}

const orgTypeLabelMap = computed(() => buildDictLabelMap(
  dictCategoryStore.getCachedOptions('org_type', true),
  Object.fromEntries(Object.entries(ORG_TYPE_MAP).map(([key, value]) => [key, value.label]))
))

const getOrgTypeTagType = (orgType: string) => ORG_TYPE_MAP[orgType]?.tagType || 'info'

/** 详情 */
const handleDetail = (row: Organization) => emit('detail', row)

/** 编辑 */
const handleEdit = (row: Organization) => emit('edit', row)

/** 删除 */
const handleDelete = (row: Organization) => emit('delete', row)

/** 更新状态 */
const handleUpdateStatus = (row: Organization, status: 'active' | 'inactive') => {
  emit('update-status', row, status)
}

onMounted(() => {
  dictCategoryStore.fetchOptions('org_type', true)
  if (tableContainerRef.value) {
    resizeObserver = new ResizeObserver(updateTableHeight)
    resizeObserver.observe(tableContainerRef.value)
    updateTableHeight()
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>

<template>
  <div ref="tableContainerRef" class="table-container">
    <el-table
      :data="data"
      :loading="loading"
      stripe
      :height="tableHeight"
    >
      <el-table-column prop="orgName" label="组织名称" min-width="150">
        <template #default="{ row }">
          <strong>{{ row.orgName }}</strong>
        </template>
      </el-table-column>

      <el-table-column prop="orgCode" label="组织编码" min-width="130" />

      <el-table-column prop="orgType" label="组织类型" min-width="100">
        <template #default="{ row }">
          <el-tag :type="getOrgTypeTagType(row.orgType)" size="small">
            {{ orgTypeLabelMap[row.orgType] || row.orgType }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="parentName" label="上级组织" min-width="140">
        <template #default="{ row }">
          {{ row.parentName || '—' }}
        </template>
      </el-table-column>

      <el-table-column prop="level" label="层级" min-width="80" align="center">
        <template #default="{ row }">
          第 {{ row.level }} 级
        </template>
      </el-table-column>

      <el-table-column prop="leaderName" label="负责人" min-width="100">
        <template #default="{ row }">
          {{ row.leaderName || '—' }}
        </template>
      </el-table-column>

      <el-table-column prop="contactPhone" label="联系电话" min-width="130">
        <template #default="{ row }">
          {{ row.contactPhone || '—' }}
        </template>
      </el-table-column>

      <el-table-column prop="status" label="状态" min-width="90">
        <template #default="{ row }">
          <el-tag :type="ORG_STATUS_MAP[row.status]?.tagType" size="small">
            {{ ORG_STATUS_MAP[row.status]?.label }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="createdAt" label="创建时间" min-width="170">
        <template #default="{ row }">
          {{ row.createdAt || '—' }}
        </template>
      </el-table-column>

      <el-table-column label="操作" min-width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="primary" link v-permission="ORG_PERMISSIONS.EDIT" @click="handleEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'active'"
            type="warning"
            link
            v-permission="ORG_PERMISSIONS.STATUS"
            @click="handleUpdateStatus(row, 'inactive')"
          >停用</el-button>
          <el-button
            v-else
            type="success"
            link
            v-permission="ORG_PERMISSIONS.STATUS"
            @click="handleUpdateStatus(row, 'active')"
          >启用</el-button>
          <el-button type="danger" link v-permission="ORG_PERMISSIONS.DELETE" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped>
.table-container {
  flex: 1;
  min-height: 0;
  background: $bg-white;
  border-radius: $border-radius-large;
  box-shadow: $box-shadow-base;
  overflow: hidden;
}
</style>
