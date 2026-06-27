<template>
  <div class="certificate-manager">
    <!-- 数据表格 + 分页 -->
    <div class="table-wrapper">
      <div class="table-header">
        <el-input
          v-model="searchKeyword"
          placeholder="员工姓名/编号/健康证编号"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 180px">
          <el-option
            v-for="(item, key) in CERTIFICATE_STATUS_MAP"
            :key="key"
            :label="item.label"
            :value="key"
          />
        </el-select>
        <el-checkbox v-model="showLeftEmployees" class="left-employee-checkbox" @change="handleSearch">
          显示离职员工
        </el-checkbox>
        <el-button class="btn-query" @click="handleSearch">查询</el-button>
        <el-button class="btn-reset" @click="handleReset">重置</el-button>
        <div style="flex: 1" />
        <el-button class="btn-refresh" @click="handleRefreshStatus">
          刷新
        </el-button>
        <el-button class="btn-export" v-permission="MORNING_CHECK_PERMISSIONS.CERT_EXPORT" :loading="exporting" @click="handleExport">
          导出
        </el-button>
        <el-button class="btn-add" v-permission="MORNING_CHECK_PERMISSIONS.CERT_CREATE" @click="openForm()">
          <el-icon><Plus /></el-icon>
          录入健康证
        </el-button>
      </div>
      <div ref="tableContainerRef" class="table-container">
        <el-table :data="certList" v-loading="loading" :height="tableHeight">
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="employeeName" label="员工姓名" min-width="100" />
          <el-table-column prop="certificateNo" label="健康证编号" min-width="160" />
          <el-table-column prop="issuingAuthority" label="发证机构" min-width="120" show-overflow-tooltip />
          <el-table-column prop="issueDate" label="发证日期" min-width="110" />
          <el-table-column prop="expiryDate" label="到期日期" min-width="110" />
          <el-table-column label="剩余天数" width="100" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.remainingDays < 0 ? 'danger' : row.remainingDays <= 30 ? 'warning' : 'success'"
                size="small"
              >
                {{ row.remainingDays < 0 ? '已过期' : `${row.remainingDays}天` }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="CERTIFICATE_STATUS_MAP[row.status]?.type || 'info'" size="small">
                {{ CERTIFICATE_STATUS_MAP[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right" class-name="action-col">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link type="primary" v-permission="MORNING_CHECK_PERMISSIONS.CERT_EDIT" @click="openForm(row)">编辑</el-button>
              <el-button link type="danger" v-permission="MORNING_CHECK_PERMISSIONS.CERT_DELETE" @click="handleDelete(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <span class="total">共 {{ total }} 项数据</span>
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          :pager-count="7"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 健康证录入/编辑弹窗 -->
    <el-dialog
      v-model="formVisible"
      :close-on-click-modal="false"
      :show-close="false"
      align-center
      destroy-on-close
      class="cert-form-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">{{ editingCert ? '编辑健康证' : '录入健康证' }}</span>
          <div class="close-btn" @click="formVisible = false">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
              <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="员工：" prop="employeeId">
          <el-input
            v-if="editingCert"
            :model-value="editingCert.employeeName"
            disabled
          />
          <el-select
            v-else
            v-model="formData.employeeId"
            placeholder="搜索员工姓名/工号/手机号"
            filterable
            remote
            :remote-method="searchEmployees"
            :loading="searchingEmployee"
            style="width: 100%"
            @visible-change="handleEmployeeDropdownVisible"
          >
            <el-option
              v-for="emp in employeeOptions"
              :key="emp.id"
              :label="`${emp.name}${emp.code ? ' (' + emp.code + ')' : ''}`"
              :value="emp.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="健康证编号：" prop="certificateNo">
          <el-input v-model="formData.certificateNo" placeholder="请输入健康证编号" maxlength="50" :show-word-limit="true" />
        </el-form-item>
        <el-form-item label="发证机构：" prop="issuingAuthority">
          <el-input v-model="formData.issuingAuthority" placeholder="请输入发证机构" maxlength="100" :show-word-limit="true" />
        </el-form-item>
        <el-form-item label="发证日期：" prop="issueDate">
          <el-date-picker v-model="formData.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="disableIssueDate" />
        </el-form-item>
        <el-form-item label="到期日期：" prop="expiryDate">
          <el-date-picker v-model="formData.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" :disabled-date="disableExpiryDate" />
        </el-form-item>
        <el-form-item label="预警天数：">
          <el-input-number v-model="formData.warningDays" :min="7" :max="90" :precision="0" :step="1" />
          <span class="form-tip">到期前N天开始预警</span>
        </el-form-item>
        <el-form-item label="备注：">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="备注信息" maxlength="500" :show-word-limit="true" />
        </el-form-item>
        <el-form-item label="电子版照片：">
          <div class="image-upload-wrapper">
            <el-upload
              class="image-uploader"
              action="#"
              :show-file-list="false"
              :before-upload="handleImageUpload"
              accept="image/*"
            >
              <div v-if="formData.certificateImages" class="image-preview">
                <img :src="getImageUrl(formData.certificateImages)" alt="健康证照片" />
                <div class="image-actions">
                  <span @click.stop="handleImageRemove">删除</span>
                </div>
              </div>
              <div v-else class="image-placeholder">
                <el-icon><Plus /></el-icon>
                <span>上传照片</span>
              </div>
            </el-upload>
            <div class="image-tip">支持 JPG、PNG、GIF、WebP 格式，最大 10MB</div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="formVisible = false">取消</button>
          <button class="btn-save" :disabled="saving || uploading" @click="handleSubmit">
            {{ (saving || uploading) ? '保存中...' : '确认保存' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <!-- 健康证详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :close-on-click-modal="false"
      :show-close="false"
      align-center
      destroy-on-close
      class="cert-detail-dialog"
    >
      <template #header>
        <div class="dialog-header">
          <span class="dialog-title">健康证详情</span>
          <div class="close-btn" @click="detailVisible = false">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
              <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
        </div>
      </template>

      <div v-if="detailData" class="detail-body">
        <div class="section-title info-section-title">
          <span class="title-bar" />
          <span>基础信息</span>
        </div>
        <div class="info-table">
          <div class="info-label">员工姓名</div>
          <div class="info-value">{{ detailData.employeeName }}</div>
          <div class="info-label">健康证编号</div>
          <div class="info-value">{{ detailData.certificateNo || '-' }}</div>
          <div class="info-label">发证机构</div>
          <div class="info-value">{{ detailData.issuingAuthority || '-' }}</div>
          <div class="info-label">状态</div>
          <div class="info-value">
            <el-tag :type="CERTIFICATE_STATUS_MAP[detailData.status]?.type || 'info'" size="small">
              {{ CERTIFICATE_STATUS_MAP[detailData.status]?.label || detailData.status }}
            </el-tag>
          </div>
          <div class="info-label">发证日期</div>
          <div class="info-value">{{ detailData.issueDate || '-' }}</div>
          <div class="info-label">到期日期</div>
          <div class="info-value">{{ detailData.expiryDate || '-' }}</div>
          <div class="info-label">剩余天数</div>
          <div class="info-value">
            <el-tag
              :type="detailData.remainingDays < 0 ? 'danger' : detailData.remainingDays <= 30 ? 'warning' : 'success'"
              size="small"
            >
              {{ detailData.remainingDays < 0 ? '已过期' : `${detailData.remainingDays}天` }}
            </el-tag>
          </div>
          <div class="info-label">预警天数</div>
          <div class="info-value">{{ detailData.warningDays || 30 }} 天</div>
          <div class="info-label">备注</div>
          <div class="info-value info-value--span3">{{ detailData.remark || '-' }}</div>
        </div>

        <template v-if="detailData.certificateImages">
          <div class="section-title info-section-title" style="margin-top: 20px">
            <span class="title-bar" />
            <span>电子版照片</span>
          </div>
          <div class="detail-image-wrapper">
            <el-image
              :src="getImageUrl(detailData.certificateImages)"
              :preview-src-list="[getImageUrl(detailData.certificateImages)]"
              fit="contain"
              class="detail-image"
            />
          </div>
        </template>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="detailVisible = false">关闭</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Download, WarningFilled } from '@element-plus/icons-vue'
import { CERTIFICATE_STATUS_MAP } from '@/constants/health-check'
import {
  getCertificatePage,
  saveHealthCertificate,
  deleteHealthCertificate,
  refreshCertificateStatus,
  getCertificateList,
  getCertificateDetail,
  uploadCertificateImage,
  exportCertificates,
} from '@/api/modules/health-check'
import type { HealthCertificate, HealthCertificatePayload, HealthCertificateQuery } from '@/types/health-check'
import { MORNING_CHECK_PERMISSIONS } from '@/constants/permission'
import { employeeApi } from '@/api/modules/employee'
import { getImageUrl } from '@/utils'

const emit = defineEmits<{ changed: [] }>()

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const exporting = ref(false)
const certList = ref<HealthCertificate[]>([])
const filterStatus = ref('')
const searchKeyword = ref('')
const showLeftEmployees = ref(false)
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

// 表格自适应高度
const tableContainerRef = ref<HTMLElement | null>(null)
const tableHeight = ref<number | undefined>(undefined)
let resizeObserver: ResizeObserver | null = null

const updateTableHeight = () => {
  if (tableContainerRef.value) {
    tableHeight.value = tableContainerRef.value.clientHeight
  }
}

// 员工搜索
const employeeOptions = ref<{ id: number; name: string; code: string }[]>([])
const searchingEmployee = ref(false)

// 表单
const formVisible = ref(false)
const editingCert = ref<HealthCertificate | null>(null)
const formRef = ref()
const formData = ref<HealthCertificatePayload>({
  employeeId: undefined,
  certificateNo: '',
  issuingAuthority: '',
  issueDate: '',
  expiryDate: '',
  certificateImages: '',
  warningDays: 30,
  remark: '',
})

const formRules = {
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  certificateNo: [{ required: true, message: '请输入健康证编号', trigger: 'blur' }],
  issueDate: [
    { required: true, message: '请选择发证日期', trigger: 'change' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && formData.value.expiryDate && value > formData.value.expiryDate) {
          callback(new Error('发证日期不能晚于到期日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
  expiryDate: [
    { required: true, message: '请选择到期日期', trigger: 'change' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value && formData.value.issueDate && value < formData.value.issueDate) {
          callback(new Error('到期日期不能早于发证日期'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ],
}

// 详情
const detailVisible = ref(false)
const detailData = ref<HealthCertificate | null>(null)

function disableIssueDate(date: Date): boolean {
  if (!formData.value.expiryDate) return false
  return date.getTime() > new Date(formData.value.expiryDate).getTime()
}

function disableExpiryDate(date: Date): boolean {
  if (!formData.value.issueDate) return false
  return date.getTime() < new Date(formData.value.issueDate).getTime()
}

onMounted(() => {
  loadList()
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

function handleSearch() {
  pageNum.value = 1
  loadList()
}

function handleReset() {
  searchKeyword.value = ''
  filterStatus.value = ''
  pageNum.value = 1
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    const query: HealthCertificateQuery = {
      keyword: searchKeyword.value || undefined,
      status: filterStatus.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      showLeftEmployees: showLeftEmployees.value,
    }
    const res = await getCertificatePage(query)
    if (res.code === 'SUCCESS' && res.data) {
      certList.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number) => {
  pageNum.value = page
  loadList()
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  pageNum.value = 1
  loadList()
}

function handleEmployeeDropdownVisible(visible: boolean) {
  if (visible && employeeOptions.value.length === 0) {
    searchEmployees('')
  }
}

async function searchEmployees(query: string) {
  searchingEmployee.value = true
  try {
    const res = await employeeApi.getList({ keyword: query || undefined, pageNum: 1, pageSize: 20, status: 'active', accountStatus: 'active' })
    if (res.code === 'SUCCESS' && res.data?.list) {
      employeeOptions.value = res.data.list.map((emp: any) => ({
        id: emp.id,
        name: emp.realName,
        code: emp.employeeNo || '',
      }))
    }
  } catch {
    employeeOptions.value = []
  } finally {
    searchingEmployee.value = false
  }
}

function openForm(cert?: HealthCertificate) {
  editingCert.value = cert || null
  if (cert) {
    formData.value = {
      employeeId: cert.employeeId,
      certificateNo: cert.certificateNo,
      issuingAuthority: cert.issuingAuthority || '',
      issueDate: cert.issueDate,
      expiryDate: cert.expiryDate,
      certificateImages: cert.certificateImages || '',
      warningDays: cert.warningDays || 30,
      remark: cert.remark || '',
    }
  } else {
    formData.value = {
      employeeId: undefined,
      certificateNo: '',
      issuingAuthority: '',
      issueDate: '',
      expiryDate: '',
      certificateImages: '',
      warningDays: 30,
      remark: '',
    }
  }
  formVisible.value = true
}

async function handleImageUpload(file: File) {
  const isImage = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'].includes(file.type)
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  try {
    uploading.value = true
    const res = await uploadCertificateImage(file)
    if (res.code === 'SUCCESS' && res.data) {
      formData.value.certificateImages = res.data.imageUrl
      ElMessage.success('图片上传成功')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
  return false
}

function handleImageRemove() {
  formData.value.certificateImages = ''
}

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  // 录入模式下，检查该员工是否已有健康证
  if (!editingCert.value && formData.value.employeeId) {
    try {
      const checkRes = await getCertificateList({})
      if (checkRes.code === 'SUCCESS' && checkRes.data) {
        const existing = checkRes.data.find((c: HealthCertificate) => c.employeeId === formData.value.employeeId)
        if (existing) {
          try {
            await ElMessageBox.confirm(
              `员工「${existing.employeeName}」已有健康证（编号：${existing.certificateNo}），保存将覆盖原有记录，是否继续？`,
              '提示',
              { confirmButtonText: '继续覆盖', cancelButtonText: '取消', type: 'warning' }
            )
          } catch {
            return
          }
        }
      }
    } catch {
      // 检查失败不阻断提交
    }
  }

  saving.value = true
  try {
    const res = await saveHealthCertificate(formData.value)
    if (res.code === 'SUCCESS') {
      ElMessage.success(editingCert.value ? '更新成功' : '录入成功')
      formVisible.value = false
      pageNum.value = 1
      emit('changed')
      await loadList()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function openDetail(row: HealthCertificate) {
  try {
    const res = await getCertificateDetail(row.id)
    if (res.code === 'SUCCESS' && res.data) {
      detailData.value = res.data
      detailVisible.value = true
    }
  } catch {
    // 拦截器已展示错误
  }
}

async function handleDelete(id: number) {
  try {
    await ElMessageBox({
      title: '删除健康证',
      message: () =>
        h('div', { class: 'cert-confirm' }, [
          h('div', { class: 'cert-confirm__content' }, [
            h(WarningFilled, { class: 'cert-confirm__icon' }),
            h('div', { class: 'cert-confirm__text' }, [
              h('div', { class: 'cert-confirm__title' }, '删除健康证'),
              h('div', { class: 'cert-confirm__description' }, '确认删除该健康证？删除后不可恢复。'),
            ]),
          ]),
        ]),
      customClass: 'material-message-box',
      showClose: false,
      closeOnClickModal: false,
      closeOnPressEscape: true,
      showCancelButton: true,
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    const res = await deleteHealthCertificate(id)
    if (res.code === 'SUCCESS') {
      ElMessage.success('删除成功')
      pageNum.value = 1
      emit('changed')
      await loadList()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
    // 拦截器已展示错误消息
  }
}

async function handleExport() {
  exporting.value = true
  try {
    await exportCertificates({
      keyword: searchKeyword.value || undefined,
      status: filterStatus.value || undefined,
      showLeftEmployees: showLeftEmployees.value,
    })
  } catch (error: any) {
    ElMessage.error(error.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

async function handleRefreshStatus() {
  try {
    const res = await refreshCertificateStatus()
    if (res.code === 'SUCCESS') {
      ElMessage.success(`状态刷新完成，更新了 ${res.data || 0} 条记录`)
      emit('changed')
      await loadList()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '刷新失败')
  }
}
</script>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.cert-form-dialog.el-dialog {
  width: 500px;
}

.cert-detail-dialog.el-dialog {
  width: 758px;
}

.cert-form-dialog.el-dialog,
.cert-detail-dialog.el-dialog {
  background: #FFFFFF;
  --el-dialog-border-radius: 12px;
  --el-dialog-padding-primary: 0;
  border-radius: 12px !important;
  overflow: hidden;
  padding: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  margin: auto !important;
}

.cert-form-dialog.el-dialog .el-dialog__header,
.cert-detail-dialog.el-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.cert-form-dialog.el-dialog .el-dialog__body {
  padding: 16px 24px 24px 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.cert-detail-dialog.el-dialog .el-dialog__body {
  height: 480px;
  padding: 16px 24px 24px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cert-form-dialog.el-dialog .el-dialog__footer,
.cert-detail-dialog.el-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
.certificate-manager {
  display: flex;
  flex-direction: column;
}

/* ---- 表格头部操作栏 ---- */
.table-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  flex-shrink: 0;

  :deep(.el-button) {
    margin-left: 0;
  }

  .btn-query {
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
}

.btn-add {
  width: auto;
  height: 32px;
  padding: 5px 16px;
  background: #7288FA;
  border: 1px solid #7288FA;
  border-radius: 6px;
  box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;

  &:hover, &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }
}

.btn-refresh {
  width: 58px;
  height: 32px;
  padding: 5px 16px;
  background: #FFFFFF;
  border: 1px solid #7288FA;
  border-radius: 6px;
  box-shadow: 0px 2px 0px rgba(0, 0, 0, 0.016);
  color: #7288FA;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;

  &:hover, &:focus {
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
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;

  &:hover, &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

/* ---- 表格容器 ---- */
.table-wrapper {
  background: #FFFFFF;
  border-radius: 8px;
  min-height: 400px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.table-container {
  flex: 1;
  min-height: 0;
  background: #FFFFFF;
  padding: 0 16px;
  overflow: hidden;
}

:deep(.el-table) {
  --el-table-index-cell-vertical-align: middle;
  --el-table-border-color: #E7E7E7;
  --el-table-row-height: 46px;

  .el-table__cell {
    padding-left: 0;
    padding-right: 0;
    font-family: 'PingFang SC', sans-serif;
    font-weight: 400;
    font-size: 14px;
    line-height: 22px;
    color: #000000E5;
  }
}

:deep(.el-table__body tr) {
  height: 46px;
  border-bottom: 1px solid #E7E7E7;

  td {
    height: 46px;
  }

  &:nth-child(odd) td {
    background-color: #FFFFFF;
  }

  &:nth-child(even) td {
    background-color: #F5F9FF;
  }
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table thead th) {
  font-family: 'PingFang SC', sans-serif;
  font-weight: 400;
  font-size: 14px;
  line-height: 22px;
  color: #00000066;
  background-color: #F5F9FF !important;
  border-bottom: 1px solid #E7E7E7;
}

:deep(.table-container .el-tag--success) {
  background: #E3F9E9;
  border: 1px solid #2BA471;
  border-radius: 3px;
  color: #2BA471;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.table-container .el-tag--warning) {
  background: #FFF1E9;
  border: 1px solid #E37318;
  border-radius: 3px;
  color: #E37318;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.table-container .el-tag--danger) {
  background: #FFF0ED;
  border: 1px solid #D54941;
  border-radius: 3px;
  color: #D54941;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

:deep(.table-container .el-tag--info) {
  background: #F4F4F5;
  border: 1px solid #909399;
  border-radius: 3px;
  color: #909399;
  height: 24px;
  padding: 2px 8px;
  line-height: 20px;
}

/* 详情弹窗标签样式（与列表不同） */
:deep(.detail-body .el-tag) {
  border-radius: 5px;
  height: 24px;
  padding: 0 8px;
  line-height: 22px;
  display: inline-flex;
  align-items: center;
}

:deep(.el-button--primary.is-link) {
  color: #5570F1;
  &:hover { color: #2E45D6; }
  &:focus { color: #5570F1; }
}

:deep(.el-button--danger.is-link) {
  color: #FF7474;
  &:hover { color: #FF3D3D; }
  &:focus { color: #FF7474; }
}

:deep(.action-col .cell) {
  overflow: visible;
}

/* ---- 分页 ---- */
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

/* ---- 弹窗通用 ---- */
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

.dialog-footer {
  display: flex;
  flex-direction: row;
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
  font-family: 'PingFang SC', sans-serif;
  font-size: 13px;
  line-height: 22px;
  cursor: pointer;

  &:hover,
  &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.btn-save {
  width: auto;
  height: 32px;
  padding: 5px 16px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #7288FA;
  border: 1px solid #7288FA;
  border-radius: 6px;
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.043);
  color: #FFFFFF;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 22px;
  cursor: pointer;

  &:hover,
  &:focus {
    background: #5C75E8;
    border-color: #5C75E8;
    color: #FFFFFF;
  }

  &:disabled {
    background: #D4DBF5;
    border-color: #D4DBF5;
    color: #FFFFFF;
    cursor: not-allowed;
    box-shadow: none;
  }
}

/* ---- 表单弹窗 ---- */
:deep(.el-form-item) {
  margin-bottom: 16px;
}

:deep(.el-form-item__label) {
  font-family: 'Roboto', 'PingFang SC', sans-serif;
  font-size: 14px;
  font-weight: 400;
  color: rgba(0, 0, 0, 0.85);
  line-height: 32px;
  padding-right: 9px;
}

:deep(.el-form-item.is-required .el-form-item__label::before) {
  color: #FF4D4F !important;
}

:deep(.el-input__wrapper) {
  height: 32px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #D9D9D9 inset !important;
  padding: 4px 12px;

  &:hover, &.is-focus {
    box-shadow: 0 0 0 1px #7288FA inset !important;
  }
}

:deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #FF4D4F inset !important;

  &:hover,
  &.is-focus {
    box-shadow: 0 0 0 1px #FF4D4F inset !important;
  }
}

:deep(.el-input__inner) {
  font-size: 14px;
  height: 24px;
  line-height: 24px;
}

:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: rgba(0, 0, 0, 0.35);
}

:deep(.el-input-number__decrease),
:deep(.el-input-number__increase) {
  width: 31px;
  height: 30px;
  background: #F5F7FA;
  border-color: #D9D9D9;
  color: #7C7E81;
}

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

.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}

/* ---- 图片上传 ---- */
.image-upload-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.image-uploader {
  flex-shrink: 0;
  width: 100px;
  height: 100px;
  background: #FAFAFA;
  border: 1px solid #D9D9D9;
  border-radius: 6px;
  cursor: pointer;
  overflow: hidden;

  &:hover {
    border-color: #7288FA;
  }

  :deep(.el-upload) {
    width: 100%;
    height: 100%;
    display: flex;
  }
}

.image-preview {
  width: 100px;
  height: 100px;
  position: relative;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .image-actions {
    position: absolute;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transition: opacity 0.3s;

    span {
      color: #fff;
      font-size: 12px;
      cursor: pointer;
    }
  }

  &:hover .image-actions {
    opacity: 1;
  }
}

.image-placeholder {
  width: 100px;
  height: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #8c939d;
  gap: 4px;

  .el-icon {
    font-size: 24px;
  }

  span {
    font-size: 12px;
  }
}

.image-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

/* ---- 详情弹窗 ---- */
.detail-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.section-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}

.info-section-title {
  margin-bottom: 12px;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 20px;
  background: #7288FA;
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  width: 100%;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 0 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;

  &--span3 {
    grid-column: span 3;
    height: auto;
    min-height: 40px;
    padding: 5px 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.detail-image-wrapper {
  text-align: center;
}

.detail-image {
  max-width: 200px;
  max-height: 200px;
  border-radius: 8px;
  object-fit: cover;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
