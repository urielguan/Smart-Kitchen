<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { integrationApi } from '@/api/modules/integration'
import { orgApi } from '@/api/modules/org'
import { INTEGRATION_PERMISSIONS } from '@/constants/permission'
import { useUserStore } from '@/stores/modules/user'
import AiConfigPage from '@/views/sys/ai-config/index.vue'
import type { OrgTreeNode } from '@/types/org'
import type {
  IntegrationCallbackLogItem,
  IntegrationExecutionResult,
  IntegrationFieldMappingForm,
  IntegrationFieldMappingItem,
  IntegrationFileRecordItem,
  IntegrationHealthCheckItem,
  IntegrationMetricItem,
  IntegrationModuleConfigForm,
  IntegrationModuleConfigItem,
  IntegrationOverview,
  IntegrationProviderTemplateForm,
  IntegrationProviderTemplateItem,
  IntegrationStatusMappingForm,
  IntegrationStatusMappingItem,
  IntegrationSyncLogHandleForm,
  IntegrationSyncLogItem,
  IntegrationSyncLogProviderOption,
  IntegrationSyncTaskItem,
  IntegrationSyncTriggerForm
} from '@/types/integration'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const orgTree = ref<OrgTreeNode[]>([])
const providerOptions = ref<IntegrationProviderTemplateItem[]>([])
const syncLogProviderOptions = ref<IntegrationSyncLogProviderOption[]>([])
const callbackLogProviderOptions = ref<IntegrationSyncLogProviderOption[]>([])
const fileRecordProviderOptions = ref<IntegrationSyncLogProviderOption[]>([])
const healthProviderOptions = ref<IntegrationSyncLogProviderOption[]>([])
const moduleConfigOptions = ref<IntegrationModuleConfigItem[]>([])

const providerTypeOptions = [
  { label: '物流平台', value: 'logistics' },
  { label: '检测平台', value: 'inspection' },
  { label: '溯源平台', value: 'traceability' },
  { label: '设备平台', value: 'device' }
]

const authTypeOptions = [
  { label: 'Bearer Token', value: 'bearer' },
  { label: 'AppSecret', value: 'app_secret' },
  { label: 'OAuth2', value: 'oauth2' }
]

const protocolOptions = [
  { label: 'HTTP', value: 'http' },
  { label: 'HTTPS', value: 'https' }
]

const bizModuleOptions = [
  { label: '采购订单', value: 'purchase_order' },
  { label: '留样管理', value: 'sample_retention' },
  { label: '智能人脸晨检', value: 'morning_check' },
  { label: '供应商管理', value: 'supplier' },
  { label: '入库管理', value: 'inbound' }
]

const sceneOptionsMap: Record<string, Array<{ label: string; value: string }>> = {
  purchase_order: [
    { label: '物流追踪', value: 'logistics' },
    { label: '检测报告', value: 'inspection' },
    { label: '溯源信息', value: 'traceability' }
  ],
  sample_retention: [
    { label: '留样任务同步', value: 'sample_task' },
    { label: '留样执行记录同步', value: 'sample_record' },
    { label: '留样结果同步', value: 'sample_result' },
    { label: '留样设备档案同步', value: 'sample_device' }
  ],
  morning_check: [
    { label: '人员身份/人脸档案同步', value: 'face_profile' },
    { label: '健康证档案同步', value: 'health_certificate' },
    { label: '晨检结果同步', value: 'morning_check_result' },
    { label: '晨检设备档案同步', value: 'device_master' }
  ],
  supplier: [{ label: '资质核验', value: 'qualification_verify' }],
  inbound: [{ label: '到货回执', value: 'arrival_receipt' }]
}

const providerSceneCodeOptions = bizModuleOptions.flatMap(module =>
  (sceneOptionsMap[module.value] || []).map(scene => ({
    label: `${module.label} / ${scene.label}`,
    value: `${module.value}:${scene.value}`
  }))
)

type StandardFieldOption = {
  value: string
  label: string
  description: string
  usage: string
}

type StandardStatusOption = {
  value: string
  label: string
  description: string
  usage: string
}

type FieldValueType = 'string' | 'date' | 'datetime' | 'number' | 'long' | 'array'

const standardFieldOptionsMap: Record<string, StandardFieldOption[]> = {
  'purchase_order:logistics': [
    { value: 'trackingNo', label: '物流单号', description: '第三方物流单号统一标准字段', usage: '最终回写到 scm_purchase_order.logistics_no' },
    { value: 'company', label: '物流公司', description: '第三方物流公司统一标准字段', usage: '最终回写到 scm_purchase_order.logistics_company' },
    { value: 'status', label: '物流状态', description: '第三方物流状态编码标准字段', usage: '先做状态映射，再写入 scm_purchase_order.logistics_status' },
    { value: 'shippedAt', label: '发货时间', description: '第三方发货时间统一标准字段', usage: '最终回写到 scm_purchase_order.shipped_at' },
    { value: 'arrivedAt', label: '到货时间', description: '第三方到货时间统一标准字段', usage: '最终回写到 scm_purchase_order.arrived_at / actual_delivery_at' },
    { value: 'remark', label: '物流备注', description: '第三方物流备注统一标准字段', usage: '最终回写到 scm_purchase_order.logistics_remark' },
    { value: 'attachments', label: '附件列表', description: '第三方附件数组标准字段', usage: '转存后写入采购订单附件表及主表镜像附件字段' }
  ],
  'purchase_order:inspection': [
    { value: 'reportNo', label: '检测报告编号', description: '第三方报告编号统一标准字段', usage: '最终回写到 scm_purchase_order.inspection_report_no' },
    { value: 'result', label: '检测结果', description: '第三方检测结论统一标准字段', usage: '最终回写到 scm_purchase_order.inspection_result' },
    { value: 'agency', label: '检测机构', description: '第三方检测机构统一标准字段', usage: '最终回写到 scm_purchase_order.inspection_agency' },
    { value: 'inspectionAt', label: '检测时间', description: '第三方检测时间统一标准字段', usage: '最终回写到 scm_purchase_order.inspection_at' },
    { value: 'remark', label: '检测备注', description: '第三方检测备注统一标准字段', usage: '最终回写到 scm_purchase_order.inspection_remark' },
    { value: 'attachments', label: '附件列表', description: '第三方附件数组标准字段', usage: '转存后写入采购订单检测附件表及主表镜像附件字段' }
  ],
  'purchase_order:traceability': [
    { value: 'batchId', label: '溯源批次码', description: '第三方批次号统一标准字段', usage: '最终回写到 scm_purchase_order.trace_batch_id' },
    { value: 'origin', label: '来源/产地', description: '第三方来源地统一标准字段', usage: '最终回写到 scm_purchase_order.trace_origin' },
    { value: 'remark', label: '溯源备注', description: '第三方溯源备注统一标准字段', usage: '最终回写到 scm_purchase_order.trace_remark' },
    { value: 'attachments', label: '附件列表', description: '第三方附件数组标准字段', usage: '转存后写入采购订单溯源附件表及主表镜像附件字段' }
  ],
  'sample_retention:sample_task': [
    { value: 'sampleTaskNo', label: '留样任务编号', description: '留样任务主键标准字段', usage: '外部任务号保留在绑定表，并镜像到 sample_record.ai_analysis_result.thirdPartySampleTaskNo' },
    { value: 'cookingTaskId', label: '关联烹饪任务', description: '上游烹饪任务关联标准字段', usage: '最终回写到 sample_record.task_id' },
    { value: 'recipeName', label: '菜谱名称', description: '菜谱名称标准字段', usage: '最终回写到 sample_record.menu_name' },
    { value: 'dishName', label: '菜品名称', description: '菜品名称标准字段', usage: '最终回写到 sample_record.menu_name' },
    { value: 'requiredSampleWeight', label: '要求留样重量', description: '计划留样重量标准字段', usage: '回写到 sample_record.ai_analysis_result.plannedSampleWeight' },
    { value: 'plannedSampleTime', label: '计划留样时间', description: '计划执行时间标准字段', usage: '回写到 sample_record.ai_analysis_result.plannedSampleTime' },
    { value: 'kitchenAreaId', label: '所属厨房/区域', description: '厨房或区域标准字段', usage: '回写到 sample_record.ai_analysis_result.kitchenAreaId' }
  ],
  'sample_retention:sample_record': [
    { value: 'sampleRecordNo', label: '留样记录编号', description: '留样执行记录主键标准字段', usage: '镜像到 sample_record.ai_analysis_result.thirdPartySampleRecordNo，业务编号仍以 sample_record.sample_no 为主' },
    { value: 'sampleTaskNo', label: '关联留样任务', description: '留样任务关联标准字段', usage: '回写到 sample_record.ai_analysis_result.linkedSampleTaskNo' },
    { value: 'sampleWeight', label: '实际留样重量', description: '实际留样重量标准字段', usage: '最终回写到 sample_record.sample_weight' },
    { value: 'samplerId', label: '留样人员', description: '留样人员标准字段', usage: '最终回写到 sample_record.sampled_by' },
    { value: 'sampledAt', label: '留样时间', description: '留样执行时间标准字段', usage: '最终回写到 sample_record.sampled_at / disposal_due_at' },
    { value: 'storageLocation', label: '存储位置', description: '留样存放位置标准字段', usage: '最终回写到 sample_record.storage_location' },
    { value: 'evidenceFiles', label: '留样证据附件', description: '留样图片/视频等证据附件标准字段', usage: '转存后回写到 sample_record.sample_images' }
  ],
  'sample_retention:sample_result': [
    { value: 'qualityScore', label: '质量评分', description: '第三方评分标准字段', usage: '最终回写到 sample_record.ai_quality_score' },
    { value: 'complianceStatus', label: '合规结论', description: '第三方合规结论标准字段', usage: '回写到 sample_record.ai_analysis_result.complianceStatus' },
    { value: 'anomalyTags', label: '异常标签', description: '第三方异常标签标准字段', usage: '回写到 sample_record.ai_analysis_result.anomalyTags' },
    { value: 'inspectionReportNo', label: '检测报告编号', description: '关联检测报告标准字段', usage: '回写到 sample_record.ai_analysis_result.inspectionReportNo' },
    { value: 'destroyAt', label: '销样时间', description: '销样执行时间标准字段', usage: '最终回写到 sample_record.disposal_at' },
    { value: 'destroyEvidenceFiles', label: '销样证据附件', description: '销样图片/视频附件标准字段', usage: '转存后回写到 sample_record.disposal_images' }
  ],
  'sample_retention:sample_device': [
    { value: 'deviceCode', label: '设备编号', description: '留样设备唯一编号标准字段', usage: '最终匹配/回写到 device_info.device_code' },
    { value: 'deviceName', label: '设备名称', description: '留样设备名称标准字段', usage: '最终回写到 device_info.device_name' },
    { value: 'manufacturer', label: '厂商', description: '留样设备厂家标准字段', usage: '最终回写到 device_info.manufacturer' },
    { value: 'model', label: '型号', description: '留样设备型号标准字段', usage: '最终回写到 device_info.device_model' },
    { value: 'onlineStatus', label: '在线状态', description: '设备在线状态标准字段', usage: '最终回写到 device_info.online_status' },
    { value: 'storageTemperature', label: '柜体温度', description: '设备温度标准字段', usage: '回写到 device_info.config_params.storageTemperature' },
    { value: 'weightPrecision', label: '称重精度', description: '设备称重精度标准字段', usage: '回写到 device_info.config_params.weightPrecision' }
  ],
  'morning_check:face_profile': [
    { value: 'employeeCode', label: '员工编码', description: '人员编码标准字段', usage: '用于匹配 sys_employee.employee_no' },
    { value: 'employeeId', label: '员工ID', description: '人员主键标准字段', usage: '用于定位 sys_employee.id / health_face_feature.employee_id' },
    { value: 'employeeName', label: '员工姓名', description: '人员姓名标准字段', usage: '最终回写到 sys_employee.real_name' },
    { value: 'externalFaceId', label: '第三方人脸ID', description: '厂家脸库人脸标识标准字段', usage: '本地无人脸向量时占位写入 health_face_feature.face_feature_vector，用于第三方追溯' },
    { value: 'faceEnrollStatus', label: '人脸录入状态', description: '人脸建档状态标准字段', usage: '最终回写到 sys_employee.face_enrolled / health_face_feature.is_active' },
    { value: 'faceImageUrl', label: '人脸照片地址', description: '人脸照片标准字段', usage: '最终回写到 sys_employee.avatar_url / health_face_feature.face_image_url' }
  ],
  'morning_check:health_certificate': [
    { value: 'employeeId', label: '员工ID', description: '健康证归属员工主键标准字段', usage: '用于定位 sys_employee.id，并回写到 health_certificate.employee_id' },
    { value: 'employeeCode', label: '员工编码', description: '健康证归属员工编码标准字段', usage: '用于匹配 sys_employee.employee_no' },
    { value: 'employeeName', label: '员工姓名', description: '健康证归属员工姓名标准字段', usage: '最终回写到 health_certificate.employee_name' },
    { value: 'certificateNo', label: '健康证编号', description: '健康证唯一编号标准字段', usage: '最终回写到 health_certificate.certificate_no' },
    { value: 'issueDate', label: '发证日期', description: '健康证发证日期标准字段', usage: '最终回写到 health_certificate.issue_date' },
    { value: 'expireDate', label: '到期日期', description: '健康证到期日期标准字段', usage: '最终回写到 health_certificate.expiry_date' },
    { value: 'certificateStatus', label: '健康证状态', description: '健康证状态标准字段', usage: '最终回写到 health_certificate.status，并同步 sys_employee.health_cert_status' },
    { value: 'certificateImages', label: '健康证附件', description: '健康证图片/附件标准字段', usage: '转存后回写到 health_certificate.certificate_images' }
  ],
  'morning_check:morning_check_result': [
    { value: 'checkNo', label: '晨检单号', description: '晨检结果主键标准字段', usage: '新建时写入 health_check_record.check_no，已存在记录则按原单号更新' },
    { value: 'externalCheckId', label: '第三方晨检ID', description: '厂家晨检记录ID标准字段', usage: '回写到 health_check_record.remark（扩展追溯信息）' },
    { value: 'employeeId', label: '员工ID', description: '晨检人员主键标准字段', usage: '最终回写到 health_check_record.employee_id' },
    { value: 'employeeCode', label: '员工编码', description: '晨检人员编码标准字段', usage: '用于匹配 sys_employee.employee_no' },
    { value: 'deviceCode', label: '设备编号', description: '晨检设备编号标准字段', usage: '回写到 health_check_record.remark（扩展设备信息）' },
    { value: 'faceCheckResult', label: '身份识别结果', description: '人脸识别结论标准字段', usage: '参与综合判定，失败原因落 health_check_record.fail_reason' },
    { value: 'faceMatchScore', label: '相似度分值', description: '人脸比对分值标准字段', usage: '最终回写到 health_check_record.face_match_score' },
    { value: 'temperature', label: '体温', description: '体温值标准字段', usage: '最终回写到 health_check_record.temperature' },
    { value: 'temperatureStatus', label: '体温状态', description: '体温正常/异常标准字段', usage: '转换后回写到 health_check_record.health_status' },
    { value: 'certificateCheckResult', label: '健康证比对结果', description: '健康证校验结论标准字段', usage: '转换后回写到 health_check_record.certificate_status' },
    { value: 'certificateCheckMessage', label: '健康证比对说明', description: '健康证校验说明标准字段', usage: '回写到 health_check_record.fail_reason / remark' },
    { value: 'handCheckResult', label: '手部检测结果', description: '手部清洁/异常结论标准字段', usage: '最终回写到 health_check_record.hand_hygiene' },
    { value: 'handCheckMessage', label: '手部检测说明', description: '手部检测说明标准字段', usage: '回写到 health_check_record.fail_reason / remark' },
    { value: 'checkResult', label: '综合晨检结果', description: '晨检最终结论标准字段', usage: '最终回写到 health_check_record.check_result / status' },
    { value: 'evidenceImageUrl', label: '取证图片', description: '晨检抓拍图片标准字段', usage: '最终回写到 health_check_record.face_image_url' },
    { value: 'checkTime', label: '晨检时间', description: '晨检执行时间标准字段', usage: '最终回写到 health_check_record.check_time / check_date' }
  ],
  'morning_check:device_master': [
    { value: 'deviceCode', label: '设备编号', description: '晨检设备唯一编号标准字段', usage: '最终匹配/回写到 device_info.device_code' },
    { value: 'uuid', label: '设备UUID', description: '晨检设备全局唯一标识标准字段', usage: '回写到 device_info.config_params.uuid' },
    { value: 'deviceName', label: '设备名称', description: '晨检设备名称标准字段', usage: '最终回写到 device_info.device_name' },
    { value: 'manufacturer', label: '厂商', description: '晨检设备厂家标准字段', usage: '最终回写到 device_info.manufacturer' },
    { value: 'model', label: '型号', description: '晨检设备型号标准字段', usage: '最终回写到 device_info.device_model' },
    { value: 'onlineStatus', label: '在线状态', description: '设备在线状态标准字段', usage: '最终回写到 device_info.online_status' },
    { value: 'installLocation', label: '安装位置', description: '设备安装点位标准字段', usage: '最终回写到 device_info.location_desc' },
    { value: 'faceThreshold', label: '人脸阈值', description: '设备识别阈值标准字段', usage: '回写到 device_info.config_params.faceThreshold' },
    { value: 'temperatureThreshold', label: '体温阈值', description: '设备体温阈值标准字段', usage: '回写到 device_info.config_params.temperatureThreshold' }
  ],
  'supplier:qualification_verify': [
    { value: 'supplierCode', label: '供应商编码', description: '供应商唯一编码标准字段', usage: '用于匹配并回写 scm_supplier.supplier_code' },
    { value: 'supplierName', label: '供应商名称', description: '供应商名称标准字段', usage: '最终回写到 scm_supplier.supplier_name' },
    { value: 'unifiedCreditCode', label: '统一社会信用代码', description: '供应商统一信用代码标准字段', usage: '最终回写到 scm_supplier.unified_credit_code' },
    { value: 'licenseNo', label: '营业执照编号', description: '营业执照编号标准字段', usage: '最终回写到 scm_supplier.license_no' },
    { value: 'licenseExpireAt', label: '营业执照到期时间', description: '营业执照有效期标准字段', usage: '最终回写到 scm_supplier.license_expires_at' },
    { value: 'foodLicenseNo', label: '食品许可证编号', description: '食品经营/生产许可证编号标准字段', usage: '最终回写到 scm_supplier.food_license_no' },
    { value: 'foodLicenseExpireAt', label: '食品许可证到期时间', description: '食品经营/生产许可证有效期标准字段', usage: '最终回写到 scm_supplier.food_license_expires_at' },
    { value: 'qualificationFiles', label: '资质附件', description: '资质文件列表标准字段', usage: '最终回写到 scm_supplier.qualification_files' },
    { value: 'qualificationStatus', label: '资质核验状态', description: '资质核验结果标准字段', usage: '最终回写到 scm_supplier.status' }
  ],
  'inbound:arrival_receipt': [
    { value: 'inboundNo', label: '入库单号', description: '入库单唯一编号标准字段', usage: '最终回写到 wms_inbound_order.inbound_no' },
    { value: 'sourceOrderNo', label: '来源单号', description: '来源采购单/外部单号标准字段', usage: '最终回写到 wms_inbound_order.source_order_no' },
    { value: 'supplierName', label: '供应商名称', description: '到货供应商名称标准字段', usage: '最终回写到 wms_inbound_order.supplier_name' },
    { value: 'warehouseCode', label: '仓库编码', description: '到货仓库编码标准字段', usage: '用于匹配 wms_warehouse.warehouse_code' },
    { value: 'warehouseName', label: '仓库名称', description: '到货仓库名称标准字段', usage: '用于匹配/展示仓库信息' },
    { value: 'receiptStatus', label: '到货回执状态', description: '到货/入库状态标准字段', usage: '最终回写到 wms_inbound_order.status' },
    { value: 'approvedAt', label: '到货确认时间', description: '到货确认或审核时间标准字段', usage: '最终回写到 wms_inbound_order.approved_at' },
    { value: 'attachments', label: '附件列表', description: '到货回执附件标准字段', usage: '最终回写到 wms_inbound_order.attachments' },
    { value: 'remark', label: '备注', description: '到货回执备注标准字段', usage: '最终回写到 wms_inbound_order.remark' }
  ]
}

const standardFieldValueTypeMap: Record<string, FieldValueType> = {
  trackingNo: 'string',
  company: 'string',
  status: 'string',
  shippedAt: 'datetime',
  arrivedAt: 'datetime',
  remark: 'string',
  attachments: 'array',
  reportNo: 'string',
  result: 'string',
  agency: 'string',
  inspectionAt: 'datetime',
  batchId: 'string',
  origin: 'string',
  sampleTaskNo: 'string',
  cookingTaskId: 'long',
  recipeName: 'string',
  dishName: 'string',
  requiredSampleWeight: 'number',
  plannedSampleTime: 'datetime',
  kitchenAreaId: 'long',
  sampleRecordNo: 'string',
  sampleWeight: 'number',
  samplerId: 'long',
  sampledAt: 'datetime',
  storageLocation: 'string',
  evidenceFiles: 'array',
  qualityScore: 'number',
  complianceStatus: 'string',
  anomalyTags: 'array',
  inspectionReportNo: 'string',
  destroyAt: 'datetime',
  destroyEvidenceFiles: 'array',
  deviceCode: 'string',
  deviceName: 'string',
  manufacturer: 'string',
  model: 'string',
  onlineStatus: 'string',
  storageTemperature: 'number',
  weightPrecision: 'number',
  employeeCode: 'string',
  employeeId: 'long',
  employeeName: 'string',
  externalFaceId: 'string',
  faceEnrollStatus: 'string',
  faceImageUrl: 'string',
  certificateNo: 'string',
  issueDate: 'date',
  expireDate: 'date',
  certificateStatus: 'string',
  certificateImages: 'array',
  checkNo: 'string',
  externalCheckId: 'string',
  faceCheckResult: 'string',
  faceMatchScore: 'number',
  temperature: 'number',
  temperatureStatus: 'string',
  certificateCheckResult: 'string',
  certificateCheckMessage: 'string',
  handCheckResult: 'string',
  handCheckMessage: 'string',
  checkResult: 'string',
  evidenceImageUrl: 'string',
  checkTime: 'datetime',
  uuid: 'string',
  installLocation: 'string',
  faceThreshold: 'number',
  temperatureThreshold: 'number',
  supplierCode: 'string',
  supplierName: 'string',
  unifiedCreditCode: 'string',
  licenseNo: 'string',
  licenseExpireAt: 'datetime',
  foodLicenseNo: 'string',
  foodLicenseExpireAt: 'datetime',
  qualificationFiles: 'array',
  qualificationStatus: 'string',
  inboundNo: 'string',
  sourceOrderNo: 'string',
  warehouseCode: 'string',
  warehouseName: 'string',
  receiptStatus: 'string',
  approvedAt: 'datetime'
}

