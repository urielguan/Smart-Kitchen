<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useAlertRuleStore } from '@/stores/modules/alert-rule'
import { THRESHOLD_METRICS, NOTIFY_CHANNELS } from '@/types/alert-rule'
import { employeeApi } from '@/api/modules/employee'
import { formatDateTime } from '@/utils'

const ruleStore = useAlertRuleStore()

/** 员工选项（用于翻译通知用户 ID → 姓名） */
const employeeOptions = ref<{ value: string; label: string }[]>([])

const loadEmployeeOptions = async () => {
  try {
    const res = await employeeApi.getList({ status: 'active', accountStatus: 'active', pageSize: 500 })
    if (res.data) {
      const pageData = res.data as any
      const emps = pageData.list || pageData || []
      employeeOptions.value = (Array.isArray(emps) ? emps : []).map((e: any) => ({ value: String(e.id), label: e.realName }))
    }
  } catch { /* ignore */ }
}

watch(() => ruleStore.detailVisible, (visible) => {
  if (visible && employeeOptions.value.length === 0) {
    loadEmployeeOptions()
  }
})

const visible = computed({
  get: () => ruleStore.detailVisible,
  set: (val) => { if (!val) ruleStore.closeDetail() },
})

const handleClose = () => ruleStore.closeDetail()

const getAlertLevelTag = (level: string) => {
  const map: Record<string, string> = { info: 'info', warning: 'warning', error: 'danger', critical: 'danger' }
  return map[level] || 'info'
}

const getConditionSummary = (rule: { ruleType: string; conditionJson: string }) => {
  try {
    const c = JSON.parse(rule.conditionJson)
    if (rule.ruleType === 'threshold') {
      const conds: any[] = (c.conditions && Array.isArray(c.conditions))
        ? c.conditions
        : [{ metric: c.metric, operator: c.operator, value: c.value }]
      const parts = conds.map(cond => {
        const metricLabel = THRESHOLD_METRICS.find(m => m.value === cond.metric)?.label || cond.metric
        return `${metricLabel} ${cond.operator} ${cond.value}`
      })
      const logicWord = c.logic === 'or' ? ' 或 ' : ' 且 '
      return `${parts.join(logicWord)}${c.duration ? `，持续 ${c.duration}秒` : ''}`
    }
    if (rule.ruleType === 'offline') return `离线超过 ${c.offlineMinutes} 分钟`
    if (rule.ruleType === 'material') return '按物料配置自动检测（效期/库存）'
    return rule.conditionJson
  } catch {
    return rule.conditionJson || '-'
  }
}

const translateNotifyChannels = (raw: string) => {
  const map = Object.fromEntries(NOTIFY_CHANNELS.map(c => [c.value, c.label]))
  return raw.split(',').map(v => map[v.trim()] || v.trim()).filter(Boolean)
}

