<script setup lang="ts">
import { computed, ref, onActivated, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { useWarehouseStore } from '@/stores/modules/warehouse'
import { WAREHOUSE_STATUS_OPTIONS } from '@/constants/warehouse'
import { useDictCategoryStore } from '@/stores/modules/dict-category'
import { mapDictOptions } from '@/utils/dict-category'
import WarehouseStatistics from '@/components/business/warehouse/WarehouseStatistics.vue'
import WarehouseTable      from '@/components/business/warehouse/WarehouseTable.vue'
import WarehouseForm       from '@/components/business/warehouse/WarehouseForm.vue'
import WarehouseDetail     from '@/components/business/warehouse/WarehouseDetail.vue'
import WarehouseImportDialog from '@/components/business/warehouse/WarehouseImportDialog.vue'
import WarehouseExportDialog from '@/components/business/warehouse/WarehouseExportDialog.vue'
import type { Warehouse, WarehouseImportTarget } from '@/types/warehouse'
import { WAREHOUSE_PERMISSIONS } from '@/constants/permission'

const store = useWarehouseStore()
const dictCategoryStore = useDictCategoryStore()
const warehouseActivatedOnce = ref(false)

const searchForm = ref({
  warehouseName: '',
  warehouseType: undefined as string | undefined,
  status: undefined as string | undefined,
})

const currentWarehouse = ref<Warehouse | null>(null)

const warehouseTypeOptions = computed(() => mapDictOptions(
  dictCategoryStore.getCachedOptions('warehouse_type')
))

const syncSearchFormFromStore = () => {
  searchForm.value = {
    warehouseName: store.searchParams.warehouseName ?? '',
    warehouseType: store.searchParams.warehouseType,
    status: store.searchParams.status,
  }
}

const refreshWarehousePage = async () => {
  await Promise.all([
    dictCategoryStore.fetchOptions('warehouse_type', false, true),
    dictCategoryStore.fetchOptions('warehouse_type', true, true),
    store.fetchList(),
    store.fetchStatistics(),
  ])
}

onMounted(async () => {
  syncSearchFormFromStore()
  await Promise.all([
    dictCategoryStore.fetchOptions('warehouse_type', false, true),
    dictCategoryStore.fetchOptions('warehouse_type', true, true),
    store.init(),
  ])
})

onActivated(async () => {
  if (!warehouseActivatedOnce.value) {
    warehouseActivatedOnce.value = true
    return
  }
  await refreshWarehousePage()
})

const handleSearch = () => store.search({
  warehouseName: searchForm.value.warehouseName || undefined,
  warehouseType: searchForm.value.warehouseType,
  status: searchForm.value.status,
})

const handleReset = () => {
  searchForm.value = { warehouseName: '', warehouseType: undefined, status: undefined }
  store.resetSearch()
}

const handleAdd = () => {
  currentWarehouse.value = null
  store.openForm(null)
}

const chooseTarget = async (actionLabel: '导入' | '导出'): Promise<WarehouseImportTarget | null> => {
  try {
    await ElMessageBox.confirm(`请选择${actionLabel}目标`, `${actionLabel}目标`, {
      type: 'info',
      distinguishCancelAndClose: true,
      confirmButtonText: '仓库',
      cancelButtonText: '仓位',
      closeOnClickModal: false,
      closeOnPressEscape: false,
    })
    return 'warehouse'
  } catch (error) {
    if (error === 'cancel') {
      return 'location'
    }
    return null
  }
}

const handleImport = async () => {
  const target = await chooseTarget('导入')
  if (!target) {
    return
  }
  store.openImportDialog(target)
}

const handleExport = async () => {
  const target = await chooseTarget('导出')
  if (!target) {
    return
  }
  store.openExportDialog(target)
}

const handleEdit = (row: Warehouse) => {
  currentWarehouse.value = JSON.parse(JSON.stringify(row))
  store.openForm(row.id)
}

const handleDetail   = (row: Warehouse) => store.openDetail(row.id)
const handleEditFromDetail = () => {
  if (store.currentWarehouseId) {
    const row = store.list.find((w: Warehouse) => w.id === store.currentWarehouseId)
    if (row) currentWarehouse.value = JSON.parse(JSON.stringify(row))
    store.openForm(store.currentWarehouseId)
  }
}
const handleDelete   = (row: Warehouse) => store.deleteWarehouse(row.id)

const handleFormSuccess = () => {
  store.fetchList()
  store.fetchStatistics()
}
</script>

<template>
  <div class="warehouse-page">
    <!-- 统计卡片 -->
    <WarehouseStatistics />

    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="6">
          <el-input v-model="searchForm.warehouseName" placeholder="仓库名称/编码"
                    clearable @keyup.enter="handleSearch" />
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.warehouseType" placeholder="全部类型" clearable>
            <el-option v-for="t in warehouseTypeOptions" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable>
            <el-option v-for="s in WAREHOUSE_STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-col>
        <el-col :span="10" style="text-align:right">
          <el-button class="btn-search" @click="handleSearch">查询</el-button>
          <el-button class="btn-reset" @click="handleReset">重置</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 数据表格 -->
    <div class="table-wrapper">
      <div class="table-header">
        <el-button class="btn-search" v-permission="WAREHOUSE_PERMISSIONS.CREATE" @click="handleAdd">+ 新增仓库</el-button>
        <el-button class="btn-import" @click="handleImport">导入</el-button>
        <el-button class="btn-export" @click="handleExport">导出</el-button>
        <el-button class="btn-help">帮助说明</el-button>
        <el-button class="btn-more"><span class="dots">⋮</span>更多</el-button>
      </div>
      <WarehouseTable
        :data="store.list"
        :loading="store.loading"
        @detail="handleDetail"
        @edit="handleEdit"
        @delete="handleDelete"
      />
      <div class="pagination">
        <span class="total">共 {{ store.total }} 项数据</span>
        <el-pagination
          v-model:current-page="store.pageNum"
          v-model:page-size="store.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="store.total"
          layout="sizes, prev, pager, next"
          @current-change="store.changePage"
          @size-change="store.changePageSize"
        />
      </div>
    </div>

    <!-- 详情弹窗（含仓位管理） -->
    <WarehouseDetail
      v-model="store.detailVisible"
      :warehouse-id="store.currentWarehouseId"
      @edit="handleEditFromDetail"
    />

    <!-- 新增/编辑弹窗 -->
    <WarehouseForm
      v-model="store.formVisible"
      :warehouse-id="store.currentWarehouseId"
      :warehouse-data="currentWarehouse"
      @success="handleFormSuccess"
    />

    <WarehouseImportDialog :target="store.importTarget" :visible="store.importDialogVisible" @update:visible="(value) => value ? store.openImportDialog(store.importTarget) : store.closeImportDialog()" />
    <WarehouseExportDialog :target="store.exportTarget" :visible="store.exportDialogVisible" :export-params="store.exportParams" @update:visible="(value) => value ? store.openExportDialog(store.exportTarget) : store.closeExportDialog()" />
  </div>
</template>

<style lang="scss" scoped>
.warehouse-page {
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

  .el-input, .el-select { width: 100%; }

  .btn-search {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    color: #fff;
  }

  .btn-reset {
    width: 60px;
    height: 32px;
    padding: 5px 16px;
    background: #F2F4F8;
    border-color: #F2F4F8;
    border-radius: 6px;
    color: rgba(0, 0, 0, 0.9);
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

  .btn-search {
    width: 110px;
    height: 32px;
    padding: 5px 16px;
    background: #7288FA;
    border-color: #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
    color: #fff;
  }

  .btn-import {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #7288FA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #7288FA;
  }

  .btn-export {
    width: 58px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;
  }

  .btn-help {
    width: 84px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;
  }

  .btn-more {
    width: 80px;
    height: 32px;
    padding: 5px 16px;
    background: #FFFFFF;
    border: 1px solid #BEC0CA;
    border-radius: 6px;
    box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
    color: #606266;

    .dots {
      margin-right: 5px;
      letter-spacing: 1px;
      color: rgba(0, 0, 0, 0.85);
    }
  }
}
.pagination {
  padding: 16px 24px;
  background: #FFFFFF;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-shrink: 0;

  .total {
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: rgba(0, 0, 0, 0.6);
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
    background: #FFFFFF;
    border: 1px solid #DCDCDC;
    border-radius: 3px;
    color: #000000E5;
    margin-left: 8px;
  }

  :deep(.el-pagination .el-pager li + li) {
    margin-left: 8px;
  }
}
</style>