const standardStatusOptionsMap: Record<string, StandardStatusOption[]> = {
  'purchase_order:logistics': [
    { value: 'pending', label: '待发货', description: '物流场景初始标准状态值', usage: '最终写入 scm_purchase_order.logistics_status；通常表示单据已创建但尚未发货' },
    { value: 'shipped', label: '已发货', description: '物流已发货标准状态值', usage: '最终写入 scm_purchase_order.logistics_status，并可联动采购订单主状态推进为运输中' },
    { value: 'in_transit', label: '运输中', description: '物流运输中标准状态值', usage: '最终写入 scm_purchase_order.logistics_status，并保持采购订单主状态为运输中' },
    { value: 'arrived', label: '已到货', description: '物流已签收/已到货标准状态值', usage: '最终写入 scm_purchase_order.logistics_status，并可联动采购订单主状态推进为待入库' }
  ],
  'sample_retention:sample_task': [
    { value: 'pending_sample', label: '待留样', description: '留样任务待执行标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'sampled', label: '已留样', description: '留样任务已执行标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'evaluated', label: '已评价', description: '留样任务已评价标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'pending_disposal', label: '待销样', description: '留样任务待销样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'disposed', label: '已销样', description: '留样任务已销样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'overdue', label: '已超期', description: '留样任务超期标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'voided', label: '已作废', description: '留样任务作废标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'archived', label: '已归档', description: '留样任务归档标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' }
  ],
  'sample_retention:sample_record': [
    { value: 'pending_sample', label: '待留样', description: '留样执行记录待执行标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'sampled', label: '已留样', description: '留样执行记录已执行标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'evaluated', label: '已评价', description: '留样执行记录已评价标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'pending_disposal', label: '待销样', description: '留样执行记录待销样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'disposed', label: '已销样', description: '留样执行记录已销样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'overdue', label: '已超期', description: '留样执行记录超期标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'voided', label: '已作废', description: '留样执行记录作废标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'archived', label: '已归档', description: '留样执行记录归档标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' }
  ],
  'sample_retention:sample_result': [
    { value: 'pending_sample', label: '待留样', description: '留样结果场景初始标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'sampled', label: '已留样', description: '留样结果场景已留样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'evaluated', label: '已评价', description: '留样结果场景已评价标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'pending_disposal', label: '待销样', description: '留样结果场景待销样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'disposed', label: '已销样', description: '留样结果场景已销样标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'overdue', label: '已超期', description: '留样结果场景超期标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'voided', label: '已作废', description: '留样结果场景作废标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' },
    { value: 'archived', label: '已归档', description: '留样结果场景归档标准状态值', usage: '最终作为 sample_record.status 的标准状态值之一' }
  ],
  'morning_check:health_certificate': [
    { value: 'pending', label: '待校验', description: '健康证待校验标准状态值', usage: '最终写入 health_certificate.status，并同步 sys_employee.health_cert_status' },
    { value: 'valid', label: '有效', description: '健康证有效标准状态值', usage: '最终写入 health_certificate.status，并同步 sys_employee.health_cert_status' },
    { value: 'expiring', label: '即将到期', description: '健康证临期标准状态值', usage: '最终写入 health_certificate.status，并同步 sys_employee.health_cert_status' },
    { value: 'expired', label: '已过期', description: '健康证过期标准状态值', usage: '最终写入 health_certificate.status，并同步 sys_employee.health_cert_status' }
  ],
  'morning_check:morning_check_result': [
    { value: 'pass', label: '通过', description: '晨检综合通过标准状态值', usage: '最终参与推导 health_check_record.check_result=pass，记录状态会继续衍生为 completed_normal' },
    { value: 'fail', label: '不通过', description: '晨检综合不通过标准状态值', usage: '最终参与推导 health_check_record.check_result=fail，记录状态会继续衍生为 completed_abnormal' }
  ]
}

const triggerStrategyOptions = [
  { label: '手动触发', value: 'manual' },
  { label: '定时轮询', value: 'scheduler' },
  { label: '回调驱动', value: 'callback' }
]

const modeOptions = [
  { label: '手工模式', value: 'manual' },
  { label: '第三方模式', value: 'third_party' }
]

const taskModeSourceOptions = [
  { label: '用户手动选择', value: 'user_selected' },
  { label: '业务场景默认', value: 'business_scene' }
]

const taskTypeOptions = [
  { label: '同步执行', value: 'sync' },
  { label: '只查询', value: 'query_only' },
  { label: '失败重试', value: 'retry' },
  { label: '回调跟进', value: 'callback_followup' }
]

const triggerTypeOptions = [
  { label: '手动触发', value: 'manual' },
  { label: '定时轮询', value: 'scheduler' },
  { label: '回调跟进', value: 'callback' }
]

const transformTypeOptions = [
  { label: '直接映射', value: 'direct' },
  { label: '字典映射', value: 'dict' },
  { label: '日期格式转换', value: 'date' },
  { label: '数值换算', value: 'number' },
  { label: 'JSON 路径提取', value: 'json_path' }
]

const fieldErrorStrategyOptions = [
  { label: '报错并阻断', value: 'fail' },
  { label: '跳过该字段', value: 'skip' },
  { label: '只记日志继续', value: 'log_only' },
  { label: '标记人工处理', value: 'manual_review' }
]

const FIELD_TARGET_HELP_TEXT = '这里选择的是系统标准字段，不是数据库列名；下拉项会随接入配置对应的业务场景自动切换，保存后系统会自动生成执行顺序。'
const FIELD_JSON_PATH_HELP_TEXT = 'JSON路径用于告诉系统去第三方返回的JSON结构里的哪个位置取值；填写后系统会优先按该路径精确取值，未填写时才按第三方字段名递归查找。'
const FIELD_TRANSFORM_TYPE_HELP_TEXT = '转换规则用于指定系统拿到第三方原始值后按什么方式处理；不同规则决定值是直接写入、字典映射、日期格式转换、数值换算还是再做JSON路径提取。'
const FIELD_TRANSFORM_RULE_HELP_TEXT = '转换表达式用于给当前转换规则提供执行参数；不同转换规则下含义不同，例如字典映射填写映射对照表、数值换算填写换算倍数、JSON路径提取填写子路径。'
const FIELD_DEFAULT_VALUE_HELP_TEXT = '默认值用于在第三方未返回该字段值时做兜底；若已填写默认值，系统会优先使用默认值继续后续转换与保存。'
const FIELD_MAPPING_FALLBACK_NOTICE = '当前场景未单独配置的系统字段，系统仍会继续按默认规则兜底；但你已配置过的系统字段，会严格以这条字段映射结果为准，不再回退到该字段的默认提取。'

const syncStatusOptions = [
  { label: '待执行', value: 'pending' },
  { label: '执行中', value: 'running' },
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failed' },
  { label: '无数据', value: 'no_data' },
  { label: '状态映射缺失（待人工处理）', value: 'mapping_missing' }
]

const syncLogHandleStatusOptions = [
  { label: '待处理', value: 'pending_review' },
  { label: '已确认', value: 'confirmed' },
  { label: '已忽略', value: 'ignored' },
  { label: '待复核', value: 'rechecked' }
]

const callbackProcessStatusOptions = [
  { label: '待处理', value: 'pending' },
  { label: '安全校验失败', value: 'security_failed' },
  { label: '已忽略', value: 'ignored' },
  { label: '重复回调', value: 'duplicate' },
  { label: '成功', value: 'success' },
  { label: '无数据', value: 'no_data' },
  { label: '状态映射缺失', value: 'mapping_missing' },
  { label: '失败', value: 'failed' }
]

const fileRecordStatusOptions = [
  { label: '待处理', value: 'pending' },
  { label: '成功', value: 'success' },
  { label: '失败', value: 'failed' },
  { label: '跳过', value: 'skipped' },
  { label: '复用历史结果', value: 'reused' }
]

const terminalStatusValuesMap: Record<string, string[]> = {
  'purchase_order:logistics': ['arrived'],
  'sample_retention:sample_task': ['disposed', 'voided', 'archived'],
  'sample_retention:sample_record': ['disposed', 'voided', 'archived'],
  'sample_retention:sample_result': ['disposed', 'voided', 'archived'],
  'morning_check:morning_check_result': ['pass', 'fail']
}

const flatOrgOptions = computed(() => flattenOrgTree(orgTree.value))
const providerOptionNameMap = computed(() => new Map(providerOptions.value.map(item => [item.providerCode, item.providerName])))
const activeTaskModuleConfigOptions = computed(() => moduleConfigOptions.value.filter(item => item.enabled === 1))

const canCreate = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.CREATE))
const canEdit = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.EDIT))
const canDelete = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.DELETE))
const canStatus = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.STATUS))
const canTest = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.TEST))
const canSync = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.SYNC))
const canRetry = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.RETRY))
const hasIntegrationAccess = computed(() => userStore.hasPermission(INTEGRATION_PERMISSIONS.VIEW))
const hasSyncLogAccess = computed(() =>
  userStore.hasPermission(INTEGRATION_PERMISSIONS.VIEW) || userStore.hasPermission(INTEGRATION_PERMISSIONS.VIEW_LOG)
)
const hasCallbackLogAccess = computed(() =>
  userStore.hasPermission(INTEGRATION_PERMISSIONS.VIEW) || userStore.hasPermission(INTEGRATION_PERMISSIONS.VIEW_CALLBACK)
)
const hasAiConfigAccess = computed(() => userStore.canAccessRoute('/ai-config'))

function normalizeTabName(tab: unknown) {
  return tab === 'ai-config' ? 'aiConfig' : typeof tab === 'string' ? tab : ''
}

function resolveRouteTabValue(tab: string) {
  if (tab === 'overview') {
    return ''
  }
  if (tab === 'aiConfig') {
    return 'ai-config'
  }
  return tab
}

function resolveDefaultTab() {
  const routeTab = normalizeTabName(route.query.tab)
  if (routeTab === 'aiConfig' && hasAiConfigAccess.value) {
    return 'aiConfig'
  }
  if (routeTab === 'syncLogs' && hasSyncLogAccess.value) {
    return 'syncLogs'
  }
  if (routeTab === 'callbackLogs' && hasCallbackLogAccess.value) {
    return 'callbackLogs'
  }
  if (routeTab && routeTab !== 'aiConfig' && routeTab !== 'syncLogs' && routeTab !== 'callbackLogs' && hasIntegrationAccess.value) {
    return routeTab
  }
  if (hasIntegrationAccess.value) {
    return 'overview'
  }
  if (hasSyncLogAccess.value) {
    return 'syncLogs'
  }
  if (hasCallbackLogAccess.value) {
    return 'callbackLogs'
  }
  if (hasAiConfigAccess.value) {
    return 'aiConfig'
  }
  return 'overview'
}

const activeTab = ref(resolveDefaultTab())

const overviewLoading = ref(false)
const overview = ref<IntegrationOverview | null>(null)
const overviewQuery = reactive({
  orgId: userStore.userInfo?.orgId || undefined
})

const providerLoading = ref(false)
const providerSaving = ref(false)
const providerDialogVisible = ref(false)
const providerDetailVisible = ref(false)
type ProviderDialogMode = 'create' | 'edit' | 'copy'
const providerDialogMode = ref<ProviderDialogMode>('create')
const currentProviderId = ref<number | null>(null)
const currentProviderDetail = ref<IntegrationProviderTemplateItem | null>(null)
const providerList = ref<IntegrationProviderTemplateItem[]>([])
const providerTotal = ref(0)
const providerTemplateErrors = reactive({
  requestTemplate: '',
  responseTemplate: ''
})
const providerQuery = reactive({
  keyword: '',
  providerType: '',
  status: '',
  pageNum: 1,
  pageSize: 20
})
const providerForm = reactive<IntegrationProviderTemplateForm>(createProviderForm())
const providerDialogTitle = computed(() => {
  if (providerDialogMode.value === 'edit') {
    return '编辑平台模板'
  }
  if (providerDialogMode.value === 'copy') {
    return '复制为自定义模板'
  }
  return '新增平台模板'
})

const configLoading = ref(false)
const configSaving = ref(false)
const configDialogVisible = ref(false)
const configDetailVisible = ref(false)
const currentConfigId = ref<number | null>(null)
const currentConfigDetail = ref<IntegrationModuleConfigItem | null>(null)
const moduleConfigList = ref<IntegrationModuleConfigItem[]>([])
const moduleConfigTotal = ref(0)
const moduleConfigQuery = reactive({
  keyword: '',
  orgId: userStore.userInfo?.orgId || undefined,
  bizModule: '',
  bizScene: '',
  providerCode: '',
  enabled: '' as '' | number,
  defaultMode: '',
  pageNum: 1,
  pageSize: 20
})
const moduleConfigForm = reactive<IntegrationModuleConfigForm>(createModuleConfigForm())

const fieldLoading = ref(false)
const fieldSaving = ref(false)
const fieldDialogVisible = ref(false)
const fieldDetailVisible = ref(false)
const currentFieldId = ref<number | null>(null)
const currentFieldDetail = ref<IntegrationFieldMappingItem | null>(null)
const fieldList = ref<IntegrationFieldMappingItem[]>([])
const fieldTotal = ref(0)
const fieldQuery = reactive({
  configId: undefined as number | undefined,
  keyword: '',
  pageNum: 1,
  pageSize: 20
})
const fieldForm = reactive<IntegrationFieldMappingForm>(createFieldForm())

const statusLoading = ref(false)
const statusSaving = ref(false)
const statusDialogVisible = ref(false)
const statusDetailVisible = ref(false)
const currentStatusId = ref<number | null>(null)
const statusOriginalTargetStatus = ref('')
const currentStatusDetail = ref<IntegrationStatusMappingItem | null>(null)
const statusList = ref<IntegrationStatusMappingItem[]>([])
const statusTotal = ref(0)
const statusQuery = reactive({
  configId: undefined as number | undefined,
  keyword: '',
  pageNum: 1,
  pageSize: 20
})
const statusForm = reactive<IntegrationStatusMappingForm>(createStatusForm())

const taskLoading = ref(false)
const taskDialogVisible = ref(false)
const taskSubmitting = ref(false)
const taskDetailVisible = ref(false)
const currentTaskDetail = ref<IntegrationSyncTaskItem | null>(null)
const taskList = ref<IntegrationSyncTaskItem[]>([])
const taskTotal = ref(0)
const taskQuery = reactive({
  orgId: userStore.userInfo?.orgId || undefined,
  keyword: '',
  bizModule: '',
  bizScene: '',
  providerCode: '',
  taskStatus: '',
  triggerType: '',
  pendingHandleOnly: 0,
  pageNum: 1,
  pageSize: 20
})
const taskForm = reactive<IntegrationSyncTriggerForm>(createTaskForm())

const syncLogLoading = ref(false)
const syncLogDetailVisible = ref(false)
const syncLogs = ref<IntegrationSyncLogItem[]>([])
const syncLogTotal = ref(0)
const currentSyncLog = ref<IntegrationSyncLogItem | null>(null)
const syncLogTimeRange = ref<string[]>([])
const callbackLogTimeRange = ref<string[]>([])
const fileTimeRange = ref<string[]>([])
const syncLogQuery = reactive({
  orgId: userStore.userInfo?.orgId || undefined,
  keyword: '',
  bizModule: '',
  bizScene: '',
  providerCode: '',
  syncStatus: '',
  triggerType: '',
  handleStatus: '',
  startTime: '',
  endTime: '',
  pageNum: 1,
  pageSize: 20
})

const callbackLogLoading = ref(false)
const callbackLogDetailVisible = ref(false)
const callbackLogs = ref<IntegrationCallbackLogItem[]>([])
const callbackLogTotal = ref(0)
const currentCallbackLog = ref<IntegrationCallbackLogItem | null>(null)
const callbackLogQuery = reactive({
  orgId: userStore.userInfo?.orgId || undefined,
  bizModule: '',
  bizScene: '',
  providerCode: '',
  signResult: '',
  processStatus: '',
  startTime: '',
  endTime: '',
  keyword: '',
  pageNum: 1,
  pageSize: 20
})

const fileLoading = ref(false)
const fileDetailVisible = ref(false)
const currentFileDetail = ref<IntegrationFileRecordItem | null>(null)
const fileRecords = ref<IntegrationFileRecordItem[]>([])
const fileTotal = ref(0)
const fileQuery = reactive({
  orgId: userStore.userInfo?.orgId || undefined,
  bindingId: undefined as number | undefined,
  bizModule: '',
  bizScene: '',
  bizNo: '',
  providerCode: '',
  downloadStatus: '',
  storageStatus: '',
  startTime: '',
  endTime: '',
  keyword: '',
  pageNum: 1,
  pageSize: 20
})

const healthLoading = ref(false)
const healthDetailVisible = ref(false)
const currentHealthDetail = ref<IntegrationHealthCheckItem | null>(null)
const healthChecks = ref<IntegrationHealthCheckItem[]>([])
const healthTotal = ref(0)
const healthQuery = reactive({
  keyword: '',
  orgId: userStore.userInfo?.orgId || undefined,
  configId: undefined as number | undefined,
  bizModule: '',
  bizScene: '',
  providerCode: '',
  pageNum: 1,
  pageSize: 20
})

const sceneOptionsForConfig = computed(() => sceneOptionsMap[moduleConfigForm.bizModule] || [])
const sceneOptionsForTask = computed(() => sceneOptionsMap[taskForm.bizModule] || [])
const sceneOptionsForSyncLogQuery = computed(() => sceneOptionsMap[syncLogQuery.bizModule] || [])
const sceneOptionsForCallbackLogQuery = computed(() => sceneOptionsMap[callbackLogQuery.bizModule] || [])
const sceneOptionsForFileQuery = computed(() => sceneOptionsMap[fileQuery.bizModule] || [])
const sceneOptionsForHealthQuery = computed(() => sceneOptionsMap[healthQuery.bizModule] || [])
const moduleConfigMap = computed(() => new Map(moduleConfigOptions.value.map(item => [item.id, item])))
const selectedFieldConfig = computed(() => {
  if (!fieldForm.configId) {
    return null
  }
  return moduleConfigMap.value.get(fieldForm.configId) || null
})
const sceneFieldOptions = computed(() => getStandardFieldOptions(selectedFieldConfig.value?.bizModule, selectedFieldConfig.value?.bizScene))
const fieldTargetOptions = computed(() => {
  const options = [...sceneFieldOptions.value]
  const currentValue = fieldForm.targetField?.trim()
  if (currentValue && !options.some(item => item.value === currentValue)) {
    options.push({
      value: currentValue,
      label: '当前历史值',
      description: '当前值不在预置标准字段字典中，请核对历史配置或接入场景',
      usage: '如需保持历史值，可直接保存；如需改成当前场景推荐字段，请重新选择'
    })
  }
  return options
})
const selectedStatusConfig = computed(() => {
  if (!statusForm.configId) {
    return null
  }
  return moduleConfigMap.value.get(statusForm.configId) || null
})
const selectedStatusQueryConfig = computed(() => {
  if (!statusQuery.configId) {
    return null
  }
  return moduleConfigMap.value.get(statusQuery.configId) || null
})
const sceneStatusOptions = computed(() => getStandardStatusOptions(selectedStatusConfig.value?.bizModule, selectedStatusConfig.value?.bizScene))
const statusTargetOptions = computed(() => {
  const options = [...sceneStatusOptions.value]
  const currentValue = statusForm.targetStatusCode?.trim()
  if (currentValue && !options.some(item => item.value === currentValue)) {
    options.push({
      value: currentValue,
      label: '历史异常值（仅供清理）',
      description: '当前值不在当前场景允许的系统标准状态字典中，系统不再允许继续按启用状态保存。',
      usage: '如需清理历史问题，请改选合法状态，或先把该记录停用/删除。'
    })
  }
  return options
})
const statusSceneSupportsPresetStates = computed(() => sceneStatusOptions.value.length > 0)
const statusTargetIsHistoricalLegacy = computed(() => {
  const currentValue = statusForm.targetStatusCode?.trim()
  return Boolean(currentValue && !sceneStatusOptions.value.some(item => item.value === currentValue))
})
const REQUEST_TEMPLATE_JSON_ERROR = '请求模板JSON格式不正确，请检查括号、逗号、引号是否规范'
const RESPONSE_TEMPLATE_JSON_ERROR = '响应模板JSON格式不正确，请检查括号、逗号、引号是否规范'
const TEMPLATE_VARIABLE_ERROR = '模板内存在不合法动态变量表达式，请修正${xxx}格式'

watch(
  () => moduleConfigForm.bizModule,
  () => {
    if (!sceneOptionsForConfig.value.some(item => item.value === moduleConfigForm.bizScene)) {
      moduleConfigForm.bizScene = ''
    }
  }
)

