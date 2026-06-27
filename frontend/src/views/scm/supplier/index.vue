<script setup lang="ts">
import { computed, onActivated, onMounted, ref, watch } from 'vue'
import { Download, Upload } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useSupplierStore } from '@/stores/modules/supplier'
import { SUPPLIER_STATUS_OPTIONS } from '@/constants/supplier'
import SupplierStatistics from '@/components/business/supplier/SupplierStatistics.vue'
import SupplierTable from '@/components/business/supplier/SupplierTable.vue'
import SupplierForm from '@/components/business/supplier/SupplierForm.vue'
import SupplierDetail from '@/components/business/supplier/SupplierDetail.vue'
import SupplierImportDialog from '@/components/business/supplier/SupplierImportDialog.vue'
import SupplierImportResultDialog from '@/components/business/supplier/SupplierImportResultDialog.vue'
import type { Supplier, SupplierQuery } from '@/types/supplier'
import { SUPPLIER_PERMISSIONS } from '@/constants/permission'

const supplierStore = useSupplierStore()

const createSearchForm = (): SupplierQuery => ({
  keyword: supplierStore.searchFormCache.keyword || '',
  status: supplierStore.searchFormCache.status || ''
})

/** 搜索表单 */
const searchForm = ref<SupplierQuery>(createSearchForm())

/** 当前编辑的供应商（深拷贝，避免直接修改原始数据） */
const currentSupplier = ref<Supplier | null>(null)

/** 表单显示状态，关闭时同步清理当前编辑上下文 */
const supplierFormVisible = computed({
  get: () => supplierStore.formVisible,
  set: (visible: boolean) => {
    if (visible) {
      supplierStore.formVisible = true
      return
    }
    supplierStore.closeForm()
    currentSupplier.value = null
  }
})

/** 编辑模式下拿到详情后再挂载表单，避免 Safari 首次渲染丢失选中值 */
const supplierFormReady = computed(() => {
  return !supplierStore.currentId || !!currentSupplier.value
})

/** 审核弹窗 */
const auditVisible = ref(false)
const auditSupplierId = ref<number | null>(null)
const auditForm = ref({
  remark: ''
})
const supplierActivatedOnce = ref(false)

const hasActiveSearchConditions = () => {
  return Boolean(searchForm.value.keyword?.trim() || searchForm.value.status)
}

const refreshListOnReturnIfNeeded = async () => {
  if (!supplierStore.initialized) {
    await supplierStore.init()
    return
  }
  await Promise.all([
    supplierStore.fetchList(),
    supplierStore.fetchStatistics(),
  ])
}

/** 初始化 */
onMounted(async () => {
  searchForm.value = createSearchForm()
  await refreshListOnReturnIfNeeded()
})

onActivated(async () => {
  if (!supplierActivatedOnce.value) {
    supplierActivatedOnce.value = true
  }
  await refreshListOnReturnIfNeeded()
})

watch(
  searchForm,
  (value) => {
    supplierStore.updateSearchFormCache({
      keyword: value.keyword,
      status: value.status || ''
    })
  },
  {
    deep: true
  }
)

/** 搜索 */
const handleSearch = () => {
  supplierStore.search({
    keyword: searchForm.value.keyword,
    status: searchForm.value.status || ''
  })
}

/** 重置 */
const handleReset = () => {
  searchForm.value = { keyword: '', status: '' }
  supplierStore.resetSearch()
}

/** 新增 */
const handleAdd = () => {
  currentSupplier.value = null
  supplierStore.openForm(null)
}

/** 导入 */
const handleImport = () => {
  supplierStore.openImportDialog()
}

/** 导出 */
const handleExport = () => {
  supplierStore.handleExport({
    keyword: searchForm.value.keyword,
    status: searchForm.value.status || ''
  })
}

/** 查看详情 */
const handleDetail = (row: Supplier) => {
  supplierStore.openDetail(row.id)
}

/** 获取用于编辑的最新供应商数据 */
const getEditSupplier = async (supplier: Supplier): Promise<Supplier> => {
  const detail = await supplierStore.getSupplierDetail(supplier.id)
  return JSON.parse(JSON.stringify(detail || supplier))
}

/** 编辑 */
const handleEdit = async (row: Supplier) => {
  currentSupplier.value = null
  currentSupplier.value = await getEditSupplier(row)
  supplierStore.openForm(row.id)
}

/** 审核 */
const handleAudit = (row: Supplier) => {
  auditSupplierId.value = row.id
  auditForm.value = { remark: '' }
  auditVisible.value = true
}