const translateNotifyUsers = (raw: string) => {
  const map = Object.fromEntries(employeeOptions.value.map(u => [u.value, u.label]))
  return raw.split(',').map(v => map[v.trim()] || v.trim()).filter(Boolean)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="758px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    class="alert-rule-detail-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">告警规则详情</span>
        <div class="close-btn" @click="handleClose">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <line x1="6" y1="6" x2="18" y2="18" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
            <line x1="6" y1="18" x2="18" y2="6" stroke="#1C1D22" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </div>
      </div>
    </template>

    <div v-if="ruleStore.detailRule" class="detail-body">
      <div class="info-table">
        <div class="info-label">规则名称</div>
        <div class="info-value">{{ ruleStore.detailRule.ruleName }}</div>

        <div class="info-label">规则类型</div>
        <div class="info-value">{{ ruleStore.detailRule.ruleTypeName }}</div>
        <div class="info-label" v-if="ruleStore.detailRule.ruleType !== 'material'">设备类型</div>
        <div class="info-value" v-if="ruleStore.detailRule.ruleType !== 'material'">{{ ruleStore.detailRule.deviceTypeName }}</div>

        <div class="info-label">告警级别</div>
        <div class="info-value">
          <el-tag :type="getAlertLevelTag(ruleStore.detailRule.alertLevel)" size="small">{{ ruleStore.detailRule.alertLevelName }}</el-tag>
        </div>
        <div class="info-label">状态</div>
        <div class="info-value">
          <el-tag :type="ruleStore.detailRule.isEnabled === 1 ? 'success' : 'info'" size="small">
            {{ ruleStore.detailRule.isEnabled === 1 ? '已启用' : '已禁用' }}
          </el-tag>
        </div>

        <div class="info-label">自动派单</div>
        <div class="info-value">
          <el-tag :type="ruleStore.detailRule.autoDispatch === 1 ? 'success' : 'info'" size="small">
            {{ ruleStore.detailRule.autoDispatch === 1 ? '已开启' : '未开启' }}
          </el-tag>
        </div>
        <div class="info-label">创建时间</div>
        <div class="info-value">{{ formatDateTime(ruleStore.detailRule.createdAt) }}</div>

        <div class="info-label">更新时间</div>
        <div class="info-value" :class="{ 'info-value--span3': ruleStore.detailRule.ruleType === 'material' }">{{ formatDateTime(ruleStore.detailRule.updatedAt) }}</div>

        <template v-if="ruleStore.detailRule.ruleType === 'material'">
          <div class="info-label">适用物料</div>
          <div class="info-value info-value--span3">
            <template v-if="ruleStore.detailRule.materialNames?.length">
              <el-tag v-for="name in ruleStore.detailRule.materialNames" :key="name" size="small" style="margin-right: 4px">{{ name }}</el-tag>
            </template>
            <template v-else>未配置</template>
          </div>
        </template>
        <template v-else>
          <div class="info-label">适用设备</div>
          <div class="info-value info-value--span3">
            <template v-if="ruleStore.detailRule.deviceNames?.length">
              <el-tag v-for="name in ruleStore.detailRule.deviceNames" :key="name" size="small" style="margin-right: 4px">{{ name }}</el-tag>
            </template>
            <template v-else>未配置</template>
          </div>
        </template>

        <div class="info-label">触发条件</div>
        <div class="info-value info-value--span3">{{ getConditionSummary(ruleStore.detailRule) }}</div>

        <div class="info-label">通知渠道</div>
        <div class="info-value info-value--span3">
          <template v-if="ruleStore.detailRule.notifyChannels">
            <el-tag v-for="ch in translateNotifyChannels(ruleStore.detailRule.notifyChannels)" :key="ch" size="small" style="margin-right: 4px">{{ ch }}</el-tag>
          </template>
          <template v-else>未配置</template>
        </div>

        <div class="info-label">通知用户</div>
        <div class="info-value info-value--span3">
          <template v-if="ruleStore.detailRule.notifyUsers">
            <el-tag v-for="name in translateNotifyUsers(ruleStore.detailRule.notifyUsers)" :key="name" size="small" style="margin-right: 4px">{{ name }}</el-tag>
          </template>
          <template v-else>未配置</template>
        </div>

        <div class="info-label">派单范围</div>
        <div class="info-value info-value--span3">
          <template v-if="ruleStore.detailRule.dispatchScopeRoleNames?.length">
            <el-tag v-for="name in ruleStore.detailRule.dispatchScopeRoleNames" :key="name" size="small" style="margin-right: 4px">{{ name }}</el-tag>
          </template>
          <template v-else>未配置</template>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button class="btn-cancel" @click="handleClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss">
/* ---- Dialog 容器（unscoped） ---- */
.alert-rule-detail-dialog.el-dialog {
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

.alert-rule-detail-dialog .el-dialog__header {
  padding: 24px 24px 13px !important;
  margin-right: 0;
  border-bottom: 1px solid #E1E2E9;
}

.alert-rule-detail-dialog .el-dialog__body {
  padding: 16px 24px 24px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.alert-rule-detail-dialog .el-dialog__footer {
  padding: 0;
  border-top: 1px solid #E1E2E9;
}
</style>

<style lang="scss" scoped>
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
  color: #53545C;
  font-size: 13px;

  &:hover, &:focus {
    background: #F5F7FA;
    border-color: #7288FA;
    color: #7288FA;
  }
}

.detail-body {
  display: flex;
  flex-direction: column;
}

.info-table {
  display: grid;
  grid-template-columns: 112px 1fr 112px 1fr;
  border-top: 1px solid #E1E2E9;
  border-left: 1px solid #E1E2E9;
}

.info-label {
  background: #F5F7FA;
  border-right: 1px solid #ECEEF5;
  border-bottom: 1px solid #E1E2E9;
  padding: 9px 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
  word-break: break-all;
}

.info-value {
  border-right: 1px solid #E1E2E9;
  border-bottom: 1px solid #E1E2E9;
  padding: 9px 12px;
  min-height: 40px;
  display: flex;
  align-items: center;
  font-family: 'PingFang SC', sans-serif;
  font-size: 14px;
  line-height: 20px;
  color: #333333;
  word-break: break-all;

  &--span3 {
    grid-column: span 3;
    height: auto;
    min-height: 40px;
    padding: 5px 12px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

/* ---- tag ---- */
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
  color: #7288FA;
  background: rgba(114, 136, 250, 0.1);
  border: 1px solid rgba(114, 136, 250, 0.3);
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