watch(
  () => moduleConfigForm.forceThirdParty,
  value => {
    if (value === 1) {
      moduleConfigForm.defaultMode = 'third_party'
      moduleConfigForm.allowDocumentSwitch = 0
    }
  }
)

watch(
  () => moduleConfigForm.triggerStrategy,
  value => {
    if (value === 'callback') {
      moduleConfigForm.callbackEnabled = 1
    }
  }
)

watch(
  () => taskForm.bizModule,
  () => {
    if (!sceneOptionsForTask.value.some(item => item.value === taskForm.bizScene)) {
      taskForm.bizScene = ''
    }
  }
)

watch(
  () => taskForm.configId,
  (value) => {
    if (!value) return
    const target = moduleConfigOptions.value.find(item => item.id === value)
    if (!target) return
    taskForm.bizModule = target.bizModule
    taskForm.bizScene = target.bizScene
  }
)

watch(
  () => taskQuery.taskStatus,
  value => {
    if (value) {
      taskQuery.pendingHandleOnly = 0
    }
  }
)

watch(
  () => fieldForm.configId,
  (value, oldValue) => {
    if (!value) {
      fieldForm.targetField = ''
      return
    }
    if (value === oldValue) {
      return
    }
    const target = moduleConfigMap.value.get(value)
    const nextOptions = getStandardFieldOptions(target?.bizModule, target?.bizScene)
    if (!nextOptions.some(item => item.value === fieldForm.targetField)) {
      fieldForm.targetField = ''
    }
  }
)

watch(
  () => statusForm.configId,
  (value, oldValue) => {
    if (!value) {
      statusForm.targetStatusCode = ''
      return
    }
    if (value === oldValue) {
      return
    }
    const target = moduleConfigMap.value.get(value)
    const nextOptions = getStandardStatusOptions(target?.bizModule, target?.bizScene)
    if (!nextOptions.some(item => item.value === statusForm.targetStatusCode)) {
      statusForm.targetStatusCode = ''
    }
  }
)

watch(
  () => route.query.tab,
  () => {
    const nextTab = resolveDefaultTab()
    if (activeTab.value !== nextTab) {
      activeTab.value = nextTab
    }
  }
)

watch(
  () => syncLogQuery.bizModule,
  () => {
    if (!sceneOptionsForSyncLogQuery.value.some(item => item.value === syncLogQuery.bizScene)) {
      syncLogQuery.bizScene = ''
    }
  }
)

watch(
  () => callbackLogQuery.bizModule,
  () => {
    if (!sceneOptionsForCallbackLogQuery.value.some(item => item.value === callbackLogQuery.bizScene)) {
      callbackLogQuery.bizScene = ''
    }
  }
)

watch(
  () => fileQuery.bizModule,
  () => {
    if (!sceneOptionsForFileQuery.value.some(item => item.value === fileQuery.bizScene)) {
      fileQuery.bizScene = ''
    }
  }
)

watch(
  () => healthQuery.bizModule,
  () => {
    if (!sceneOptionsForHealthQuery.value.some(item => item.value === healthQuery.bizScene)) {
      healthQuery.bizScene = ''
    }
  }
)

watch(
  () => [hasIntegrationAccess.value, hasSyncLogAccess.value, hasCallbackLogAccess.value, hasAiConfigAccess.value],
  () => {
    const nextTab = resolveDefaultTab()
    if (activeTab.value !== nextTab) {
      activeTab.value = nextTab
    }
  }
)

watch(
  activeTab,
  (tab) => {
    const nextQuery = { ...route.query }
    const nextRouteTab = resolveRouteTabValue(tab)
    if (nextRouteTab) {
      nextQuery.tab = nextRouteTab
    } else {
      delete nextQuery.tab
    }
    const currentRouteTab = typeof route.query.tab === 'string' ? route.query.tab : ''
    const normalizedNextRouteTab = typeof nextQuery.tab === 'string' ? nextQuery.tab : ''
    if (currentRouteTab !== normalizedNextRouteTab) {
      router.replace({ path: '/integration-management', query: nextQuery })
    }
    if (tab === 'aiConfig') {
      return
    }
    const loaderMap: Record<string, () => Promise<void>> = {
      overview: loadOverview,
      providers: loadProviders,
      configs: loadModuleConfigs,
      fieldMappings: loadFieldMappings,
      statusMappings: loadStatusMappings,
      tasks: loadSyncTasks,
      syncLogs: loadSyncLogs,
      callbackLogs: loadCallbackLogs,
      files: loadFileRecords,
      health: loadHealthChecks
    }
    const loader = loaderMap[tab]
    if (!loader) {
      return
    }
    if (tab === 'syncLogs') {
      if (!hasSyncLogAccess.value) {
        return
      }
      void runSafeIntegrationTask('loadSyncLogProviderOptions', loadSyncLogProviderOptions)
      void runSafeIntegrationTask(`loadTab:${tab}`, loader)
      return
    }
    if (tab === 'callbackLogs') {
      if (!hasCallbackLogAccess.value) {
        return
      }
      void runSafeIntegrationTask('loadCallbackLogProviderOptions', loadCallbackLogProviderOptions)
      void runSafeIntegrationTask(`loadTab:${tab}`, loader)
      return
    }
    if (tab === 'files') {
      if (!hasIntegrationAccess.value) {
        return
      }
      void runSafeIntegrationTask('loadFileRecordProviderOptions', loadFileRecordProviderOptions)
      void runSafeIntegrationTask(`loadTab:${tab}`, loader)
      return
    }
    if (hasIntegrationAccess.value) {
      void runSafeIntegrationTask(`loadTab:${tab}`, loader)
    }
  }
)

onMounted(async () => {
  if (!hasIntegrationAccess.value && !hasSyncLogAccess.value && !hasCallbackLogAccess.value) {
    return
  }
  if (activeTab.value === 'aiConfig') {
    return
  }
  await Promise.allSettled([
    runSafeIntegrationTask('loadOrgTree', loadOrgTree),
    hasIntegrationAccess.value ? runSafeIntegrationTask('loadProviderOptions', loadProviderOptions) : Promise.resolve(),
    hasIntegrationAccess.value ? runSafeIntegrationTask('loadModuleConfigOptions', loadModuleConfigOptions) : Promise.resolve(),
    hasSyncLogAccess.value ? runSafeIntegrationTask('loadSyncLogProviderOptions', loadSyncLogProviderOptions) : Promise.resolve(),
    hasCallbackLogAccess.value ? runSafeIntegrationTask('loadCallbackLogProviderOptions', loadCallbackLogProviderOptions) : Promise.resolve(),
    hasIntegrationAccess.value ? runSafeIntegrationTask('loadFileRecordProviderOptions', loadFileRecordProviderOptions) : Promise.resolve()
  ])
  const loaderMap: Record<string, () => Promise<void>> = {
    overview: loadOverview,
    providers: loadProviders,
    configs: loadModuleConfigs,
    fieldMappings: loadFieldMappings,
    statusMappings: loadStatusMappings,
    tasks: loadSyncTasks,
    syncLogs: loadSyncLogs,
    callbackLogs: loadCallbackLogs,
    files: loadFileRecords,
    health: loadHealthChecks
  }
  const loader = loaderMap[activeTab.value] || loadOverview
  await runSafeIntegrationTask(`loadTab:${activeTab.value}`, loader)
})

async function refreshAllIntegrationData() {
  if (!hasIntegrationAccess.value && !hasSyncLogAccess.value && !hasCallbackLogAccess.value) {
    return
  }
  const tasks: Array<Promise<unknown>> = [loadOrgTree()]
  if (hasIntegrationAccess.value) {
    tasks.push(
      loadOverview(),
      loadProviders(),
      loadModuleConfigs(),
      loadFieldMappings(),
      loadStatusMappings(),
      loadSyncTasks(),
      loadCallbackLogs(),
      loadFileRecords(),
      loadHealthChecks(),
      loadHealthCheckProviderOptions(),
      loadProviderOptions(),
      loadModuleConfigOptions(),
      loadFileRecordProviderOptions()
    )
  }
  if (hasSyncLogAccess.value) {
    tasks.push(loadSyncLogs(), loadSyncLogProviderOptions())
  }
  if (hasCallbackLogAccess.value) {
    tasks.push(loadCallbackLogs(), loadCallbackLogProviderOptions())
  }
  await Promise.allSettled(tasks)
}

async function runSafeIntegrationTask(taskName: string, task: () => Promise<void>) {
  try {
    await task()
  } catch (error) {
    console.error(`[integration-management] ${taskName} failed`, error)
  }
}

async function loadOrgTree() {
  const res = await orgApi.getTree({ includeChildren: true })
  if (res.code === 'SUCCESS' && res.data) {
    orgTree.value = res.data
  }
}

async function loadProviderOptions() {
  const res = await integrationApi.pageProviders({ pageNum: 1, pageSize: 200, status: 'active' })
  if (res.code === 'SUCCESS' && res.data) {
    providerOptions.value = res.data.list
  }
}

async function loadSyncLogProviderOptions() {
  const res = await integrationApi.getSyncLogProviderOptions()
  if (res.code === 'SUCCESS' && res.data) {
    syncLogProviderOptions.value = res.data
  }
}

async function loadCallbackLogProviderOptions() {
  const res = await integrationApi.getCallbackLogProviderOptions()
  if (res.code === 'SUCCESS' && res.data) {
    callbackLogProviderOptions.value = res.data
  }
}

async function loadFileRecordProviderOptions() {
  const res = await integrationApi.getFileRecordProviderOptions()
  if (res.code === 'SUCCESS' && res.data) {
    fileRecordProviderOptions.value = res.data
  }
}

async function loadHealthCheckProviderOptions() {
  const res = await integrationApi.getHealthCheckProviderOptions()
  if (res.code === 'SUCCESS' && res.data) {
    healthProviderOptions.value = res.data
  }
}

async function loadModuleConfigOptions() {
  const res = await integrationApi.pageModuleConfigs({ pageNum: 1, pageSize: 200 })
  if (res.code === 'SUCCESS' && res.data) {
    moduleConfigOptions.value = res.data.list
  }
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res = await integrationApi.getOverview(overviewQuery.orgId)
    if (res.code === 'SUCCESS' && res.data) {
      overview.value = res.data
    }
  } finally {
    overviewLoading.value = false
  }
}

async function loadProviders() {
  providerLoading.value = true
  try {
    const res = await integrationApi.pageProviders(providerQuery)
    if (res.code === 'SUCCESS' && res.data) {
      providerList.value = res.data.list
      providerTotal.value = res.data.total
    }
  } finally {
    providerLoading.value = false
  }
}

async function openProviderDetail(row: IntegrationProviderTemplateItem) {
  const res = await integrationApi.getProvider(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentProviderDetail.value = res.data
    providerDetailVisible.value = true
  }
}

function openCreateProvider() {
  providerDialogMode.value = 'create'
  currentProviderId.value = null
  Object.assign(providerForm, createProviderForm())
  resetProviderTemplateErrors()
  providerDialogVisible.value = true
}

function applyProviderFormFromRow(row: IntegrationProviderTemplateItem, overrides: Partial<IntegrationProviderTemplateForm> = {}) {
  Object.assign(providerForm, {
    providerCode: row.providerCode,
    providerName: row.providerName,
    providerType: row.providerType,
    authType: row.authType,
    protocolType: row.protocolType,
    callbackSupported: row.callbackSupported,
    filePullSupported: row.filePullSupported,
    sceneCodes: [...(row.sceneCodeList || [])],
    requestTemplate: row.requestTemplate || '',
    responseTemplate: row.responseTemplate || '',
    status: row.status,
    remark: row.remark || '',
    ...overrides
  })
}

function openEditProvider(row: IntegrationProviderTemplateItem) {
  if (row.builtinFlag === 1) {
    ElMessage.warning('系统内置模板不允许编辑，请使用复制为自定义模板')
    return
  }
  providerDialogMode.value = 'edit'
  currentProviderId.value = row.id
  applyProviderFormFromRow(row)
  resetProviderTemplateErrors()
  providerDialogVisible.value = true
}

function openCopyProvider(row: IntegrationProviderTemplateItem) {
  providerDialogMode.value = 'copy'
  currentProviderId.value = null
  applyProviderFormFromRow(row, {
    providerCode: '',
    providerName: `${row.providerName}-自定义`
  })
  resetProviderTemplateErrors()
  providerDialogVisible.value = true
}

async function submitProvider() {
  const missingFieldMessage = resolveProviderBaseFieldMessage()
  if (missingFieldMessage) {
    ElMessage.warning(missingFieldMessage)
    return
  }
  if (!validateProviderTemplateField('requestTemplate', true) || !validateProviderTemplateField('responseTemplate', true)) {
    return
  }
  providerSaving.value = true
  try {
    const payload = buildProviderPayload()
    const res = currentProviderId.value
      ? await integrationApi.updateProvider(currentProviderId.value, payload)
      : await integrationApi.createProvider(payload)
    if (res.code === 'SUCCESS') {
      ElMessage.success('保存成功')
      providerDialogVisible.value = false
      await Promise.all([loadProviders(), loadProviderOptions()])
    }
  } catch (error) {
    console.error('保存平台模板失败', error)
  } finally {
    providerSaving.value = false
  }
}

async function changeProviderStatus(row: IntegrationProviderTemplateItem) {
  if (row.builtinFlag === 1) {
    ElMessage.warning('系统内置模板不允许启用或停用')
    return
  }
  const targetStatus = row.status === 'active' ? 'inactive' : 'active'
  const res = await integrationApi.changeProviderStatus(row.id, targetStatus)
  if (res.code === 'SUCCESS') {
    ElMessage.success(targetStatus === 'active' ? '已启用' : '已停用')
    await Promise.all([loadProviders(), loadProviderOptions()])
  }
}

async function removeProvider(row: IntegrationProviderTemplateItem) {
  await ElMessageBox.confirm(`确认删除平台模板「${row.providerName}」吗？`, '删除确认', { type: 'warning' })
  const res = await integrationApi.removeProvider(row.id)
  if (res.code === 'SUCCESS') {
    ElMessage.success('删除成功')
    await Promise.all([loadProviders(), loadProviderOptions()])
  }
}

async function loadModuleConfigs() {
  configLoading.value = true
  try {
    const res = await integrationApi.pageModuleConfigs(moduleConfigQuery)
    if (res.code === 'SUCCESS' && res.data) {
      moduleConfigList.value = res.data.list
      moduleConfigTotal.value = res.data.total
    }
  } finally {
    configLoading.value = false
  }
}

async function openConfigDetail(row: IntegrationModuleConfigItem) {
  const res = await integrationApi.getModuleConfig(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentConfigDetail.value = res.data
    configDetailVisible.value = true
  }
}

function openCreateConfig() {
  currentConfigId.value = null
  Object.assign(moduleConfigForm, createModuleConfigForm())
  configDialogVisible.value = true
}

async function openEditConfig(row: IntegrationModuleConfigItem) {
  currentConfigId.value = row.id
  const res = await integrationApi.getModuleConfig(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    const data = res.data
    Object.assign(moduleConfigForm, {
      orgId: data.orgId,
      bizModule: data.bizModule,
      bizScene: data.bizScene,
      providerCode: data.providerCode,
      configName: data.configName,
      enabled: data.enabled,
      defaultMode: data.defaultMode,
      allowDocumentSwitch: data.allowDocumentSwitch,
      forceThirdParty: data.forceThirdParty,
      triggerStrategy: data.triggerStrategy,
      allowManualFallback: data.allowManualFallback,
      autoCoverEnabled: data.autoCoverEnabled,
      autoCoverStrategy: data.autoCoverStrategy || 'merge',
      allowManualConfirmCover: data.allowManualConfirmCover,
      attachmentPullEnabled: data.attachmentPullEnabled,
      callbackEnabled: data.callbackEnabled,
      callbackUrl: data.callbackUrl || '',
      externalNoFieldRule: data.externalNoFieldRule || '',
      accessTokenUrl: data.accessTokenUrl || '',
      refreshTokenUrl: data.refreshTokenUrl || '',
      tokenRequestMethod: data.tokenRequestMethod || 'POST',
      syncFrequencyMinutes: data.syncFrequencyMinutes || 60,
      scheduleCron: data.scheduleCron || '',
      timeoutMs: data.timeoutMs || 10000,
      retryMaxCount: data.retryMaxCount || 3,
      remark: data.remark || '',
      secrets: (data.secrets || []).map(item => ({
        secretKey: item.secretKey,
        secretValue: ''
      }))
    })
    if (!moduleConfigForm.secrets.length) {
      moduleConfigForm.secrets.push({ secretKey: '', secretValue: '' })
    }
    configDialogVisible.value = true
  }
}

function addSecretRow() {
  moduleConfigForm.secrets.push({ secretKey: '', secretValue: '' })
}

function removeSecretRow(index: number) {
  moduleConfigForm.secrets.splice(index, 1)
  if (!moduleConfigForm.secrets.length) {
    moduleConfigForm.secrets.push({ secretKey: '', secretValue: '' })
  }
}

async function submitConfig() {
  const validationMessage = resolveModuleConfigValidationMessage()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  configSaving.value = true
  try {
    const payload = buildModuleConfigPayload()
    const res = currentConfigId.value
      ? await integrationApi.updateModuleConfig(currentConfigId.value, payload, { silentError: true })
      : await integrationApi.createModuleConfig(payload, { silentError: true })
    if (res.code === 'SUCCESS') {
      ElMessage.success('保存成功')
      configDialogVisible.value = false
      await Promise.all([loadModuleConfigs(), loadModuleConfigOptions()])
    }
  } catch (error) {
    ElMessage.warning(resolveRequestErrorMessage(error, '模块接入配置保存失败，请稍后重试'))
  } finally {
    configSaving.value = false
  }
}

async function changeConfigStatus(row: IntegrationModuleConfigItem) {
  const target = row.enabled === 1 ? 0 : 1
  const res = await integrationApi.changeModuleConfigStatus(row.id, target)
  if (res.code === 'SUCCESS') {
    ElMessage.success(target === 1 ? '已启用' : '已停用')
    await Promise.all([loadModuleConfigs(), loadModuleConfigOptions()])
  }
}

async function testConfig(row: IntegrationModuleConfigItem) {
  const res = await integrationApi.testModuleConfig(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    ElMessage[healthTestMessageType(res.data.lastTestStatus)](res.data.testMessage || '执行完成')
    await Promise.all([loadModuleConfigs(), loadHealthChecks()])
  }
}

async function removeConfig(row: IntegrationModuleConfigItem) {
  await ElMessageBox.confirm(`确认删除接入配置「${row.configName}」吗？`, '删除确认', { type: 'warning' })
  const res = await integrationApi.removeModuleConfig(row.id)
  if (res.code === 'SUCCESS') {
    ElMessage.success('删除成功')
    await Promise.all([loadModuleConfigs(), loadModuleConfigOptions()])
  }
}

async function loadFieldMappings() {
  fieldLoading.value = true
  try {
    const res = await integrationApi.pageFieldMappings(fieldQuery)
    if (res.code === 'SUCCESS' && res.data) {
      fieldList.value = res.data.list
      fieldTotal.value = res.data.total
    }
  } finally {
    fieldLoading.value = false
  }
}

async function openFieldDetail(row: IntegrationFieldMappingItem) {
  const res = await integrationApi.getFieldMapping(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentFieldDetail.value = res.data
    fieldDetailVisible.value = true
  }
}

function openCreateField() {
  currentFieldId.value = null
  Object.assign(fieldForm, createFieldForm())
  fieldDialogVisible.value = true
}

function openEditField(row: IntegrationFieldMappingItem) {
  currentFieldId.value = row.id
  Object.assign(fieldForm, {
    configId: row.configId,
    sourceField: row.sourceField || '',
    sourcePath: row.sourcePath || '',
    targetField: row.targetField,
    transformType: row.transformType,
    transformRule: row.transformRule || '',
    defaultValue: row.defaultValue || '',
    requiredFlag: row.requiredFlag,
    sortNo: row.sortNo,
    enabled: row.enabled,
    errorStrategy: row.errorStrategy || 'fail',
    remark: row.remark || ''
  })
  fieldDialogVisible.value = true
}

function resolveRequestErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message?.trim() ? error.message.trim() : fallback
}

function buildFieldMappingPayload(): IntegrationFieldMappingForm {
  return {
    ...fieldForm,
    configId: fieldForm.configId,
    sourceField: fieldForm.sourceField?.trim() || '',
    sourcePath: fieldForm.sourcePath?.trim() || '',
    targetField: fieldForm.targetField?.trim() || '',
    transformType: fieldForm.transformType?.trim() || 'direct',
    transformRule: fieldForm.transformRule?.trim() || '',
    defaultValue: fieldForm.defaultValue?.trim() || '',
    requiredFlag: fieldForm.requiredFlag ?? 0,
    sortNo: fieldForm.sortNo || 0,
    enabled: fieldForm.enabled ?? 1,
    errorStrategy: fieldForm.errorStrategy?.trim() || 'fail',
    remark: fieldForm.remark?.trim() || ''
  }
}

function getStandardFieldValueType(fieldCode?: string): FieldValueType | undefined {
  return fieldCode ? standardFieldValueTypeMap[fieldCode] : undefined
}

function validateSimpleJsonPathInput(path?: string, fieldLabel = 'JSON路径') {
  const normalized = path?.trim()
  if (!normalized) {
    return ''
  }
  const body = normalized.startsWith('$.') ? normalized.slice(2) : normalized.startsWith('$') ? normalized.slice(1) : normalized
  if (!body) {
    return `${fieldLabel}不能为空`
  }
  const parts = body.split('.')
  for (const part of parts) {
    if (!part) {
      return `${fieldLabel}格式不正确，仅支持简单对象路径和数组下标`
    }
    const firstBracket = part.indexOf('[')
    if (firstBracket < 0) {
      if (part.includes(']')) {
        return `${fieldLabel}格式不正确，仅支持简单对象路径和数组下标`
      }
      continue
    }
    const lastBracket = part.lastIndexOf(']')
    if (lastBracket !== part.length - 1 || part.indexOf('[', firstBracket + 1) >= 0 || firstBracket > lastBracket) {
      return `${fieldLabel}格式不正确，仅支持简单对象路径和数组下标`
    }
    const indexText = part.slice(firstBracket + 1, lastBracket)
    if (!/^\d+$/.test(indexText)) {
      return `${fieldLabel}中的数组下标必须是非负整数`
    }
  }
  return ''
}