/** 关闭审核弹窗 */
const closeAuditDialog = () => {
  auditVisible.value = false
  auditSupplierId.value = null
  auditForm.value = { remark: '' }
}

/** 审核通过 */
const handleAuditApprove = async () => {
  if (!auditSupplierId.value) return
  const ok = await supplierStore.auditSupplier(auditSupplierId.value, 'active', auditForm.value.remark)
  if (ok) {
    closeAuditDialog()
  }
}

/** 审核驳回 */
const handleAuditReject = async () => {
  if (!auditSupplierId.value) return
  const ok = await supplierStore.auditSupplier(
    auditSupplierId.value,
    'rejected',
    auditForm.value.remark
  )
  if (ok) {
    closeAuditDialog()
  }
}

/** 删除 */
const handleDelete = (row: Supplier) => {
  supplierStore.deleteSupplier(row.id)
}

/** 启用 */
const handleEnable = async (row: Supplier) => {
  await supplierStore.enableSupplier(row.id)
}

/** 采集注销原因 */
const requestCancelReason = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请填写注销原因', '注销供应商', {
      inputType: 'textarea',
      inputPlaceholder: '请输入注销原因',
      inputValidator: (input) => input.trim() ? true : '注销原因不能为空',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
    return value.trim()
  } catch {
    return null
  }
}

/** 注销 */
const handleCancel = async (row: Supplier) => {
  const reason = await requestCancelReason()
  if (!reason) return
  await supplierStore.cancelSupplier(row.id, { reason })
}

/** 分页改变 */
const handlePageChange = (page: number) => {
  supplierStore.changePage(page)
}

/** 每页条数改变 */
const handleSizeChange = (size: number) => {
  supplierStore.changePageSize(size)
}

/** 详情页点击编辑 */
const handleDetailEdit = async (supplier: Supplier) => {
  currentSupplier.value = await getEditSupplier(supplier)
  supplierStore.closeDetail()
  supplierStore.openForm(supplier.id)
}
</script>

<template>
  <div class="supplier-page">
    <!-- 统计卡片 -->
    <SupplierStatistics />

    <!-- 搜索工具栏 -->
    <div class="toolbar">
      <el-row :gutter="10" align="middle">
        <el-col :span="8">
          <el-input
            v-model="searchForm.keyword"
            placeholder="请输入供应商名称/编码/统一社会信用代码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="item in SUPPLIER_STATUS_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button @click="handleImport">
            <el-icon><Upload /></el-icon>
            导入
          </el-button>
          <el-button :loading="supplierStore.exportLoading" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <el-button type="primary" v-permission="SUPPLIER_PERMISSIONS.CREATE" @click="handleAdd">+ 新增供应商</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 数据表格 -->
    <SupplierTable
      :data="supplierStore.list"
      :loading="supplierStore.loading"
      @detail="handleDetail"
      @audit="handleAudit"
      @edit="handleEdit"
      @enable="handleEnable"
      @cancel="handleCancel"
      @delete="handleDelete"
    />

    <!-- 分页 -->
    <div class="pagination">
      <span class="total">共 {{ supplierStore.total }} 条</span>
      <el-pagination
        v-model:current-page="supplierStore.pageNum"
        v-model:page-size="supplierStore.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="supplierStore.total"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <SupplierDetail
      v-model="supplierStore.detailVisible"
      :supplier-id="supplierStore.currentId"
      @edit="handleDetailEdit"
    />

    <!-- 新增/编辑弹窗 -->
    <SupplierForm
      v-if="supplierFormVisible && supplierFormReady"
      :key="`supplier-form-${supplierStore.currentId ?? 'create'}`"
      v-model="supplierFormVisible"
      :supplier-id="supplierStore.currentId"
      :supplier-data="currentSupplier"
    />

    <SupplierImportDialog />

    <SupplierImportResultDialog />

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="auditVisible"
      title="审核供应商"
      width="520px"
      :close-on-click-modal="false"
      @close="closeAuditDialog"
    >
      <el-form :model="auditForm" label-width="90px">
        <el-form-item label="审核意见">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="4"
            placeholder="请输入审核意见（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeAuditDialog">取消</el-button>
        <el-button type="danger" v-permission="SUPPLIER_PERMISSIONS.APPROVE" @click="handleAuditReject">驳回</el-button>
        <el-button type="success" v-permission="SUPPLIER_PERMISSIONS.APPROVE" @click="handleAuditApprove">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.supplier-page {
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
}

.pagination {
  background: $bg-white;
  padding: 20px;
  border-radius: $border-radius-large;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;

  .total {
    color: $text-regular;
    font-size: 14px;
  }
}
</style>