function parseDictRule(rule: string) {
  const trimmed = rule.trim()
  if (!trimmed) {
    throw new Error('字典映射必须填写转换表达式')
  }
  if (trimmed.startsWith('{')) {
    const parsed = JSON.parse(trimmed)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) || !Object.keys(parsed).length) {
      throw new Error('字典映射表达式必须是非空 JSON 对象')
    }
    return parsed as Record<string, unknown>
  }
  const result: Record<string, string> = {}
  trimmed.split(',').forEach(item => {
    const ruleItem = item.trim()
    if (!ruleItem) return
    const [key, value] = ruleItem.split('=')
    if (!key?.trim() || value === undefined) {
      throw new Error('字典映射表达式格式应为 key=value 或 JSON 对象')
    }
    result[key.trim()] = value.trim()
  })
  if (!Object.keys(result).length) {
    throw new Error('字典映射表达式不能为空')
  }
  return result
}

function normalizeDateTimeInput(value: string) {
  const trimmed = value.trim()
  if (!trimmed) return NaN
  return Date.parse(trimmed.includes('T') ? trimmed : trimmed.replace(' ', 'T'))
}

function validateFieldDefaultValuePayload(payload: IntegrationFieldMappingForm) {
  const defaultValue = payload.defaultValue?.trim()
  if (!defaultValue) {
    return ''
  }
  const targetType = getStandardFieldValueType(payload.targetField)
  if (payload.transformType === 'dict') {
    const dict = parseDictRule(payload.transformRule || '')
    if (!(defaultValue in dict)) {
      return '默认值在字典映射规则中未命中，请检查转换表达式'
    }
  }
  if (payload.transformType === 'number' && Number.isNaN(Number(defaultValue))) {
    return '默认值在数值换算规则下必须是合法数字'
  }
  if (payload.transformType === 'json_path') {
    const transformRuleMessage = validateSimpleJsonPathInput(payload.transformRule, '转换表达式')
    if (transformRuleMessage) {
      return transformRuleMessage
    }
    try {
      JSON.parse(defaultValue)
    } catch {
      return '默认值在 JSON 路径提取规则下必须是合法JSON'
    }
  }
  if (!targetType) {
    return ''
  }
  if (targetType === 'number' && Number.isNaN(Number(defaultValue))) {
    return '当前系统字段要求默认值为合法数字'
  }
  if (targetType === 'long' && !/^-?\d+$/.test(defaultValue)) {
    return '当前系统字段要求默认值为整数'
  }
  if (targetType === 'date' && Number.isNaN(normalizeDateTimeInput(defaultValue))) {
    return '当前系统字段要求默认值为合法日期'
  }
  if (targetType === 'datetime' && Number.isNaN(normalizeDateTimeInput(defaultValue))) {
    return '当前系统字段要求默认值为合法日期时间'
  }
  if (targetType === 'array' && defaultValue.startsWith('[')) {
    try {
      const parsed = JSON.parse(defaultValue)
      if (!Array.isArray(parsed)) {
        return '当前系统字段要求默认值为JSON数组或逗号分隔列表'
      }
    } catch {
      return '当前系统字段要求默认值为JSON数组或逗号分隔列表'
    }
  }
  return ''
}

function resolveFieldValidationMessage(payload: IntegrationFieldMappingForm) {
  if (!payload.configId || !payload.targetField || !payload.transformType) {
    return '请完整填写字段映射'
  }
  if (!payload.sourceField && !payload.sourcePath && !payload.defaultValue) {
    return '第三方字段、JSON路径、默认值不能同时为空，请至少填写一项'
  }
  const pathMessage = validateSimpleJsonPathInput(payload.sourcePath, 'JSON路径')
  if (pathMessage) {
    return pathMessage
  }
  if (payload.transformType === 'direct' && payload.transformRule) {
    return '直接映射不需要填写转换表达式'
  }
  if (payload.transformType === 'dict') {
    try {
      parseDictRule(payload.transformRule || '')
    } catch (error) {
      return resolveRequestErrorMessage(error, '字典映射表达式格式不正确')
    }
  }
  if (payload.transformType === 'number' && payload.transformRule && Number.isNaN(Number(payload.transformRule))) {
    return '数值换算的转换表达式必须是合法数字'
  }
  if (payload.transformType === 'json_path') {
    if (!payload.transformRule) {
      return 'JSON 路径提取必须填写转换表达式'
    }
    const transformRuleMessage = validateSimpleJsonPathInput(payload.transformRule, '转换表达式')
    if (transformRuleMessage) {
      return transformRuleMessage
    }
  }
  const defaultValueMessage = validateFieldDefaultValuePayload(payload)
  if (defaultValueMessage) {
    return defaultValueMessage
  }
  return ''
}

async function submitField() {
  const payload = buildFieldMappingPayload()
  const validationMessage = resolveFieldValidationMessage(payload)
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  fieldSaving.value = true
  try {
    await integrationApi.checkFieldMappingDuplicate({
      id: currentFieldId.value,
      configId: payload.configId,
      targetField: payload.targetField,
      enabled: payload.enabled
    }, { silentError: true })
    const res = currentFieldId.value
      ? await integrationApi.updateFieldMapping(currentFieldId.value, payload, { silentError: true })
      : await integrationApi.createFieldMapping(payload, { silentError: true })
    if (res.code === 'SUCCESS') {
      ElMessage.success('保存成功')
      fieldDialogVisible.value = false
      await loadFieldMappings()
    }
  } catch (error) {
    ElMessage.warning(resolveRequestErrorMessage(error, '字段映射保存失败，请稍后重试'))
  } finally {
    fieldSaving.value = false
  }
}

async function removeField(row: IntegrationFieldMappingItem) {
  await ElMessageBox.confirm(`确认删除字段映射「${row.targetField}」吗？`, '删除确认', { type: 'warning' })
  const res = await integrationApi.removeFieldMapping(row.id)
  if (res.code === 'SUCCESS') {
    ElMessage.success('删除成功')
    await loadFieldMappings()
  }
}

async function loadStatusMappings() {
  statusLoading.value = true
  try {
    const res = await integrationApi.pageStatusMappings(statusQuery)
    if (res.code === 'SUCCESS' && res.data) {
      statusList.value = res.data.list
      statusTotal.value = res.data.total
    }
  } finally {
    statusLoading.value = false
  }
}

async function openStatusDetail(row: IntegrationStatusMappingItem) {
  const res = await integrationApi.getStatusMapping(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentStatusDetail.value = res.data
    statusDetailVisible.value = true
  }
}

function openCreateStatus() {
  currentStatusId.value = null
  statusOriginalTargetStatus.value = ''
  Object.assign(statusForm, createStatusForm())
  statusDialogVisible.value = true
}

function openEditStatus(row: IntegrationStatusMappingItem) {
  currentStatusId.value = row.id
  statusOriginalTargetStatus.value = row.targetStatusCode || ''
  Object.assign(statusForm, {
    configId: row.configId,
    sourceStatusCode: row.sourceStatusCode,
    sourceStatusName: row.sourceStatusName,
    targetStatusCode: row.targetStatusCode,
    finishFlag: row.finishFlag,
    triggerBusinessAction: row.triggerBusinessAction,
    actionCode: row.actionCode || '',
    writeAttachmentFlag: row.writeAttachmentFlag,
    sortNo: row.sortNo,
    enabled: row.enabled,
    remark: row.remark || ''
  })
  statusDialogVisible.value = true
}

function buildStatusMappingPayload(): IntegrationStatusMappingForm {
  return {
    configId: statusForm.configId,
    sourceStatusCode: statusForm.sourceStatusCode.trim(),
    sourceStatusName: statusForm.sourceStatusName.trim(),
    targetStatusCode: statusForm.targetStatusCode.trim(),
    finishFlag: statusForm.finishFlag,
    triggerBusinessAction: statusForm.triggerBusinessAction,
    actionCode: statusForm.triggerBusinessAction === 1 ? statusForm.actionCode?.trim() || '' : '',
    writeAttachmentFlag: 0,
    sortNo: 0,
    enabled: statusForm.enabled,
    remark: statusForm.remark?.trim() || ''
  }
}

function resolveStatusValidationMessage(payload: IntegrationStatusMappingForm) {
  if (!payload.configId || !payload.sourceStatusCode || !payload.sourceStatusName || !payload.targetStatusCode) {
    return '请完整填写状态映射'
  }
  const config = payload.configId ? moduleConfigMap.value.get(payload.configId) : null
  const options = getStandardStatusOptions(config?.bizModule, config?.bizScene)
  const currentAllowed = options.some(item => item.value === payload.targetStatusCode)
  const keepHistoricalDisabledValue = Boolean(
    currentStatusId.value
    && payload.enabled === 0
    && statusOriginalTargetStatus.value.trim()
    && statusOriginalTargetStatus.value.trim() === payload.targetStatusCode
    && !currentAllowed
  )
  if (!options.length && !keepHistoricalDisabledValue) {
    return '当前场景暂无可用的系统标准状态，请优先改配字段映射；状态映射不支持继续新增或启用'
  }
  if (!currentAllowed && !keepHistoricalDisabledValue) {
    return '当前记录使用的是历史异常状态值，不能继续以启用状态保存；请改选当前场景允许的系统标准状态，或先停用/删除该记录'
  }
  if (payload.finishFlag === 1 && !isTerminalStatusForConfig(payload.configId, payload.targetStatusCode)) {
    return '当前系统标准状态不是该场景允许的终态，不能勾选结束同步'
  }
  return ''
}

async function submitStatus() {
  const payload = buildStatusMappingPayload()
  const validationMessage = resolveStatusValidationMessage(payload)
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  statusSaving.value = true
  try {
    await integrationApi.checkStatusMappingDuplicate({
      id: currentStatusId.value,
      configId: payload.configId,
      sourceStatusCode: payload.sourceStatusCode
    }, { silentError: true })
    const res = currentStatusId.value
      ? await integrationApi.updateStatusMapping(currentStatusId.value, payload, { silentError: true })
      : await integrationApi.createStatusMapping(payload, { silentError: true })
    if (res.code === 'SUCCESS') {
      ElMessage.success('保存成功')
      statusDialogVisible.value = false
      await loadStatusMappings()
    }
  } catch (error) {
    ElMessage.warning(resolveRequestErrorMessage(error, '状态映射保存失败，请稍后重试'))
  } finally {
    statusSaving.value = false
  }
}

async function removeStatus(row: IntegrationStatusMappingItem) {
  await ElMessageBox.confirm(`确认删除状态映射「${row.sourceStatusCode}」吗？`, '删除确认', { type: 'warning' })
  const res = await integrationApi.removeStatusMapping(row.id)
  if (res.code === 'SUCCESS') {
    ElMessage.success('删除成功')
    await loadStatusMappings()
  }
}

async function openMappingMissingLogs() {
  const config = statusQuery.configId ? moduleConfigMap.value.get(statusQuery.configId) : null
  syncLogQuery.pageNum = 1
  syncLogQuery.syncStatus = 'mapping_missing'
  syncLogQuery.keyword = ''
  syncLogQuery.providerCode = config?.providerCode || ''
  syncLogQuery.bizModule = config?.bizModule || ''
  syncLogQuery.bizScene = config?.bizScene || ''
  activeTab.value = 'syncLogs'
  await loadSyncLogs()
}

async function loadSyncTasks() {
  taskLoading.value = true
  try {
    const res = await integrationApi.pageSyncTasks(taskQuery)
    if (res.code === 'SUCCESS' && res.data) {
      taskList.value = res.data.list
      taskTotal.value = res.data.total
    }
  } finally {
    taskLoading.value = false
  }
}

async function openTaskDetail(row: IntegrationSyncTaskItem) {
  const res = await integrationApi.getSyncTask(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentTaskDetail.value = res.data
    taskDetailVisible.value = true
  }
}

function togglePendingHandleTasks() {
  taskQuery.pageNum = 1
  taskQuery.pendingHandleOnly = taskQuery.pendingHandleOnly === 1 ? 0 : 1
  if (taskQuery.pendingHandleOnly === 1) {
    taskQuery.taskStatus = ''
  }
  loadSyncTasks()
}

function openTriggerTask() {
  Object.assign(taskForm, createTaskForm())
  taskDialogVisible.value = true
}

function resolveTaskValidationMessage() {
  const bizId = taskForm.bizId?.trim?.() || ''
  if (!taskForm.configId || !taskForm.bizModule || !taskForm.bizScene || !bizId || !taskForm.bizNo.trim() || !taskForm.externalNo.trim()) {
    return '请完整填写同步任务信息'
  }
  if (!/^[1-9]\d*$/.test(bizId)) {
    return '业务主键ID仅支持大于0的正整数'
  }
  if (!['manual', 'third_party'].includes(taskForm.maintenanceMode)) {
    return '维护方式仅支持手工模式或第三方模式'
  }
  if (!['user_selected', 'business_scene'].includes(taskForm.modeSource)) {
    return '方式来源仅支持“用户手动选择”或“业务场景默认”'
  }
  if (![0, 1].includes(taskForm.modeLocked) || ![0, 1].includes(taskForm.queryOnly)) {
    return '锁定方式、只查询不覆盖仅支持开或关两种取值'
  }
  return ''
}

async function submitTask() {
  const validationMessage = resolveTaskValidationMessage()
  if (validationMessage) {
    ElMessage.warning(validationMessage)
    return
  }
  taskForm.bizId = taskForm.bizId.trim()
  taskForm.bizNo = taskForm.bizNo.trim()
  taskForm.externalNo = taskForm.externalNo.trim()
  taskSubmitting.value = true
  try {
    const res = await integrationApi.triggerSync(taskForm)
    if (res.code === 'SUCCESS' && res.data) {
      taskDialogVisible.value = false
      showExecutionResult(res.data)
      await Promise.all([loadSyncTasks(), loadSyncLogs(), loadHealthChecks(), loadModuleConfigs()])
    }
  } finally {
    taskSubmitting.value = false
  }
}

function canRetryTask(row: IntegrationSyncTaskItem) {
  return Boolean(canRetry.value && row.retryAvailable)
}

async function retryTask(row: IntegrationSyncTaskItem) {
  const res = await integrationApi.retrySyncTask(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    showExecutionResult(res.data)
    await Promise.all([loadSyncTasks(), loadSyncLogs(), loadHealthChecks(), loadModuleConfigs()])
  }
}

async function loadSyncLogs() {
  syncLogQuery.startTime = syncLogTimeRange.value?.[0] || ''
  syncLogQuery.endTime = syncLogTimeRange.value?.[1] || ''
  syncLogLoading.value = true
  try {
    const res = await integrationApi.pageSyncLogs(syncLogQuery)
    if (res.code === 'SUCCESS' && res.data) {
      syncLogs.value = res.data.list
      syncLogTotal.value = res.data.total
    }
  } finally {
    syncLogLoading.value = false
  }
}

async function openSyncLogDetail(row: IntegrationSyncLogItem) {
  const res = await integrationApi.getSyncLog(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentSyncLog.value = res.data
    syncLogDetailVisible.value = true
  }
}

async function saveSyncLogHandleStatus() {
  if (!currentSyncLog.value) {
    return
  }
  const logId = currentSyncLog.value.id
  const payload: IntegrationSyncLogHandleForm = {
    handleStatus: currentSyncLog.value.handleStatus || '',
    handleRemark: currentSyncLog.value.handleRemark?.trim() || ''
  }
  if (!payload.handleStatus) {
    ElMessage.warning('请选择处理状态')
    return
  }
  const res = await integrationApi.updateSyncLogHandleStatus(logId, payload)
  if (res.code === 'SUCCESS') {
    ElMessage.success('处理状态已更新')
    await Promise.all([loadSyncLogs(), integrationApi.getSyncLog(logId).then(detailRes => {
      if (detailRes.code === 'SUCCESS' && detailRes.data) {
        currentSyncLog.value = detailRes.data
      }
    })])
  }
}

async function loadCallbackLogs() {
  callbackLogQuery.startTime = callbackLogTimeRange.value?.[0] || ''
  callbackLogQuery.endTime = callbackLogTimeRange.value?.[1] || ''
  callbackLogLoading.value = true
  try {
    const res = await integrationApi.pageCallbackLogs(callbackLogQuery)
    if (res.code === 'SUCCESS' && res.data) {
      callbackLogs.value = res.data.list
      callbackLogTotal.value = res.data.total
    }
  } finally {
    callbackLogLoading.value = false
  }
}

async function openCallbackLogDetail(row: IntegrationCallbackLogItem) {
  const res = await integrationApi.getCallbackLog(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentCallbackLog.value = res.data
    callbackLogDetailVisible.value = true
  }
}

async function loadFileRecords() {
  fileQuery.startTime = fileTimeRange.value?.[0] || ''
  fileQuery.endTime = fileTimeRange.value?.[1] || ''
  fileLoading.value = true
  try {
    const res = await integrationApi.pageFileRecords(fileQuery)
    if (res.code === 'SUCCESS' && res.data) {
      fileRecords.value = res.data.list
      fileTotal.value = res.data.total
    }
  } finally {
    fileLoading.value = false
  }
}

async function openFileDetail(row: IntegrationFileRecordItem) {
  const res = await integrationApi.getFileRecord(row.id)
  if (res.code === 'SUCCESS' && res.data) {
    currentFileDetail.value = res.data
    fileDetailVisible.value = true
  }
}

async function loadHealthChecks() {
  healthLoading.value = true
  try {
    const res = await integrationApi.pageHealthChecks(healthQuery)
    if (res.code === 'SUCCESS' && res.data) {
      healthChecks.value = res.data.list
      healthTotal.value = res.data.total
    }
  } finally {
    healthLoading.value = false
  }
}

async function openHealthDetail(row: IntegrationHealthCheckItem) {
  const res = await integrationApi.getHealthCheck(row.configId)
  if (res.code === 'SUCCESS' && res.data) {
    currentHealthDetail.value = res.data
    healthDetailVisible.value = true
  }
}

async function testHealth(row: IntegrationHealthCheckItem) {
  const res = await integrationApi.testModuleConfig(row.configId)
  if (res.code === 'SUCCESS' && res.data) {
    ElMessage[healthTestMessageType(res.data.lastTestStatus)](res.data.testMessage || '执行完成')
    await Promise.all([loadHealthChecks(), loadModuleConfigs()])
  }
}

function showExecutionResult(result: IntegrationExecutionResult) {
  ElMessageBox.alert(
    `<div style="line-height:1.8;">
      <div><strong>执行状态：</strong>${result.syncStatus || '-'}</div>
      <div><strong>结果说明：</strong>${result.message || '-'}</div>
      <div><strong>任务编号：</strong>${result.taskNo || '-'}</div>
      <div><strong>可用附件数：</strong>${result.downloadedFileCount ?? 0}</div>
      <div><strong>附件失败数：</strong>${result.failedFileCount ?? 0}</div>
      <div><strong>附件跳过数：</strong>${result.skippedFileCount ?? 0}</div>
      <div><strong>附件复用数：</strong>${result.reusedFileCount ?? 0}</div>
      <div><strong>附件处理摘要：</strong>${result.fileTransferSummary || '-'}</div>
      <div><strong>业务回写结果：</strong>${result.writeBackResult || '-'}</div>
      <div><strong>同步日志ID：</strong>${result.syncLogId ?? '-'}</div>
      <div><strong>审计日志ID：</strong>${result.auditLogId ?? '-'}</div>
      <div><strong>标准化结果：</strong><pre style="white-space:pre-wrap;max-height:240px;overflow:auto;background:#f7f8fa;padding:8px;border-radius:6px;">${escapeHtml(result.normalizedPayload || '-')}</pre></div>
    </div>`,
    '执行结果',
    { dangerouslyUseHTMLString: true }
  )
}

function flattenOrgTree(list: OrgTreeNode[], level = 0): Array<{ label: string; value: number }> {
  const result: Array<{ label: string; value: number }> = []
  list.forEach(item => {
    result.push({
      label: `${'  '.repeat(level)}${item.orgName}`,
      value: item.id
    })
    if (item.children?.length) {
      result.push(...flattenOrgTree(item.children, level + 1))
    }
  })
  return result
}

function sceneLabel(moduleCode: string, sceneCode: string) {
  if (!moduleCode || !sceneCode) {
    return '—'
  }
  return sceneOptionsMap[moduleCode]?.find(item => item.value === sceneCode)?.label || sceneCode
}

function buildSceneKey(moduleCode?: string, sceneCode?: string) {
  return moduleCode && sceneCode ? `${moduleCode}:${sceneCode}` : ''
}

function getStandardFieldOptions(moduleCode?: string, sceneCode?: string) {
  return standardFieldOptionsMap[buildSceneKey(moduleCode, sceneCode)] || []
}

function getStandardStatusOptions(moduleCode?: string, sceneCode?: string) {
  return standardStatusOptionsMap[buildSceneKey(moduleCode, sceneCode)] || []
}

function standardFieldDisplay(configId?: number | null, targetField?: string) {
  if (!targetField) {
    return '—'
  }
  const config = configId ? moduleConfigMap.value.get(configId) : undefined
  const option = getStandardFieldOptions(config?.bizModule, config?.bizScene).find(item => item.value === targetField)
  return option ? `${targetField}：${option.label}` : targetField
}

function standardStatusDisplay(configId?: number | null, targetStatus?: string) {
  if (!targetStatus) {
    return '—'
  }
  const config = configId ? moduleConfigMap.value.get(configId) : undefined
  const option = getStandardStatusOptions(config?.bizModule, config?.bizScene).find(item => item.value === targetStatus)
  return option ? `${targetStatus}：${option.label}` : `${targetStatus}（历史异常值）`
}

function syncStatusLabel(value?: string) {
  if (!value) {
    return '—'
  }
  return syncStatusOptions.find(item => item.value === value)?.label || value
}

function healthCheckStatusLabel(value?: string) {
  if (!value || value === 'unknown') {
    return '未检测'
  }
  if (value === 'success') {
    return '通过'
  }
  if (value === 'warning') {
    return '告警'
  }
  if (value === 'failed') {
    return '失败'
  }
  return value
}

function healthCheckStatusTagType(value?: string) {
  if (!value || value === 'unknown') {
    return 'info'
  }
  if (value === 'success') {
    return 'success'
  }
  if (value === 'warning') {
    return 'warning'
  }
  if (value === 'failed') {
    return 'danger'
  }
  return 'info'
}

function healthResultLabel(value?: boolean, nullLabel = '未检测') {
  if (value === true) {
    return '正常'
  }
  if (value === false) {
    return '异常'
  }
  return nullLabel
}

function healthResultTagType(value?: boolean) {
  if (value === true) {
    return 'success'
  }
  if (value === false) {
    return 'danger'
  }
  return 'info'
}

function healthTestMessageType(status?: string) {
  if (status === 'success') {
    return 'success'
  }
  if (status === 'warning') {
    return 'warning'
  }
  return 'error'
}

function isTerminalStatusForConfig(configId?: number | null, targetStatus?: string) {
  if (!configId || !targetStatus) {
    return false
  }
  const config = moduleConfigMap.value.get(configId)
  if (!config) {
    return false
  }
  const key = `${config.bizModule}:${config.bizScene}`
  return (terminalStatusValuesMap[key] || []).includes(targetStatus)
}

function bizModuleLabel(moduleCode: string) {
  return bizModuleOptions.find(item => item.value === moduleCode)?.label || detailText(moduleCode)
}

function overviewModuleLabel(item?: IntegrationMetricItem) {
  if (!item) {
    return '—'
  }
  return bizModuleLabel(item.code || item.label)
}

function overviewProviderLabel(item?: IntegrationMetricItem) {
  if (!item) {
    return '—'
  }
  return providerOptionNameMap.value.get(item.code) || detailText(item.label || item.code)
}

function overviewCallbackProviderLabel(item?: IntegrationCallbackLogItem) {
  if (!item) {
    return '—'
  }
  return providerOptionNameMap.value.get(item.providerCode) || detailText(item.providerName || item.providerCode)
}

function providerTypeLabel(code: string) {
  return providerTypeOptions.find(item => item.value === code)?.label || detailText(code)
}

function authTypeLabel(code?: string) {
  return authTypeOptions.find(item => item.value === code)?.label || detailText(code)
}

function protocolTypeLabel(code?: string) {
  return protocolOptions.find(item => item.value === code)?.label || detailText(code)
}

function triggerStrategyLabel(code?: string) {
  return triggerStrategyOptions.find(item => item.value === code)?.label || detailText(code)
}

function transformTypeLabel(code?: string) {
  return transformTypeOptions.find(item => item.value === code)?.label || detailText(code)
}

function fieldErrorStrategyLabel(code?: string) {
  return fieldErrorStrategyOptions.find(item => item.value === code)?.label || detailText(code)
}

function modeLabel(code?: string) {
  return modeOptions.find(item => item.value === code)?.label || detailText(code)
}

function taskModeSourceLabel(code?: string) {
  return taskModeSourceOptions.find(item => item.value === code)?.label || detailText(code)
}

function taskTypeLabel(code?: string) {
  return taskTypeOptions.find(item => item.value === code)?.label || detailText(code)
}

function triggerTypeLabel(code?: string) {
  return triggerTypeOptions.find(item => item.value === code)?.label || detailText(code)
}

function syncLogHandleStatusLabel(code?: string) {
  return syncLogHandleStatusOptions.find(item => item.value === code)?.label || detailText(code)
}

function signResultLabel(code?: string) {
  if (!code) {
    return '—'
  }
  return code === 'pass' ? '通过' : code === 'fail' ? '失败' : code
}

function callbackProcessStatusLabel(code?: string) {
  return callbackProcessStatusOptions.find(item => item.value === code)?.label || detailText(code)
}

function fileRecordStatusLabel(code?: string) {
  return fileRecordStatusOptions.find(item => item.value === code)?.label || detailText(code)
}

function fileTypeLabel(mimeType?: string, fileName?: string) {
  const normalizedMimeType = mimeType?.split(';')[0]?.trim()?.toLowerCase() || ''
  const extension = fileName?.includes('.') ? fileName.split('.').pop()?.trim()?.toLowerCase() || '' : ''
  if (normalizedMimeType === 'application/pdf' || extension === 'pdf') {
    return 'PDF'
  }
  if (normalizedMimeType === 'image/jpeg' || normalizedMimeType === 'image/jpg' || extension === 'jpg' || extension === 'jpeg') {
    return 'JPG图片'
  }
  if (normalizedMimeType === 'image/png' || extension === 'png') {
    return 'PNG图片'
  }
  if (
    normalizedMimeType === 'application/vnd.ms-excel' ||
    normalizedMimeType === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' ||
    extension === 'xls' ||
    extension === 'xlsx'
  ) {
    return 'Excel'
  }
  if (normalizedMimeType === 'text/csv' || extension === 'csv') {
    return 'CSV'
  }
  if (normalizedMimeType === 'text/plain' || extension === 'txt') {
    return 'TXT文本'
  }
  if (normalizedMimeType.startsWith('image/')) {
    return '图片文件'
  }
  if (normalizedMimeType) {
    return '其他文件'
  }
  return '未知类型'
}

function fileRecordSyncResultText(record?: IntegrationFileRecordItem | null) {
  return detailText(record?.syncLogResultMessage || record?.syncLogErrorMessage)
}

function providerSceneLabel(value?: string) {
  if (!value) {
    return '—'
  }
  const [moduleCode, sceneCode] = value.split(':')
  if (moduleCode && sceneCode) {
    return `${bizModuleLabel(moduleCode)} / ${sceneLabel(moduleCode, sceneCode)}`
  }
  return value
}

function detailText(value: unknown) {
  if (value === null || value === undefined) {
    return '—'
  }
  if (Array.isArray(value)) {
    return value.length ? value.join('、') : '—'
  }
  if (typeof value === 'string') {
    return value.trim() ? value : '—'
  }
  return String(value)
}

function detailYesNo(value: unknown, yesLabel = '是', noLabel = '否') {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  if (typeof value === 'boolean') {
    return value ? yesLabel : noLabel
  }
  if (typeof value === 'number') {
    return value === 1 ? yesLabel : noLabel
  }
  if (typeof value === 'string') {
    if (['1', 'true', 'yes', 'active', 'success', 'pass'].includes(value)) {
      return yesLabel
    }
    if (['0', 'false', 'no', 'inactive', 'failed', 'fail'].includes(value)) {
      return noLabel
    }
  }
  return String(value)
}

function formatJsonContent(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) {
      return '—'
    }
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2)
    } catch {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function formatPercent(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  return `${value}%`
}

function escapeHtml(value: string) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function createProviderForm(): IntegrationProviderTemplateForm {
  return {
    providerCode: '',
    providerName: '',
    providerType: 'logistics',
    authType: 'bearer',
    protocolType: 'http',
    callbackSupported: 1,
    filePullSupported: 1,
    sceneCodes: [],
    requestTemplate: '',
    responseTemplate: '',
    status: 'active',
    remark: ''
  }
}

function resetProviderTemplateErrors() {
  providerTemplateErrors.requestTemplate = ''
  providerTemplateErrors.responseTemplate = ''
}

function normalizeTemplateValue(value?: string) {
  const normalized = value?.trim()
  return normalized ? normalized : undefined
}

function resolveProviderBaseFieldMessage() {
  const allowedSceneCodeSet = new Set(providerSceneCodeOptions.map(item => item.value))
  const hasInvalidSceneCode = (providerForm.sceneCodes || []).some(code => !allowedSceneCodeSet.has(code))
  const fieldChecks: Array<{ valid: boolean; label: string }> = [
    { valid: !!providerForm.providerCode?.trim(), label: '平台编码' },
    { valid: !!providerForm.providerName?.trim(), label: '平台名称' },
    { valid: !!providerForm.providerType?.trim(), label: '平台类型' },
    { valid: !!providerForm.authType?.trim(), label: '鉴权类型' },
    { valid: !!providerForm.protocolType?.trim(), label: '协议类型' },
    { valid: Array.isArray(providerForm.sceneCodes) && providerForm.sceneCodes.length > 0, label: '支持场景' },
    { valid: !hasInvalidSceneCode, label: '支持场景取值' }
  ]
  const missingField = fieldChecks.find(item => !item.valid)
  if (!missingField) {
    return ''
  }
  if (missingField.label === '支持场景取值') {
    return '【支持场景】只能选择系统预设业务场景，请勿填写自定义场景码'
  }
  return `【${missingField.label}】为必填项，请完成填写后再保存`
}

function validateTemplateVariables(template: string) {
  const placeholderPattern = /\$\{[^{}]*}/g
  const legalPlaceholderPattern = /^\$\{[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*}$/
  const remainder = template.replace(placeholderPattern, (token) => {
    if (!legalPlaceholderPattern.test(token)) {
      throw new Error(TEMPLATE_VARIABLE_ERROR)
    }
    return ''
  })
  if (remainder.includes('${')) {
    throw new Error(TEMPLATE_VARIABLE_ERROR)
  }
}

function validateProviderTemplateField(field: 'requestTemplate' | 'responseTemplate', showMessage = false) {
  const rawValue = field === 'requestTemplate' ? providerForm.requestTemplate : providerForm.responseTemplate
  const normalizedValue = rawValue?.trim()
  const errorKey = field === 'requestTemplate' ? 'requestTemplate' : 'responseTemplate'
  const jsonErrorMessage = field === 'requestTemplate' ? REQUEST_TEMPLATE_JSON_ERROR : RESPONSE_TEMPLATE_JSON_ERROR
  if (!normalizedValue) {
    providerTemplateErrors[errorKey] = ''
    return true
  }

  try {
    validateTemplateVariables(normalizedValue)
    JSON.parse(normalizedValue)
    providerTemplateErrors[errorKey] = ''
    return true
  } catch (error) {
    const message = error instanceof Error && error.message === TEMPLATE_VARIABLE_ERROR
      ? TEMPLATE_VARIABLE_ERROR
      : jsonErrorMessage
    providerTemplateErrors[errorKey] = message
    if (showMessage) {
      ElMessage.error(message)
    }
    return false
  }
}

function buildProviderPayload(): IntegrationProviderTemplateForm {
  return {
    ...providerForm,
    providerCode: providerForm.providerCode.trim(),
    providerName: providerForm.providerName.trim(),
    providerType: providerForm.providerType.trim(),
    authType: providerForm.authType.trim(),
    protocolType: providerForm.protocolType.trim(),
    sceneCodes: [...new Set((providerForm.sceneCodes || []).map(item => item.trim()).filter(Boolean))],
    requestTemplate: normalizeTemplateValue(providerForm.requestTemplate),
    responseTemplate: normalizeTemplateValue(providerForm.responseTemplate),
    remark: providerForm.remark?.trim() || ''
  }
}

function isValidHttpUrl(url?: string) {
  if (!url?.trim()) {
    return false
  }
  try {
    const parsed = new URL(url.trim())
    return parsed.protocol === 'http:' || parsed.protocol === 'https:'
  } catch {
    return false
  }
}

function callbackUrlTailMatchesProvider(callbackUrl: string, providerCode: string) {
  try {
    const parsed = new URL(callbackUrl.trim())
    const segments = parsed.pathname.split('/').filter(Boolean)
    return segments[segments.length - 1] === providerCode.trim()
  } catch {
    return false
  }
}

function hasSecretKey(secretKeys: Set<string>, candidates: string[]) {
  return candidates.some(key => secretKeys.has(key.toLowerCase()))
}

function resolveModuleConfigValidationMessage() {
  if (!moduleConfigForm.orgId || !moduleConfigForm.bizModule || !moduleConfigForm.bizScene || !moduleConfigForm.providerCode || !moduleConfigForm.configName.trim()) {
    return '请完整填写模块接入配置'
  }
  if (![0, 1].includes(moduleConfigForm.enabled)) {
    return '启用状态仅支持 0 或 1'
  }
  if (!['manual', 'third_party'].includes(moduleConfigForm.defaultMode)) {
    return '默认维护方式仅支持手工或第三方接口'
  }
  if (![0, 1].includes(moduleConfigForm.allowDocumentSwitch)
    || ![0, 1].includes(moduleConfigForm.forceThirdParty)
    || ![0, 1].includes(moduleConfigForm.allowManualFallback)
    || ![0, 1].includes(moduleConfigForm.autoCoverEnabled)
    || ![0, 1].includes(moduleConfigForm.allowManualConfirmCover)
    || ![0, 1].includes(moduleConfigForm.attachmentPullEnabled)
    || ![0, 1].includes(moduleConfigForm.callbackEnabled)) {
    return '配置开关仅支持是/否两种取值，请重新选择'
  }
  if (!['manual', 'scheduler', 'callback'].includes(moduleConfigForm.triggerStrategy)) {
    return '触发策略仅支持手动触发、定时轮询、回调驱动'
  }
  if (!['GET', 'POST'].includes(moduleConfigForm.tokenRequestMethod)) {
    return 'Token请求方式仅支持 GET 或 POST'
  }
  if (!moduleConfigForm.timeoutMs || moduleConfigForm.timeoutMs <= 0) {
    return '超时(ms)必须大于0'
  }
  if (moduleConfigForm.retryMaxCount < 0) {
    return '最大重试次数不能小于0'
  }
  if (!moduleConfigForm.syncFrequencyMinutes || moduleConfigForm.syncFrequencyMinutes <= 0) {
    return '同步频率(分钟)必须大于0'
  }
  if (moduleConfigForm.callbackUrl?.trim() && !isValidHttpUrl(moduleConfigForm.callbackUrl)) {
    return '回调地址必须是有效的 http/https 地址'
  }
  if (moduleConfigForm.accessTokenUrl?.trim() && !isValidHttpUrl(moduleConfigForm.accessTokenUrl)) {
    return 'AccessToken地址必须是有效的 http/https 地址'
  }
  if (moduleConfigForm.refreshTokenUrl?.trim() && !isValidHttpUrl(moduleConfigForm.refreshTokenUrl)) {
    return 'RefreshToken地址必须是有效的 http/https 地址'
  }
  if (moduleConfigForm.callbackUrl?.trim() && !callbackUrlTailMatchesProvider(moduleConfigForm.callbackUrl, moduleConfigForm.providerCode)) {
    return '回调地址最后一段必须与平台编码保持一致'
  }
  if (moduleConfigForm.refreshTokenUrl?.trim() && !moduleConfigForm.accessTokenUrl?.trim()) {
    return '填写 RefreshToken地址 前，必须先填写 AccessToken地址'
  }
  if (moduleConfigForm.scheduleCron?.trim() && moduleConfigForm.triggerStrategy !== 'scheduler') {
    return '仅当触发策略为定时轮询时才允许填写计划Cron'
  }
  if (moduleConfigForm.forceThirdParty === 1 && moduleConfigForm.defaultMode !== 'third_party') {
    return '强制第三方开启时，默认维护方式必须选择第三方接口'
  }
  if (moduleConfigForm.triggerStrategy === 'callback' && moduleConfigForm.callbackEnabled !== 1) {
    return '触发策略为回调驱动时，必须同时启用回调'
  }

  const provider = providerOptions.value.find(item => item.providerCode === moduleConfigForm.providerCode)
  if (moduleConfigForm.callbackEnabled === 1) {
    if (provider && provider.callbackSupported !== 1) {
      return '当前平台模板未开启回调支持，不能启用回调'
    }
    if (!moduleConfigForm.callbackUrl?.trim()) {
      return '启用回调时必须填写回调地址'
    }
  }

  const secretRows = moduleConfigForm.secrets.map(item => ({
    secretKey: item.secretKey.trim(),
    secretValue: item.secretValue
  }))
  const secretKeySet = new Set<string>()
  for (const row of secretRows) {
    if (!row.secretKey && row.secretValue?.trim()) {
      return '存在仅填写了密钥值但未填写参数名的接入密钥'
    }
    if (!row.secretKey) {
      continue
    }
    const normalizedKey = row.secretKey.toLowerCase()
    if (secretKeySet.has(normalizedKey)) {
      return `接入密钥参数名不能重复：${row.secretKey}`
    }
    secretKeySet.add(normalizedKey)
  }

  const authType = provider?.authType || 'bearer'
  if (authType === 'bearer' && !moduleConfigForm.accessTokenUrl?.trim() && !hasSecretKey(secretKeySet, ['accessToken', 'ACCESS_TOKEN', 'token'])) {
    return 'Bearer鉴权至少需要配置固定令牌，或填写 AccessToken地址'
  }
  if (['app_secret', 'oauth2'].includes(authType)) {
    if (!hasSecretKey(secretKeySet, ['clientId', 'appKey', 'ClientId'])) {
      return '缺少必填密钥：鉴权参数 clientId/appKey'
    }
    if (!hasSecretKey(secretKeySet, ['clientSecret', 'appSecret', 'ClientSecret'])) {
      return '缺少必填密钥：鉴权参数 clientSecret/appSecret'
    }
  }
  if (authType === 'oauth2' && !moduleConfigForm.accessTokenUrl?.trim()) {
    return 'OAuth2鉴权必须填写 AccessToken地址'
  }
  if (moduleConfigForm.callbackEnabled === 1) {
    if (!hasSecretKey(secretKeySet, ['callbackIpWhitelist', 'ipWhitelist', 'callbackSourceIps'])) {
      return '缺少必填密钥：回调IP白名单'
    }
    if (!hasSecretKey(secretKeySet, ['callbackSignSecret', 'signatureSecret', 'appSecret', 'clientSecret'])) {
      return '缺少必填密钥：回调签名密钥'
    }
  }
  return ''
}

function buildModuleConfigPayload(): IntegrationModuleConfigForm {
  return {
    ...moduleConfigForm,
    bizModule: moduleConfigForm.bizModule.trim(),
    bizScene: moduleConfigForm.bizScene.trim(),
    providerCode: moduleConfigForm.providerCode.trim(),
    configName: moduleConfigForm.configName.trim(),
    callbackUrl: moduleConfigForm.callbackUrl?.trim() || '',
    externalNoFieldRule: moduleConfigForm.externalNoFieldRule?.trim() || '',
    accessTokenUrl: moduleConfigForm.accessTokenUrl?.trim() || '',
    refreshTokenUrl: moduleConfigForm.refreshTokenUrl?.trim() || '',
    scheduleCron: moduleConfigForm.scheduleCron?.trim() || '',
    remark: moduleConfigForm.remark?.trim() || '',
    secrets: moduleConfigForm.secrets
      .map(item => ({
        secretKey: item.secretKey.trim(),
        secretValue: item.secretValue
      }))
      .filter(item => item.secretKey || item.secretValue?.trim())
  }
}

function createModuleConfigForm(): IntegrationModuleConfigForm {
  return {
    orgId: userStore.userInfo?.orgId || null,
    bizModule: 'purchase_order',
    bizScene: 'logistics',
    providerCode: '',
    configName: '',
    enabled: 1,
    defaultMode: 'manual',
    allowDocumentSwitch: 1,
    forceThirdParty: 0,
    triggerStrategy: 'manual',
    allowManualFallback: 1,
    autoCoverEnabled: 0,
    autoCoverStrategy: 'merge',
    allowManualConfirmCover: 1,
    attachmentPullEnabled: 0,
    callbackEnabled: 0,
    callbackUrl: '',
    externalNoFieldRule: '',
    accessTokenUrl: '',
    refreshTokenUrl: '',
    tokenRequestMethod: 'POST',
    syncFrequencyMinutes: 60,
    scheduleCron: '',
    timeoutMs: 10000,
    retryMaxCount: 3,
    remark: '',
    secrets: [{ secretKey: '', secretValue: '' }]
  }
}

function createFieldForm(): IntegrationFieldMappingForm {
  return {
    configId: undefined as unknown as number,
    sourceField: '',
    sourcePath: '',
    targetField: '',
    transformType: 'direct',
    transformRule: '',
    defaultValue: '',
    requiredFlag: 0,
    sortNo: 0,
    enabled: 1,
    errorStrategy: 'fail',
    remark: ''
  }
}

function createStatusForm(): IntegrationStatusMappingForm {
  return {
    configId: undefined as unknown as number,
    sourceStatusCode: '',
    sourceStatusName: '',
    targetStatusCode: '',
    finishFlag: 0,
    triggerBusinessAction: 0,
    actionCode: '',
    writeAttachmentFlag: 0,
    sortNo: 0,
    enabled: 1,
    remark: ''
  }
}

function createTaskForm(): IntegrationSyncTriggerForm {
  return {
    configId: undefined as unknown as number,
    bizModule: 'purchase_order',
    bizScene: 'logistics',
    bizId: '',
    bizNo: '',
    externalNo: '',
    maintenanceMode: 'third_party',
    modeSource: 'user_selected',
    modeLocked: 0,
    triggerType: 'manual',
    queryOnly: 0
  }
}
</script>

<template>
  <div class="integration-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane v-if="hasIntegrationAccess" label="接入总览" name="overview">
        <div class="toolbar">
          <el-select v-model="overviewQuery.orgId" clearable placeholder="所属组织" style="width: 240px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-button type="primary" @click="loadOverview">刷新</el-button>
        </div>

        <div v-loading="overviewLoading">
          <div class="stat-grid">
            <div class="stat-card">
              <div class="stat-label">已启用接入数</div>
              <div class="stat-value">{{ overview?.enabledIntegrationCount ?? 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">同步成功数</div>
              <div class="stat-value">{{ overview?.syncSuccessCount ?? 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">同步失败数</div>
              <div class="stat-value danger">{{ overview?.syncFailureCount ?? 0 }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">回调成功率</div>
              <div class="stat-value">{{ overview?.callbackSuccessRate ?? 0 }}%</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">平均耗时</div>
              <div class="stat-value">{{ overview?.averageDurationMs ?? 0 }}ms</div>
            </div>
          </div>

          <div class="triple-grid">
            <el-card shadow="never">
              <template #header>模块接入分布</template>
              <el-table :data="overview?.moduleDistribution || []" size="small" border>
                <el-table-column label="模块" min-width="160">
                  <template #default="{ row }">{{ overviewModuleLabel(row) }}</template>
                </el-table-column>
                <el-table-column prop="value" label="数量" width="100" />
              </el-table>
            </el-card>
            <el-card shadow="never">
              <template #header>近7日同步趋势</template>
              <el-table :data="overview?.syncTrend || []" size="small" border>
                <el-table-column prop="label" label="日期" min-width="120" />
                <el-table-column prop="value" label="次数" width="100" />
              </el-table>
            </el-card>
            <el-card shadow="never">
              <template #header>平台失败分布</template>
              <el-table :data="overview?.providerFailureDistribution || []" size="small" border>
                <el-table-column label="平台" min-width="160">
                  <template #default="{ row }">{{ overviewProviderLabel(row) }}</template>
                </el-table-column>
                <el-table-column prop="value" label="失败数" width="100" />
              </el-table>
            </el-card>
          </div>

          <div class="triple-grid">
            <el-card shadow="never">
              <template #header>最近失败记录</template>
              <el-table :data="overview?.recentFailedRecords || []" size="small" border>
                <el-table-column prop="bizNo" label="业务编号" min-width="140" />
                <el-table-column prop="providerName" label="平台" min-width="120" />
                <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
              </el-table>
            </el-card>
            <el-card shadow="never">
              <template #header>最近超时记录</template>
              <el-table :data="overview?.recentTimeoutRecords || []" size="small" border>
                <el-table-column prop="bizNo" label="业务编号" min-width="140" />
                <el-table-column prop="providerName" label="平台" min-width="120" />
                <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
              </el-table>
            </el-card>
            <el-card shadow="never">
              <template #header>最近签名失败记录</template>
              <el-table :data="overview?.recentSignFailedRecords || []" size="small" border>
                <el-table-column prop="bizNo" label="业务编号" min-width="140" />
                <el-table-column label="平台" min-width="120">
                  <template #default="{ row }">{{ overviewCallbackProviderLabel(row) }}</template>
                </el-table-column>
                <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
              </el-table>
            </el-card>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="接入平台模板" name="providers">
        <div class="toolbar">
          <el-input v-model="providerQuery.keyword" placeholder="平台名称/编码" clearable style="width: 220px" />
          <el-select v-model="providerQuery.providerType" clearable placeholder="平台类型" style="width: 160px">
            <el-option v-for="item in providerTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="providerQuery.status" clearable placeholder="状态" style="width: 120px">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
          <el-button type="primary" @click="loadProviders">查询</el-button>
          <el-button v-if="canCreate" @click="openCreateProvider">新增模板</el-button>
        </div>

        <el-table v-loading="providerLoading" :data="providerList" border>
          <el-table-column prop="providerName" label="平台名称" min-width="160" />
          <el-table-column prop="providerCode" label="平台编码" min-width="140" />
          <el-table-column label="平台类型" width="120">
            <template #default="{ row }">{{ providerTypeLabel(row.providerType) }}</template>
          </el-table-column>
          <el-table-column prop="authType" label="鉴权类型" width="120" />
          <el-table-column prop="protocolType" label="协议类型" width="100" />
          <el-table-column label="支持场景" min-width="220">
            <template #default="{ row }">{{ row.sceneCodeList?.join('、') || '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="420" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openProviderDetail(row)">查看详情</el-button>
              <el-button v-if="canCreate && row.builtinFlag === 1" link type="primary" @click="openCopyProvider(row)">复制为自定义模板</el-button>
              <el-button v-if="canEdit && row.builtinFlag !== 1" link type="primary" @click="openEditProvider(row)">编辑</el-button>
              <el-button v-if="canStatus && row.builtinFlag !== 1" link type="primary" @click="changeProviderStatus(row)">{{ row.status === 'active' ? '停用' : '启用' }}</el-button>
              <el-button v-if="canDelete && row.builtinFlag !== 1" link type="danger" @click="removeProvider(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="providerQuery.pageNum"
            v-model:page-size="providerQuery.pageSize"
            :total="providerTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadProviders"
            @size-change="loadProviders"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="模块接入配置" name="configs">
        <div class="toolbar">
          <el-select v-model="moduleConfigQuery.orgId" clearable placeholder="所属组织" style="width: 220px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="moduleConfigQuery.bizModule" clearable placeholder="业务模块" style="width: 160px">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-model="moduleConfigQuery.keyword" placeholder="配置名称/场景/平台编码" clearable style="width: 220px" />
          <el-select v-model="moduleConfigQuery.enabled" clearable placeholder="启用状态" style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadModuleConfigs">查询</el-button>
          <el-button v-if="canCreate" @click="openCreateConfig">新增配置</el-button>
        </div>

        <el-table v-loading="configLoading" :data="moduleConfigList" border>
          <el-table-column prop="configName" label="配置名称" min-width="180" />
          <el-table-column prop="orgName" label="所属组织" min-width="160" />
          <el-table-column label="业务场景" min-width="180">
            <template #default="{ row }">{{ sceneLabel(row.bizModule, row.bizScene) }}</template>
          </el-table-column>
          <el-table-column prop="providerName" label="平台" min-width="140" />
          <el-table-column label="默认方式" width="110">
            <template #default="{ row }">{{ row.defaultMode === 'third_party' ? '第三方' : '手工' }}</template>
          </el-table-column>
          <el-table-column prop="triggerStrategy" label="触发策略" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastSyncStatus" label="最近同步" width="120" />
          <el-table-column prop="lastSyncAt" label="最近同步时间" min-width="160" />
          <el-table-column label="操作" width="430" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openConfigDetail(row)">查看详情</el-button>
              <el-button v-if="canEdit" link type="primary" @click="openEditConfig(row)">编辑</el-button>
              <el-button v-if="canStatus" link type="primary" @click="changeConfigStatus(row)">{{ row.enabled === 1 ? '停用' : '启用' }}</el-button>
              <el-button v-if="canTest" link type="primary" @click="testConfig(row)">测试</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeConfig(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="moduleConfigQuery.pageNum"
            v-model:page-size="moduleConfigQuery.pageSize"
            :total="moduleConfigTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadModuleConfigs"
            @size-change="loadModuleConfigs"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="字段映射配置" name="fieldMappings">
        <div class="toolbar">
          <el-select v-model="fieldQuery.configId" clearable filterable placeholder="选择接入配置" style="width: 260px">
            <el-option v-for="item in moduleConfigOptions" :key="item.id" :label="item.configName" :value="item.id" />
          </el-select>
          <el-input v-model="fieldQuery.keyword" placeholder="第三方字段/系统字段" clearable style="width: 220px" />
          <el-button type="primary" @click="loadFieldMappings">查询</el-button>
          <el-button v-if="canCreate" @click="openCreateField">新增字段映射</el-button>
        </div>

        <el-table v-loading="fieldLoading" :data="fieldList" border>
          <el-table-column prop="configName" label="配置名称" min-width="160" />
          <el-table-column prop="sourceField" label="第三方字段" min-width="120" />
          <el-table-column prop="sourcePath" label="JSON路径" min-width="160" />
          <el-table-column label="系统字段" min-width="220">
            <template #default="{ row }">{{ standardFieldDisplay(row.configId, row.targetField) }}</template>
          </el-table-column>
          <el-table-column prop="transformType" label="转换规则" width="120" />
          <el-table-column label="是否必填" width="90">
            <template #default="{ row }">{{ row.requiredFlag === 1 ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'info'">{{ row.enabled === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFieldDetail(row)">查看详情</el-button>
              <el-button v-if="canEdit" link type="primary" @click="openEditField(row)">编辑</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeField(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="fieldQuery.pageNum"
            v-model:page-size="fieldQuery.pageSize"
            :total="fieldTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadFieldMappings"
            @size-change="loadFieldMappings"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="状态映射配置" name="statusMappings">
        <div class="toolbar">
          <el-select v-model="statusQuery.configId" clearable filterable placeholder="选择接入配置" style="width: 260px">
            <el-option v-for="item in moduleConfigOptions" :key="item.id" :label="item.configName" :value="item.id" />
          </el-select>
          <el-input v-model="statusQuery.keyword" placeholder="状态编码/状态名称" clearable style="width: 220px" />
          <el-button type="primary" @click="loadStatusMappings">查询</el-button>
          <el-button v-if="canCreate" @click="openCreateStatus">新增状态映射</el-button>
          <el-button @click="openMappingMissingLogs">查看状态映射缺失记录</el-button>
        </div>

        <el-alert
          v-if="selectedStatusQueryConfig && !getStandardStatusOptions(selectedStatusQueryConfig.bizModule, selectedStatusQueryConfig.bizScene).length"
          type="warning"
          :closable="false"
          show-icon
          title="当前场景没有可选的系统标准状态，建议优先使用字段映射；状态映射仅保留历史兼容，不建议继续新增。"
          class="inline-alert"
        />

        <el-table v-loading="statusLoading" :data="statusList" border>
          <el-table-column prop="configName" label="配置名称" min-width="160" />
          <el-table-column prop="sourceStatusCode" label="第三方状态编码" min-width="140" />
          <el-table-column prop="sourceStatusName" label="第三方状态名称" min-width="140" />
          <el-table-column label="系统标准状态" min-width="180">
            <template #default="{ row }">
              {{ standardStatusDisplay(row.configId, row.targetStatusCode) }}
            </template>
          </el-table-column>
          <el-table-column label="结束同步" width="100">
            <template #default="{ row }">{{ row.finishFlag === 1 ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="(row.status ?? row.enabled) === 1 ? 'success' : 'info'">{{ (row.status ?? row.enabled) === 1 ? '启用' : '禁用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openStatusDetail(row)">查看详情</el-button>
              <el-button v-if="canEdit" link type="primary" @click="openEditStatus(row)">编辑</el-button>
              <el-button v-if="canDelete" link type="danger" @click="removeStatus(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="statusQuery.pageNum"
            v-model:page-size="statusQuery.pageSize"
            :total="statusTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadStatusMappings"
            @size-change="loadStatusMappings"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="同步任务管理" name="tasks">
        <div class="toolbar">
          <el-select v-model="taskQuery.orgId" clearable placeholder="所属组织" style="width: 220px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="taskQuery.providerCode" clearable filterable placeholder="平台" style="width: 200px">
            <el-option v-for="item in providerOptions" :key="item.providerCode" :label="item.providerName" :value="item.providerCode" />
          </el-select>
          <el-select v-model="taskQuery.taskStatus" clearable placeholder="任务状态" style="width: 140px">
            <el-option v-for="item in syncStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-model="taskQuery.keyword" placeholder="任务编号/业务编号/外部单号" clearable style="width: 240px" />
          <el-button type="primary" @click="loadSyncTasks">查询</el-button>
          <el-button :type="taskQuery.pendingHandleOnly === 1 ? 'warning' : 'default'" @click="togglePendingHandleTasks">
            {{ taskQuery.pendingHandleOnly === 1 ? '退出失败待处理' : '查看失败待处理' }}
          </el-button>
          <el-button v-if="canSync" @click="openTriggerTask">执行同步</el-button>
        </div>

        <el-alert
          v-if="taskQuery.pendingHandleOnly === 1"
          type="warning"
          :closable="false"
          show-icon
          title="当前仅展示需要人工关注的任务：失败、无数据、状态映射缺失。请优先查看结果说明和同步日志。"
          class="inline-alert"
        />

        <el-table v-loading="taskLoading" :data="taskList" border>
          <el-table-column prop="taskNo" label="任务编号" min-width="160" />
          <el-table-column prop="configName" label="接入配置" min-width="180" />
          <el-table-column prop="bizNo" label="业务编号" min-width="140" />
          <el-table-column prop="externalNo" label="外部单号" min-width="140" />
          <el-table-column label="任务类型" width="120">
            <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
          </el-table-column>
          <el-table-column label="触发方式" width="100">
            <template #default="{ row }">{{ triggerStrategyLabel(row.triggerType) }}</template>
          </el-table-column>
          <el-table-column label="任务状态" width="180">
            <template #default="{ row }">{{ syncStatusLabel(row.taskStatus) }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="触发人" width="120" />
          <el-table-column prop="createdAt" label="触发时间" min-width="160" />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTaskDetail(row)">查看详情</el-button>
              <el-button v-if="canRetryTask(row)" link type="primary" @click="retryTask(row)">重试</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="taskQuery.pageNum"
            v-model:page-size="taskQuery.pageSize"
            :total="taskTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadSyncTasks"
            @size-change="loadSyncTasks"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasSyncLogAccess" label="同步日志中心" name="syncLogs">
        <div class="toolbar">
          <el-select v-model="syncLogQuery.orgId" clearable placeholder="所属组织" style="width: 220px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="syncLogQuery.bizModule" clearable placeholder="业务模块" style="width: 180px">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="syncLogQuery.bizScene" clearable placeholder="业务场景" style="width: 180px">
            <el-option v-for="item in sceneOptionsForSyncLogQuery" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="syncLogQuery.providerCode" clearable filterable placeholder="第三方平台" style="width: 220px">
            <el-option v-for="item in syncLogProviderOptions" :key="item.providerCode" :label="item.providerName" :value="item.providerCode" />
          </el-select>
          <el-select v-model="syncLogQuery.syncStatus" clearable placeholder="同步状态" style="width: 200px">
            <el-option v-for="item in syncStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="syncLogQuery.triggerType" clearable placeholder="触发方式" style="width: 160px">
            <el-option v-for="item in triggerTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="syncLogQuery.handleStatus" clearable placeholder="处理状态" style="width: 160px">
            <el-option v-for="item in syncLogHandleStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-date-picker
            v-model="syncLogTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
          <el-input v-model="syncLogQuery.keyword" placeholder="业务编号/外部单号/错误信息" clearable style="width: 240px" />
          <el-button type="primary" @click="loadSyncLogs">查询</el-button>
        </div>

        <el-table v-loading="syncLogLoading" :data="syncLogs" border>
          <el-table-column prop="bizNo" label="业务编号" min-width="140" />
          <el-table-column prop="orgName" label="所属组织" min-width="140" />
          <el-table-column prop="providerName" label="平台" min-width="140" />
          <el-table-column label="业务模块" min-width="120">
            <template #default="{ row }">{{ bizModuleLabel(row.bizModule) }}</template>
          </el-table-column>
          <el-table-column label="业务场景" min-width="120">
            <template #default="{ row }">{{ sceneLabel(row.bizModule, row.bizScene) }}</template>
          </el-table-column>
          <el-table-column label="任务类型" width="110">
            <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
          </el-table-column>
          <el-table-column label="触发方式" width="100">
            <template #default="{ row }">{{ triggerTypeLabel(row.triggerType) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="150">
            <template #default="{ row }">{{ syncStatusLabel(row.syncStatus) }}</template>
          </el-table-column>
          <el-table-column label="处理状态" width="120">
            <template #default="{ row }">{{ syncLogHandleStatusLabel(row.handleStatus) }}</template>
          </el-table-column>
          <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
          <el-table-column prop="resultMessage" label="结果说明" min-width="220" show-overflow-tooltip />
          <el-table-column prop="errorMessage" label="错误信息" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="同步时间" min-width="160" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openSyncLogDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="syncLogQuery.pageNum"
            v-model:page-size="syncLogQuery.pageSize"
            :total="syncLogTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadSyncLogs"
            @size-change="loadSyncLogs"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasCallbackLogAccess" label="回调日志中心" name="callbackLogs">
        <div class="toolbar">
          <el-select v-model="callbackLogQuery.orgId" clearable placeholder="所属组织" style="width: 220px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="callbackLogQuery.bizModule" clearable placeholder="业务模块" style="width: 180px">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="callbackLogQuery.bizScene" clearable placeholder="业务场景" style="width: 180px">
            <el-option v-for="item in sceneOptionsForCallbackLogQuery" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="callbackLogQuery.providerCode" clearable filterable placeholder="第三方平台" style="width: 220px">
            <el-option v-for="item in callbackLogProviderOptions" :key="item.providerCode" :label="item.providerName" :value="item.providerCode" />
          </el-select>
          <el-select v-model="callbackLogQuery.signResult" clearable placeholder="验签结果" style="width: 140px">
            <el-option label="通过" value="pass" />
            <el-option label="失败" value="fail" />
          </el-select>
          <el-select v-model="callbackLogQuery.processStatus" clearable placeholder="处理状态" style="width: 200px">
            <el-option v-for="item in callbackProcessStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-date-picker
            v-model="callbackLogTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
          <el-input v-model="callbackLogQuery.keyword" placeholder="业务编号/外部单号/错误信息" clearable style="width: 240px" />
          <el-button type="primary" @click="loadCallbackLogs">查询</el-button>
        </div>

        <el-table v-loading="callbackLogLoading" :data="callbackLogs" border>
          <el-table-column prop="bizNo" label="业务编号" min-width="140" />
          <el-table-column prop="orgName" label="所属组织" min-width="140" />
          <el-table-column prop="providerName" label="平台" min-width="160" />
          <el-table-column label="业务模块" min-width="120">
            <template #default="{ row }">{{ bizModuleLabel(row.bizModule || '') }}</template>
          </el-table-column>
          <el-table-column label="业务场景" min-width="140">
            <template #default="{ row }">{{ sceneLabel(row.bizModule || '', row.bizScene || '') }}</template>
          </el-table-column>
          <el-table-column prop="clientIp" label="来源IP" min-width="120" />
          <el-table-column label="验签结果" width="100">
            <template #default="{ row }">{{ signResultLabel(row.signResult) }}</template>
          </el-table-column>
          <el-table-column label="处理状态" width="180">
            <template #default="{ row }">{{ callbackProcessStatusLabel(row.processStatus) }}</template>
          </el-table-column>
          <el-table-column prop="processResult" label="结果说明" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="回调时间" min-width="160" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCallbackLogDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="callbackLogQuery.pageNum"
            v-model:page-size="callbackLogQuery.pageSize"
            :total="callbackLogTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadCallbackLogs"
            @size-change="loadCallbackLogs"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="附件转存记录" name="files">
        <div class="toolbar">
          <el-select v-model="fileQuery.orgId" clearable placeholder="所属组织" style="width: 220px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="fileQuery.bizModule" clearable placeholder="业务模块" style="width: 180px">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="fileQuery.bizScene" clearable placeholder="业务场景" style="width: 180px">
            <el-option v-for="item in sceneOptionsForFileQuery" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="fileQuery.providerCode" clearable filterable placeholder="平台" style="width: 220px">
            <el-option v-for="item in fileRecordProviderOptions" :key="item.providerCode" :label="item.providerName" :value="item.providerCode" />
          </el-select>
          <el-select v-model="fileQuery.downloadStatus" clearable placeholder="下载状态" style="width: 160px">
            <el-option v-for="item in fileRecordStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="fileQuery.storageStatus" clearable placeholder="转存状态" style="width: 140px">
            <el-option v-for="item in fileRecordStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input-number v-model="fileQuery.bindingId" :min="1" :controls="false" placeholder="绑定ID" style="width: 160px" />
          <el-input v-model="fileQuery.bizNo" placeholder="业务编号" clearable style="width: 180px" />
          <el-date-picker
            v-model="fileTimeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 360px"
          />
          <el-input v-model="fileQuery.keyword" placeholder="文件名/脱敏地址/错误信息/配置名" clearable style="width: 280px" />
          <el-button type="primary" @click="loadFileRecords">查询</el-button>
        </div>

        <el-table v-loading="fileLoading" :data="fileRecords" border>
          <el-table-column prop="configName" label="接入配置" min-width="180" />
          <el-table-column prop="sourceFileName" label="原文件名" min-width="180" />
          <el-table-column prop="bizNo" label="业务编号" min-width="150" />
          <el-table-column prop="orgName" label="所属组织" min-width="140" />
          <el-table-column prop="providerName" label="平台" min-width="140" />
          <el-table-column label="业务模块" min-width="120">
            <template #default="{ row }">{{ bizModuleLabel(row.bizModule || '') }}</template>
          </el-table-column>
          <el-table-column label="业务场景" min-width="140">
            <template #default="{ row }">{{ sceneLabel(row.bizModule || '', row.bizScene || '') }}</template>
          </el-table-column>
          <el-table-column label="下载状态" width="120">
            <template #default="{ row }">{{ fileRecordStatusLabel(row.downloadStatus) }}</template>
          </el-table-column>
          <el-table-column label="转存状态" width="120">
            <template #default="{ row }">{{ fileRecordStatusLabel(row.storageStatus) }}</template>
          </el-table-column>
          <el-table-column prop="errorMessage" label="失败原因" min-width="220" show-overflow-tooltip />
          <el-table-column prop="minioFileUrl" label="MinIO地址" min-width="240" show-overflow-tooltip />
          <el-table-column prop="updatedAt" label="最近更新时间" min-width="170" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFileDetail(row)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="fileQuery.pageNum"
            v-model:page-size="fileQuery.pageSize"
            :total="fileTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadFileRecords"
            @size-change="loadFileRecords"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasIntegrationAccess" label="连接测试与健康检查" name="health">
        <div class="toolbar">
          <el-input v-model="healthQuery.keyword" clearable placeholder="配置名称/平台编码/场景关键字" style="width: 240px" />
          <el-select v-model="healthQuery.orgId" clearable placeholder="所属组织" style="width: 220px">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="healthQuery.bizModule" clearable placeholder="业务模块" style="width: 180px">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="healthQuery.bizScene" clearable placeholder="业务场景" style="width: 200px">
            <el-option v-for="item in sceneOptionsForHealthQuery" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="healthQuery.providerCode" clearable filterable placeholder="平台" style="width: 200px">
            <el-option v-for="item in healthProviderOptions" :key="item.providerCode" :label="item.providerName" :value="item.providerCode" />
          </el-select>
          <el-button type="primary" @click="loadHealthChecks">查询</el-button>
        </div>

        <el-table v-loading="healthLoading" :data="healthChecks" border>
          <el-table-column prop="configName" label="配置名称" min-width="180" />
          <el-table-column prop="orgName" label="所属组织" min-width="140" />
          <el-table-column label="业务场景" min-width="180">
            <template #default="{ row }">{{ bizModuleLabel(row.bizModule || '') }} / {{ sceneLabel(row.bizModule || '', row.bizScene || '') }}</template>
          </el-table-column>
          <el-table-column prop="providerName" label="平台" min-width="140" />
          <el-table-column label="最近测试" width="110">
            <template #default="{ row }">
              <el-tag :type="healthCheckStatusTagType(row.lastTestStatus)">{{ healthCheckStatusLabel(row.lastTestStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="鉴权" width="100">
            <template #default="{ row }">
              <el-tag :type="healthResultTagType(row.authSuccess)">{{ healthResultLabel(row.authSuccess) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接口可达" width="100">
            <template #default="{ row }">
              <el-tag :type="healthResultTagType(row.reachable)">{{ healthResultLabel(row.reachable) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="回调可达" width="110">
            <template #default="{ row }">
              <el-tag :type="healthResultTagType(row.callbackReachable)">
                {{ healthResultLabel(row.callbackReachable, row.callbackEnabled === 1 ? '未检测' : '不适用') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastTestAt" label="最近测试时间" min-width="170" />
          <el-table-column label="24h同步成功率" width="120">
            <template #default="{ row }">{{ row.successRate24h ?? 0 }}%</template>
          </el-table-column>
          <el-table-column label="24h回调成功率" width="130">
            <template #default="{ row }">{{ row.callbackSuccessRate24h ?? 0 }}%</template>
          </el-table-column>
          <el-table-column prop="averageDurationMs24h" label="平均耗时(ms)" width="120" />
          <el-table-column prop="lastSyncStatus" label="最近同步状态" width="120" />
          <el-table-column prop="testMessage" label="健康说明" min-width="220" show-overflow-tooltip />
          <el-table-column prop="lastErrorMessage" label="最近同步错误" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openHealthDetail(row)">查看详情</el-button>
              <el-button v-if="canTest" link type="primary" @click="testHealth(row)">测试连接</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="healthQuery.pageNum"
            v-model:page-size="healthQuery.pageSize"
            :total="healthTotal"
            layout="total, sizes, prev, pager, next"
            @current-change="loadHealthChecks"
            @size-change="loadHealthChecks"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="hasAiConfigAccess" label="AI接口配置" name="aiConfig">
        <AiConfigPage />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="providerDetailVisible" title="平台模板详情" width="960px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="平台名称">{{ detailText(currentProviderDetail?.providerName) }}</el-descriptions-item>
          <el-descriptions-item label="平台编码">{{ detailText(currentProviderDetail?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="平台类型">{{ providerTypeLabel(currentProviderDetail?.providerType || '') }}</el-descriptions-item>
          <el-descriptions-item label="鉴权类型">{{ authTypeLabel(currentProviderDetail?.authType) }}</el-descriptions-item>
          <el-descriptions-item label="协议类型">{{ protocolTypeLabel(currentProviderDetail?.protocolType) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailYesNo(currentProviderDetail?.status, '启用', '停用') }}</el-descriptions-item>
          <el-descriptions-item label="支持回调">{{ detailYesNo(currentProviderDetail?.callbackSupported) }}</el-descriptions-item>
          <el-descriptions-item label="支持文件拉取">{{ detailYesNo(currentProviderDetail?.filePullSupported) }}</el-descriptions-item>
          <el-descriptions-item label="支持场景" :span="2">
            {{ currentProviderDetail?.sceneCodeList?.length ? currentProviderDetail.sceneCodeList.map(providerSceneLabel).join('、') : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailText(currentProviderDetail?.remark) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailText(currentProviderDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailText(currentProviderDetail?.updatedAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="detail-section__title">请求模板</div>
          <pre class="detail-json">{{ formatJsonContent(currentProviderDetail?.requestTemplate) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">响应模板</div>
          <pre class="detail-json">{{ formatJsonContent(currentProviderDetail?.responseTemplate) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="providerDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configDetailVisible" title="模块接入配置详情" width="1080px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="配置名称">{{ detailText(currentConfigDetail?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailText(currentConfigDetail?.orgName) }}</el-descriptions-item>
          <el-descriptions-item label="业务模块">{{ bizModuleLabel(currentConfigDetail?.bizModule || '') }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ sceneLabel(currentConfigDetail?.bizModule || '', currentConfigDetail?.bizScene || '') }}</el-descriptions-item>
          <el-descriptions-item label="第三方平台">{{ detailText(currentConfigDetail?.providerName || currentConfigDetail?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">{{ detailYesNo(currentConfigDetail?.enabled, '启用', '停用') }}</el-descriptions-item>
          <el-descriptions-item label="默认维护方式">{{ modeLabel(currentConfigDetail?.defaultMode) }}</el-descriptions-item>
          <el-descriptions-item label="触发策略">{{ triggerStrategyLabel(currentConfigDetail?.triggerStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="允许单据切换">{{ detailYesNo(currentConfigDetail?.allowDocumentSwitch) }}</el-descriptions-item>
          <el-descriptions-item label="强制第三方">{{ detailYesNo(currentConfigDetail?.forceThirdParty) }}</el-descriptions-item>
          <el-descriptions-item label="自动覆盖">{{ detailYesNo(currentConfigDetail?.autoCoverEnabled) }}</el-descriptions-item>
          <el-descriptions-item label="自动覆盖策略">{{ detailText(currentConfigDetail?.autoCoverStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="允许手工兜底">{{ detailYesNo(currentConfigDetail?.allowManualFallback) }}</el-descriptions-item>
          <el-descriptions-item label="允许人工确认覆盖">{{ detailYesNo(currentConfigDetail?.allowManualConfirmCover) }}</el-descriptions-item>
          <el-descriptions-item label="下载附件">{{ detailYesNo(currentConfigDetail?.attachmentPullEnabled) }}</el-descriptions-item>
          <el-descriptions-item label="启用回调">{{ detailYesNo(currentConfigDetail?.callbackEnabled) }}</el-descriptions-item>
          <el-descriptions-item label="回调地址" :span="2">{{ detailText(currentConfigDetail?.callbackUrl) }}</el-descriptions-item>
          <el-descriptions-item label="外部单号规则" :span="2">{{ detailText(currentConfigDetail?.externalNoFieldRule) }}</el-descriptions-item>
          <el-descriptions-item label="AccessToken地址" :span="2">{{ detailText(currentConfigDetail?.accessTokenUrl) }}</el-descriptions-item>
          <el-descriptions-item label="RefreshToken地址" :span="2">{{ detailText(currentConfigDetail?.refreshTokenUrl) }}</el-descriptions-item>
          <el-descriptions-item label="Token请求方式">{{ detailText(currentConfigDetail?.tokenRequestMethod) }}</el-descriptions-item>
          <el-descriptions-item label="同步频率(分钟)">{{ detailText(currentConfigDetail?.syncFrequencyMinutes) }}</el-descriptions-item>
          <el-descriptions-item label="计划Cron">{{ detailText(currentConfigDetail?.scheduleCron) }}</el-descriptions-item>
          <el-descriptions-item label="超时(ms)">{{ detailText(currentConfigDetail?.timeoutMs) }}</el-descriptions-item>
          <el-descriptions-item label="最大重试次数">{{ detailText(currentConfigDetail?.retryMaxCount) }}</el-descriptions-item>
          <el-descriptions-item label="最近同步状态">{{ detailText(currentConfigDetail?.lastSyncStatus) }}</el-descriptions-item>
          <el-descriptions-item label="最近同步时间">{{ detailText(currentConfigDetail?.lastSyncAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近错误信息" :span="2">{{ detailText(currentConfigDetail?.lastErrorMessage) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailText(currentConfigDetail?.remark) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailText(currentConfigDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailText(currentConfigDetail?.updatedAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="detail-section__title">接入密钥</div>
          <el-table v-if="currentConfigDetail?.secrets?.length" :data="currentConfigDetail.secrets" border size="small">
            <el-table-column prop="secretKey" label="参数名" min-width="180" />
            <el-table-column prop="secretMask" label="掩码值" min-width="220" />
            <el-table-column label="是否加密" width="120">
              <template #default="{ row }">{{ detailYesNo(row.encryptedFlag) }}</template>
            </el-table-column>
          </el-table>
          <div v-else class="detail-empty">暂无密钥配置</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="configDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldDetailVisible" title="字段映射详情" width="900px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="接入配置">{{ detailText(currentFieldDetail?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="平台编码">{{ detailText(currentFieldDetail?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="第三方字段">{{ detailText(currentFieldDetail?.sourceField) }}</el-descriptions-item>
          <el-descriptions-item label="JSON路径">{{ detailText(currentFieldDetail?.sourcePath) }}</el-descriptions-item>
          <el-descriptions-item label="系统字段">{{ standardFieldDisplay(currentFieldDetail?.configId, currentFieldDetail?.targetField) }}</el-descriptions-item>
          <el-descriptions-item label="转换规则">{{ transformTypeLabel(currentFieldDetail?.transformType) }}</el-descriptions-item>
          <el-descriptions-item label="转换表达式" :span="2">{{ detailText(currentFieldDetail?.transformRule) }}</el-descriptions-item>
          <el-descriptions-item label="默认值">{{ detailText(currentFieldDetail?.defaultValue) }}</el-descriptions-item>
          <el-descriptions-item label="异常时处理方式">{{ fieldErrorStrategyLabel(currentFieldDetail?.errorStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="是否必填">{{ detailYesNo(currentFieldDetail?.requiredFlag) }}</el-descriptions-item>
          <el-descriptions-item label="系统执行顺序">{{ detailText(currentFieldDetail?.sortNo) }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">{{ detailYesNo(currentFieldDetail?.enabled, '启用', '停用') }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailText(currentFieldDetail?.remark) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailText(currentFieldDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailText(currentFieldDetail?.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="fieldDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusDetailVisible" title="状态映射详情" width="900px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="接入配置">{{ detailText(currentStatusDetail?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="平台编码">{{ detailText(currentStatusDetail?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="第三方状态编码">{{ detailText(currentStatusDetail?.sourceStatusCode) }}</el-descriptions-item>
          <el-descriptions-item label="第三方状态名称">{{ detailText(currentStatusDetail?.sourceStatusName) }}</el-descriptions-item>
          <el-descriptions-item label="系统标准状态">{{ standardStatusDisplay(currentStatusDetail?.configId, currentStatusDetail?.targetStatusCode) }}</el-descriptions-item>
          <el-descriptions-item label="结束同步">{{ detailYesNo(currentStatusDetail?.finishFlag) }}</el-descriptions-item>
          <el-descriptions-item label="系统执行顺序">{{ detailText(currentStatusDetail?.sortNo) }}</el-descriptions-item>
          <el-descriptions-item label="启用状态">{{ detailYesNo(currentStatusDetail?.enabled, '启用', '停用') }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailText(currentStatusDetail?.remark) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detailText(currentStatusDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detailText(currentStatusDetail?.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="statusDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDetailVisible" title="同步任务详情" width="980px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务编号">{{ detailText(currentTaskDetail?.taskNo) }}</el-descriptions-item>
          <el-descriptions-item label="接入配置">{{ detailText(currentTaskDetail?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailText(currentTaskDetail?.orgName) }}</el-descriptions-item>
          <el-descriptions-item label="第三方平台">{{ detailText(currentTaskDetail?.providerName || currentTaskDetail?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="业务模块">{{ bizModuleLabel(currentTaskDetail?.bizModule || '') }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ sceneLabel(currentTaskDetail?.bizModule || '', currentTaskDetail?.bizScene || '') }}</el-descriptions-item>
          <el-descriptions-item label="业务主键ID">{{ detailText(currentTaskDetail?.bizId) }}</el-descriptions-item>
          <el-descriptions-item label="业务编号">{{ detailText(currentTaskDetail?.bizNo) }}</el-descriptions-item>
          <el-descriptions-item label="外部单号">{{ detailText(currentTaskDetail?.externalNo) }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ taskTypeLabel(currentTaskDetail?.taskType) }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ triggerStrategyLabel(currentTaskDetail?.triggerType) }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">{{ syncStatusLabel(currentTaskDetail?.taskStatus || '') }}</el-descriptions-item>
          <el-descriptions-item label="触发人">{{ detailText(currentTaskDetail?.operatorName) }}</el-descriptions-item>
          <el-descriptions-item label="重试次数">{{ detailText(currentTaskDetail?.retryCount) }}</el-descriptions-item>
          <el-descriptions-item label="最大重试次数">{{ detailText(currentTaskDetail?.retryMaxCount) }}</el-descriptions-item>
          <el-descriptions-item label="计划执行时间">{{ detailText(currentTaskDetail?.planExecuteAt) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ detailText(currentTaskDetail?.startAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ detailText(currentTaskDetail?.finishAt) }}</el-descriptions-item>
          <el-descriptions-item label="触发时间">{{ detailText(currentTaskDetail?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="结果说明" :span="2">{{ detailText(currentTaskDetail?.resultMessage) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="taskDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fileDetailVisible" title="附件转存详情" width="min(960px, calc(100vw - 32px))" class="file-detail-dialog">
      <div class="detail-dialog detail-dialog--scroll">
        <el-descriptions :column="2" border class="file-detail-descriptions">
          <el-descriptions-item label="接入配置"><span class="detail-value">{{ detailText(currentFileDetail?.configName) }}</span></el-descriptions-item>
          <el-descriptions-item label="原文件名"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.sourceFileName) }}</span></el-descriptions-item>
          <el-descriptions-item label="第三方平台"><span class="detail-value">{{ detailText(currentFileDetail?.providerName || currentFileDetail?.providerCode) }}</span></el-descriptions-item>
          <el-descriptions-item label="所属组织"><span class="detail-value">{{ detailText(currentFileDetail?.orgName) }}</span></el-descriptions-item>
          <el-descriptions-item label="业务模块"><span class="detail-value">{{ bizModuleLabel(currentFileDetail?.bizModule || '') }}</span></el-descriptions-item>
          <el-descriptions-item label="业务场景"><span class="detail-value">{{ sceneLabel(currentFileDetail?.bizModule || '', currentFileDetail?.bizScene || '') }}</span></el-descriptions-item>
          <el-descriptions-item label="业务编号"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.bizNo) }}</span></el-descriptions-item>
          <el-descriptions-item label="下载状态"><span class="detail-value">{{ fileRecordStatusLabel(currentFileDetail?.downloadStatus) }}</span></el-descriptions-item>
          <el-descriptions-item label="转存状态"><span class="detail-value">{{ fileRecordStatusLabel(currentFileDetail?.storageStatus) }}</span></el-descriptions-item>
          <el-descriptions-item label="文件Hash"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.fileHash) }}</span></el-descriptions-item>
          <el-descriptions-item label="文件大小"><span class="detail-value">{{ detailText(currentFileDetail?.fileSize) }}</span></el-descriptions-item>
          <el-descriptions-item label="文件类型">
            <el-tooltip v-if="currentFileDetail?.mimeType" :content="`原始MIME：${currentFileDetail.mimeType}`" placement="top">
              <span class="detail-value detail-value--break">{{ fileTypeLabel(currentFileDetail?.mimeType, currentFileDetail?.sourceFileName) }}</span>
            </el-tooltip>
            <span v-else class="detail-value detail-value--break">{{ fileTypeLabel(currentFileDetail?.mimeType, currentFileDetail?.sourceFileName) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="错误码"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.errorCode) }}</span></el-descriptions-item>
          <el-descriptions-item label="失败原因" :span="2"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.errorMessage) }}</span></el-descriptions-item>
          <el-descriptions-item label="同步任务编号"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.taskNo) }}</span></el-descriptions-item>
          <el-descriptions-item label="同步时间"><span class="detail-value">{{ detailText(currentFileDetail?.syncLogCreatedAt) }}</span></el-descriptions-item>
          <el-descriptions-item label="同步状态"><span class="detail-value">{{ syncStatusLabel(currentFileDetail?.syncLogStatus) }}</span></el-descriptions-item>
          <el-descriptions-item label="同步结果"><span class="detail-value detail-value--break">{{ fileRecordSyncResultText(currentFileDetail) }}</span></el-descriptions-item>
          <el-descriptions-item label="地址签名"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.sourceUrlSignature) }}</span></el-descriptions-item>
          <el-descriptions-item label="原始文件地址（脱敏）" :span="2"><span class="detail-value detail-value--break">{{ detailText(currentFileDetail?.sourceFileUrl) }}</span></el-descriptions-item>
          <el-descriptions-item label="MinIO地址" :span="2">
            <el-link
              v-if="currentFileDetail?.minioFileUrl"
              :href="currentFileDetail.minioFileUrl"
              target="_blank"
              type="primary"
              class="detail-link"
            >
              {{ currentFileDetail.minioFileUrl }}
            </el-link>
            <span v-else class="detail-value">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间"><span class="detail-value">{{ detailText(currentFileDetail?.createdAt) }}</span></el-descriptions-item>
          <el-descriptions-item label="更新时间"><span class="detail-value">{{ detailText(currentFileDetail?.updatedAt) }}</span></el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="fileDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="healthDetailVisible" title="健康检查详情" width="1080px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="配置名称">{{ detailText(currentHealthDetail?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailText(currentHealthDetail?.orgName) }}</el-descriptions-item>
          <el-descriptions-item label="业务模块">{{ bizModuleLabel(currentHealthDetail?.bizModule || '') }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ sceneLabel(currentHealthDetail?.bizModule || '', currentHealthDetail?.bizScene || '') }}</el-descriptions-item>
          <el-descriptions-item label="第三方平台">{{ detailText(currentHealthDetail?.providerName || currentHealthDetail?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="配置状态">{{ detailYesNo(currentHealthDetail?.enabled, '启用', '停用') }}</el-descriptions-item>
          <el-descriptions-item label="最近测试状态">{{ healthCheckStatusLabel(currentHealthDetail?.lastTestStatus) }}</el-descriptions-item>
          <el-descriptions-item label="最近测试时间">{{ detailText(currentHealthDetail?.lastTestAt) }}</el-descriptions-item>
          <el-descriptions-item label="鉴权结果">{{ healthResultLabel(currentHealthDetail?.authSuccess) }}</el-descriptions-item>
          <el-descriptions-item label="接口可达">{{ healthResultLabel(currentHealthDetail?.reachable) }}</el-descriptions-item>
          <el-descriptions-item label="回调可达">
            {{ healthResultLabel(currentHealthDetail?.callbackReachable, currentHealthDetail?.callbackEnabled === 1 ? '未检测' : '不适用') }}
          </el-descriptions-item>
          <el-descriptions-item label="24h同步成功率">{{ formatPercent(currentHealthDetail?.successRate24h) }}</el-descriptions-item>
          <el-descriptions-item label="24h回调成功率">{{ formatPercent(currentHealthDetail?.callbackSuccessRate24h) }}</el-descriptions-item>
          <el-descriptions-item label="24h平均耗时(ms)">{{ detailText(currentHealthDetail?.averageDurationMs24h) }}</el-descriptions-item>
          <el-descriptions-item label="最近同步状态">{{ detailText(currentHealthDetail?.lastSyncStatus) }}</el-descriptions-item>
          <el-descriptions-item label="最近同步时间">{{ detailText(currentHealthDetail?.lastSyncAt) }}</el-descriptions-item>
          <el-descriptions-item label="最近错误信息" :span="2">{{ detailText(currentHealthDetail?.lastErrorMessage) }}</el-descriptions-item>
          <el-descriptions-item label="测试结果说明" :span="2">{{ detailText(currentHealthDetail?.lastTestMessage || currentHealthDetail?.testMessage) }}</el-descriptions-item>
          <el-descriptions-item label="鉴权说明" :span="2">{{ detailText(currentHealthDetail?.authMessage) }}</el-descriptions-item>
          <el-descriptions-item label="接口说明" :span="2">{{ detailText(currentHealthDetail?.reachableMessage) }}</el-descriptions-item>
          <el-descriptions-item label="回调说明" :span="2">{{ detailText(currentHealthDetail?.callbackMessage) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="detail-section__title">最近异常同步日志</div>
          <el-table v-if="currentHealthDetail?.recentFailedLogs?.length" :data="currentHealthDetail.recentFailedLogs" border size="small">
            <el-table-column prop="bizNo" label="业务编号" min-width="150" />
            <el-table-column label="状态" width="150">
              <template #default="{ row }">{{ syncStatusLabel(row.syncStatus) }}</template>
            </el-table-column>
            <el-table-column prop="errorMessage" label="错误信息" min-width="220" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="同步时间" min-width="180" />
          </el-table>
          <div v-else class="detail-empty">暂无失败日志</div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">最近测试记录</div>
          <el-table v-if="currentHealthDetail?.recentTestLogs?.length" :data="currentHealthDetail.recentTestLogs" border size="small">
            <el-table-column prop="createdAt" label="测试时间" min-width="180" />
            <el-table-column label="测试状态" width="110">
              <template #default="{ row }">
                <el-tag :type="healthCheckStatusTagType(row.testStatus)">{{ healthCheckStatusLabel(row.testStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="鉴权" width="90">
              <template #default="{ row }">{{ healthResultLabel(row.authSuccess) }}</template>
            </el-table-column>
            <el-table-column label="接口" width="90">
              <template #default="{ row }">{{ healthResultLabel(row.reachable) }}</template>
            </el-table-column>
            <el-table-column label="回调" width="90">
              <template #default="{ row }">
                {{ healthResultLabel(row.callbackReachable, currentHealthDetail?.callbackEnabled === 1 ? '未检测' : '不适用') }}
              </template>
            </el-table-column>
            <el-table-column prop="errorCode" label="错误码" width="140" />
            <el-table-column prop="testMessage" label="结果说明" min-width="260" show-overflow-tooltip />
            <el-table-column prop="operatorName" label="测试人" width="120" />
          </el-table>
          <div v-else class="detail-empty">暂无测试记录</div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">最新测试请求</div>
          <pre class="detail-json">{{ formatJsonContent(currentHealthDetail?.recentTestLogs?.[0]?.requestPayload) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">最新测试请求头</div>
          <pre class="detail-json">{{ formatJsonContent(currentHealthDetail?.recentTestLogs?.[0]?.requestHeaders) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">最新测试请求体</div>
          <pre class="detail-json">{{ formatJsonContent(currentHealthDetail?.recentTestLogs?.[0]?.requestBody) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">最新测试响应</div>
          <pre class="detail-json">{{ formatJsonContent(currentHealthDetail?.recentTestLogs?.[0]?.responsePayload) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="healthDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="providerDialogVisible" :title="providerDialogTitle" width="860px">
      <el-form label-width="120px">
        <el-form-item label="平台编码"><el-input v-model="providerForm.providerCode" /></el-form-item>
        <el-form-item label="平台名称"><el-input v-model="providerForm.providerName" /></el-form-item>
        <el-form-item label="平台类型">
          <el-select v-model="providerForm.providerType" style="width: 100%">
            <el-option v-for="item in providerTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="鉴权类型">
          <el-select v-model="providerForm.authType" style="width: 100%">
            <el-option v-for="item in authTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="协议类型">
          <el-select v-model="providerForm.protocolType" style="width: 100%">
            <el-option v-for="item in protocolOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支持场景">
          <el-select v-model="providerForm.sceneCodes" multiple filterable style="width: 100%">
            <el-option
              v-for="item in providerSceneCodeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="支持回调">
          <el-switch v-model="providerForm.callbackSupported" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="支持文件拉取">
          <el-switch v-model="providerForm.filePullSupported" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="请求模板" :error="providerTemplateErrors.requestTemplate">
          <el-input
            v-model="providerForm.requestTemplate"
            type="textarea"
            :rows="5"
            placeholder="请输入 JSON 请求模板"
            @blur="validateProviderTemplateField('requestTemplate')"
          />
        </el-form-item>
        <el-form-item label="响应模板" :error="providerTemplateErrors.responseTemplate">
          <el-input
            v-model="providerForm.responseTemplate"
            type="textarea"
            :rows="4"
            placeholder="请输入 JSON 响应模板（可留空）"
            @blur="validateProviderTemplateField('responseTemplate')"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="providerForm.status">
            <el-radio value="active">启用</el-radio>
            <el-radio value="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="providerForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="providerSaving" @click="submitProvider">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="configDialogVisible" :title="currentConfigId ? '编辑模块接入配置' : '新增模块接入配置'" width="960px">
      <el-form label-width="130px">
        <el-form-item label="所属组织">
          <el-select v-model="moduleConfigForm.orgId" filterable style="width: 100%">
            <el-option v-for="item in flatOrgOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务模块">
          <el-select v-model="moduleConfigForm.bizModule" style="width: 100%">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务场景">
          <el-select v-model="moduleConfigForm.bizScene" style="width: 100%">
            <el-option v-for="item in sceneOptionsForConfig" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="第三方平台">
          <el-select v-model="moduleConfigForm.providerCode" filterable style="width: 100%">
            <el-option v-for="item in providerOptions" :key="item.providerCode" :label="item.providerName" :value="item.providerCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置名称"><el-input v-model="moduleConfigForm.configName" /></el-form-item>
        <el-form-item label="默认维护方式">
          <el-radio-group v-model="moduleConfigForm.defaultMode">
            <el-radio value="manual" :disabled="moduleConfigForm.forceThirdParty === 1">手工</el-radio>
            <el-radio value="third_party">第三方</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="触发策略">
          <el-select v-model="moduleConfigForm.triggerStrategy" style="width: 100%">
            <el-option v-for="item in triggerStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="允许单据切换"><el-switch v-model="moduleConfigForm.allowDocumentSwitch" :active-value="1" :inactive-value="0" :disabled="moduleConfigForm.forceThirdParty === 1" /></el-form-item>
        <el-form-item label="强制第三方"><el-switch v-model="moduleConfigForm.forceThirdParty" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="自动覆盖"><el-switch v-model="moduleConfigForm.autoCoverEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="允许手工兜底"><el-switch v-model="moduleConfigForm.allowManualFallback" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="允许人工确认覆盖"><el-switch v-model="moduleConfigForm.allowManualConfirmCover" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="下载附件"><el-switch v-model="moduleConfigForm.attachmentPullEnabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="启用回调"><el-switch v-model="moduleConfigForm.callbackEnabled" :active-value="1" :inactive-value="0" :disabled="moduleConfigForm.triggerStrategy === 'callback'" /></el-form-item>
        <el-form-item label="回调地址"><el-input v-model="moduleConfigForm.callbackUrl" /></el-form-item>
        <el-form-item label="外部单号规则"><el-input v-model="moduleConfigForm.externalNoFieldRule" /></el-form-item>
        <el-form-item label="AccessToken地址"><el-input v-model="moduleConfigForm.accessTokenUrl" /></el-form-item>
        <el-form-item label="RefreshToken地址"><el-input v-model="moduleConfigForm.refreshTokenUrl" /></el-form-item>
        <el-form-item label="Token请求方式">
          <el-select v-model="moduleConfigForm.tokenRequestMethod" style="width: 100%">
            <el-option label="POST" value="POST" />
            <el-option label="GET" value="GET" />
          </el-select>
        </el-form-item>
        <el-form-item label="同步频率(分钟)"><el-input-number v-model="moduleConfigForm.syncFrequencyMinutes" :min="1" /></el-form-item>
        <el-form-item label="计划Cron"><el-input v-model="moduleConfigForm.scheduleCron" /></el-form-item>
        <el-form-item label="超时(ms)"><el-input-number v-model="moduleConfigForm.timeoutMs" :min="1000" :step="1000" /></el-form-item>
        <el-form-item label="最大重试次数"><el-input-number v-model="moduleConfigForm.retryMaxCount" :min="0" /></el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="moduleConfigForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="接入密钥">
          <div class="secret-block">
            <div v-for="(item, index) in moduleConfigForm.secrets" :key="index" class="secret-row">
              <el-input v-model="item.secretKey" placeholder="参数名，如 clientId / appSecret" />
              <el-input v-model="item.secretValue" placeholder="参数值，留空则保留原值" show-password />
              <el-button link type="danger" @click="removeSecretRow(index)">删除</el-button>
            </div>
            <el-button link type="primary" @click="addSecretRow">新增密钥</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="moduleConfigForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSaving" @click="submitConfig">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="fieldDialogVisible" :title="currentFieldId ? '编辑字段映射' : '新增字段映射'" width="760px">
      <el-alert
        :closable="false"
        type="info"
        show-icon
        :title="FIELD_MAPPING_FALLBACK_NOTICE"
        class="dialog-alert"
      />
      <el-form label-width="120px">
        <el-form-item label="接入配置">
          <el-select v-model="fieldForm.configId" filterable style="width: 100%">
            <el-option v-for="item in moduleConfigOptions" :key="item.id" :label="item.configName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="第三方字段"><el-input v-model="fieldForm.sourceField" /></el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              JSON路径
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">{{ FIELD_JSON_PATH_HELP_TEXT }}</div>
                </template>
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="fieldForm.sourcePath" />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              系统字段
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">{{ FIELD_TARGET_HELP_TEXT }}</div>
                </template>
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-select
            v-model="fieldForm.targetField"
            filterable
            style="width: 100%"
            :disabled="!fieldForm.configId"
            :placeholder="fieldForm.configId ? '请选择系统标准字段（不是数据库列名）' : '请先选择接入配置'"
            :no-data-text="fieldForm.configId ? '当前场景暂无预置标准字段，请核对接入配置场景' : '请先选择接入配置'"
          >
            <el-option
              v-for="item in fieldTargetOptions"
              :key="item.value"
              :label="`${item.value}：${item.label}`"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              转换规则
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">{{ FIELD_TRANSFORM_TYPE_HELP_TEXT }}</div>
                </template>
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-select v-model="fieldForm.transformType" style="width: 100%">
            <el-option v-for="item in transformTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              转换表达式
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">{{ FIELD_TRANSFORM_RULE_HELP_TEXT }}</div>
                </template>
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="fieldForm.transformRule" />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              默认值
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">{{ FIELD_DEFAULT_VALUE_HELP_TEXT }}</div>
                </template>
                <el-icon class="help-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-input v-model="fieldForm.defaultValue" />
        </el-form-item>
        <el-form-item label="异常时处理方式">
          <el-select v-model="fieldForm.errorStrategy" style="width: 100%">
            <el-option v-for="item in fieldErrorStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否必填"><el-switch v-model="fieldForm.requiredFlag" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="fieldForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="fieldForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fieldDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="fieldSaving" @click="submitField">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statusDialogVisible" :title="currentStatusId ? '编辑状态映射' : '新增状态映射'" width="760px">
      <el-form label-width="130px">
        <el-form-item label="接入配置">
          <el-select v-model="statusForm.configId" filterable style="width: 100%">
            <el-option v-for="item in moduleConfigOptions" :key="item.id" :label="item.configName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="第三方状态编码"><el-input v-model="statusForm.sourceStatusCode" /></el-form-item>
        <el-form-item label="第三方状态名称"><el-input v-model="statusForm.sourceStatusName" /></el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              系统标准状态
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">
                    这里选择的是系统标准状态值，不是数据库字段名；下拉项会随接入配置对应的业务场景自动切换。若当前场景没有候选值，通常表示应优先通过字段映射回写业务字段，而不是依赖状态映射；保存后系统会自动生成匹配优先级
                  </div>
                </template>
                <el-icon class="help-icon" @click.stop.prevent @mousedown.stop.prevent><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-select
            v-model="statusForm.targetStatusCode"
            filterable
            style="width: 100%"
            :disabled="!statusForm.configId"
            :placeholder="statusForm.configId ? '请选择系统标准状态' : '请先选择接入配置'"
            :no-data-text="statusForm.configId ? '当前场景暂无预置标准状态；请优先核对是否应改配字段映射' : '请先选择接入配置'"
          >
            <el-option v-for="item in statusTargetOptions" :key="item.value" :label="`${item.value}：${item.label}`" :value="item.value" />
          </el-select>
          <el-alert
            v-if="statusForm.configId && !statusSceneSupportsPresetStates"
            type="warning"
            :closable="false"
            show-icon
            title="当前场景没有可用的系统标准状态，通常应优先配置字段映射；状态映射不支持继续新增或启用。"
            class="inline-alert"
          />
          <el-alert
            v-else-if="statusTargetIsHistoricalLegacy"
            type="error"
            :closable="false"
            show-icon
            title="该记录正在使用历史异常状态值。系统不再允许继续按启用状态保存；请改选当前场景允许的系统标准状态，或先停用/删除该记录。"
            class="inline-alert"
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="label-with-help">
              结束同步
              <el-tooltip trigger="hover" placement="top" effect="light" popper-class="integration-help-tooltip">
                <template #content>
                  <div class="help-tooltip-content">
                    第三方一旦返回并命中该状态映射后，把该状态视为该场景的终态，停止自动轮询第三方后续状态
                  </div>
                </template>
                <el-icon class="help-icon" @click.stop.prevent @mousedown.stop.prevent><QuestionFilled /></el-icon>
              </el-tooltip>
            </span>
          </template>
          <el-switch v-model="statusForm.finishFlag" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="启用状态"><el-switch v-model="statusForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="statusForm.remark" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="statusSaving" @click="submitStatus">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="taskDialogVisible" title="执行同步任务" width="720px">
      <el-form label-width="120px">
        <el-form-item label="接入配置">
          <el-select v-model="taskForm.configId" filterable style="width: 100%">
            <el-option v-for="item in activeTaskModuleConfigOptions" :key="item.id" :label="item.configName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务模块">
          <el-select v-model="taskForm.bizModule" style="width: 100%">
            <el-option v-for="item in bizModuleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务场景">
          <el-select v-model="taskForm.bizScene" style="width: 100%">
            <el-option v-for="item in sceneOptionsForTask" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务主键ID">
          <el-input v-model="taskForm.bizId" inputmode="numeric" placeholder="仅支持大于0的正整数" style="width: 100%" />
        </el-form-item>
        <el-form-item label="业务编号"><el-input v-model="taskForm.bizNo" /></el-form-item>
        <el-form-item label="外部单号"><el-input v-model="taskForm.externalNo" /></el-form-item>
        <el-form-item label="维护方式">
          <el-radio-group v-model="taskForm.maintenanceMode">
            <el-radio v-for="item in modeOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="方式来源">
          <el-select v-model="taskForm.modeSource" style="width: 100%">
            <el-option v-for="item in taskModeSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="锁定方式"><el-switch v-model="taskForm.modeLocked" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="只查询不覆盖"><el-switch v-model="taskForm.queryOnly" :active-value="1" :inactive-value="0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="taskSubmitting" @click="submitTask">执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="syncLogDetailVisible" title="同步日志详情" width="1080px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="业务编号">{{ detailText(currentSyncLog?.bizNo) }}</el-descriptions-item>
          <el-descriptions-item label="外部单号">{{ detailText(currentSyncLog?.externalNo) }}</el-descriptions-item>
          <el-descriptions-item label="接入配置">{{ detailText(currentSyncLog?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="第三方平台">{{ detailText(currentSyncLog?.providerName || currentSyncLog?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailText(currentSyncLog?.orgName) }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ detailText(currentSyncLog?.taskNo) }}</el-descriptions-item>
          <el-descriptions-item label="业务模块">{{ bizModuleLabel(currentSyncLog?.bizModule || '') }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ sceneLabel(currentSyncLog?.bizModule || '', currentSyncLog?.bizScene || '') }}</el-descriptions-item>
          <el-descriptions-item label="任务类型">{{ taskTypeLabel(currentSyncLog?.taskType) }}</el-descriptions-item>
          <el-descriptions-item label="同步状态">{{ syncStatusLabel(currentSyncLog?.syncStatus) }}</el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ triggerTypeLabel(currentSyncLog?.triggerType) }}</el-descriptions-item>
          <el-descriptions-item label="触发人">{{ detailText(currentSyncLog?.operatorName) }}</el-descriptions-item>
          <el-descriptions-item label="耗时(ms)">{{ detailText(currentSyncLog?.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="错误编码">{{ detailText(currentSyncLog?.errorCode) }}</el-descriptions-item>
          <el-descriptions-item label="同步时间">{{ detailText(currentSyncLog?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="结果说明" :span="2">{{ detailText(currentSyncLog?.resultMessage) }}</el-descriptions-item>
          <el-descriptions-item label="业务回写结果" :span="2">{{ detailText(currentSyncLog?.writeBackResult) }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ detailText(currentSyncLog?.errorMessage) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="detail-section__title">人工处理闭环</div>
          <el-form v-if="currentSyncLog" label-width="100px" class="sync-log-handle-form">
            <el-form-item label="处理状态">
              <el-select v-model="currentSyncLog.handleStatus" style="width: 220px">
                <el-option v-for="item in syncLogHandleStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="处理备注">
              <el-input v-model="currentSyncLog.handleRemark" type="textarea" :rows="3" placeholder="可填写人工判断、已确认原因或后续复核说明" />
            </el-form-item>
            <el-form-item label="最近处理人">
              <span>{{ detailText(currentSyncLog.handledByName || currentSyncLog.handledBy) }}</span>
            </el-form-item>
            <el-form-item label="最近处理时间">
              <span>{{ detailText(currentSyncLog.handledAt) }}</span>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSyncLogHandleStatus">保存处理结果</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">请求概要</div>
          <pre class="detail-json">{{ formatJsonContent(currentSyncLog?.requestPayload) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">请求头</div>
          <pre class="detail-json">{{ formatJsonContent(currentSyncLog?.requestHeaders) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">请求体</div>
          <pre class="detail-json">{{ formatJsonContent(currentSyncLog?.requestBody) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">响应报文</div>
          <pre class="detail-json">{{ formatJsonContent(currentSyncLog?.responsePayload) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">标准化结果</div>
          <pre class="detail-json">{{ formatJsonContent(currentSyncLog?.normalizedPayload) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="syncLogDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="callbackLogDetailVisible" title="回调日志详情" width="1080px">
      <div class="detail-dialog">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="配置名称">{{ detailText(currentCallbackLog?.configName) }}</el-descriptions-item>
          <el-descriptions-item label="第三方平台">{{ detailText(currentCallbackLog?.providerName || currentCallbackLog?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="平台编码">{{ detailText(currentCallbackLog?.providerCode) }}</el-descriptions-item>
          <el-descriptions-item label="所属组织">{{ detailText(currentCallbackLog?.orgName) }}</el-descriptions-item>
          <el-descriptions-item label="外部单号">{{ detailText(currentCallbackLog?.externalNo) }}</el-descriptions-item>
          <el-descriptions-item label="业务模块">{{ bizModuleLabel(currentCallbackLog?.bizModule || '') }}</el-descriptions-item>
          <el-descriptions-item label="业务场景">{{ sceneLabel(currentCallbackLog?.bizModule || '', currentCallbackLog?.bizScene || '') }}</el-descriptions-item>
          <el-descriptions-item label="来源IP">{{ detailText(currentCallbackLog?.clientIp) }}</el-descriptions-item>
          <el-descriptions-item label="幂等键">{{ detailText(currentCallbackLog?.idempotentKey) }}</el-descriptions-item>
          <el-descriptions-item label="验签结果">{{ signResultLabel(currentCallbackLog?.signResult) }}</el-descriptions-item>
          <el-descriptions-item label="处理状态">{{ callbackProcessStatusLabel(currentCallbackLog?.processStatus) }}</el-descriptions-item>
          <el-descriptions-item label="任务编号">{{ detailText(currentCallbackLog?.taskNo) }}</el-descriptions-item>
          <el-descriptions-item label="回调日志业务编号">{{ detailText(currentCallbackLog?.bizNo) }}</el-descriptions-item>
          <el-descriptions-item label="回调时间">{{ detailText(currentCallbackLog?.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="回调地址">{{ detailText(currentCallbackLog?.callbackUri) }}</el-descriptions-item>
          <el-descriptions-item label="处理结果" :span="2">{{ detailText(currentCallbackLog?.processResult) }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ detailText(currentCallbackLog?.errorMessage) }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <div class="detail-section__title">请求头</div>
          <pre class="detail-json">{{ formatJsonContent(currentCallbackLog?.callbackHeaders) }}</pre>
        </div>
        <div class="detail-section">
          <div class="detail-section__title">原始报文</div>
          <pre class="detail-json">{{ formatJsonContent(currentCallbackLog?.callbackPayload) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="callbackLogDetailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.integration-page {
  padding: 16px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px;
  background: #fff;
}

.stat-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.stat-value.danger {
  color: #f56c6c;
}

.triple-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.secret-block {
  width: 100%;
}

.secret-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 12px;
  margin-bottom: 8px;
}

.detail-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.detail-dialog--scroll {
  max-height: min(72vh, 720px);
  overflow: auto;
  padding-right: 4px;
}

.detail-section__title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.detail-json {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 280px;
  overflow: auto;
  margin: 0;
}

.detail-empty {
  padding: 16px 0;
  color: #909399;
}

.detail-value {
  display: block;
  min-width: 0;
  line-height: 1.6;
}

.detail-value--break,
.detail-link {
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.detail-link {
  display: inline-block;
  max-width: 100%;
  line-height: 1.6;
  vertical-align: top;
}

.sync-log-handle-form {
  margin-top: 8px;
}

.dialog-alert {
  margin-bottom: 16px;
}

.inline-alert {
  margin-top: 12px;
}

.label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.help-icon {
  cursor: pointer;
  color: #909399;
  font-size: 14px;
}

.help-icon:hover {
  color: #409eff;
}

.help-tooltip-content {
  max-width: 320px;
  line-height: 1.7;
  color: #606266;
}

:deep(.file-detail-dialog .el-dialog) {
  max-width: calc(100vw - 32px);
}

:deep(.file-detail-dialog .el-dialog__body) {
  padding-top: 14px;
  overflow: hidden;
}

:deep(.file-detail-descriptions .el-descriptions__table) {
  width: 100%;
  table-layout: fixed;
}

:deep(.file-detail-descriptions .el-descriptions__cell) {
  vertical-align: top;
}

:deep(.file-detail-descriptions .el-descriptions__label.el-descriptions__cell) {
  width: 136px;
  min-width: 136px;
  white-space: normal;
  word-break: break-word;
}

:deep(.file-detail-descriptions .el-descriptions__content.el-descriptions__cell) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
}

:deep(.file-detail-descriptions .el-link__inner) {
  white-space: normal;
  overflow-wrap: anywhere;
  word-break: break-word;
  text-align: left;
}

@media (max-width: 1280px) {
  .stat-grid,
  .triple-grid {
    grid-template-columns: 1fr;
  }
}
</style>
