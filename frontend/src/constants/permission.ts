export const SUPPLIER_PERMISSIONS = {
  CREATE: 'supplier:create',
  APPROVE: 'supplier:approve',
  EDIT: 'supplier:edit',
  DELETE: 'supplier:delete',
  CANCEL: 'supplier:cancel',
  STATUS: 'supplier:status'
} as const

export const PURCHASE_PLAN_PERMISSIONS = {
  CREATE: 'purchasePlan:create',
  EDIT: 'purchasePlan:edit',
  DELETE: 'purchasePlan:delete',
  APPROVE: 'purchasePlan:approve',
  GENERATE_ORDER: 'purchasePlan:generateOrder'
} as const

export const PURCHASE_PERMISSIONS = {
  CREATE: 'purchase:create',
  EDIT: 'purchase:edit',
  DELETE: 'purchase:delete',
  APPROVE: 'purchase:approve',
  VOID: 'purchase:void',
  VOID_AUDIT: 'purchase:void-audit',
  LOGISTICS: 'purchase:logistics',
  INSPECTION: 'purchase:inspection',
  TRACEABILITY: 'purchase:traceability'
} as const

export const WAREHOUSE_PERMISSIONS = {
  CREATE: 'warehouse:create',
  EDIT: 'warehouse:edit',
  DELETE: 'warehouse:delete',
  LOCATION_CREATE: 'warehouse:location:create',
  LOCATION_EDIT: 'warehouse:location:edit',
  LOCATION_DELETE: 'warehouse:location:delete'
} as const

export const MATERIAL_PERMISSIONS = {
  CREATE: 'material:create',
  EDIT: 'material:edit',
  DELETE: 'material:delete',
  STATUS: 'material:status',
  IMPORT: 'material:import',
  EXPORT: 'material:export'
} as const

export const INBOUND_PERMISSIONS = {
  CREATE: 'inbound:create',
  EDIT: 'inbound:edit',
  DELETE: 'inbound:delete',
  SUBMIT: 'inbound:submit',
  CANCEL: 'inbound:cancel',
  AUDIT: 'inbound:audit'
} as const

export const OUTBOUND_PERMISSIONS = {
  CREATE: 'outbound:create',
  EDIT: 'outbound:edit',
  DELETE: 'outbound:delete',
  SUBMIT: 'outbound:submit',
  WITHDRAW: 'outbound:withdraw',
  AUDIT: 'outbound:audit',
  REVERSE: 'outbound:reverse',
  EXECUTE: 'outbound:execute'
} as const

export const STOCKTAKE_PERMISSIONS = {
  CREATE: 'stocktake:create',
  EDIT: 'stocktake:edit',
  SUBMIT: 'stocktake:submit',
  APPROVE: 'stocktake:approve',
  VOID: 'stocktake:void',
  EXPORT: 'stocktake:export'
} as const

export const INVENTORY_PERMISSIONS = {
  EXPORT: 'inventory:export'
} as const

export const ORG_PERMISSIONS = {
  CREATE: 'org:create',
  EDIT: 'org:edit',
  DELETE: 'org:delete',
  STATUS: 'org:status',
  IMPORT: 'org:import',
  EXPORT: 'org:export'
} as const

export const EMPLOYEE_PERMISSIONS = {
  CREATE: 'employee:create',
  EDIT: 'employee:edit',
  DELETE: 'employee:delete',
  STATUS: 'employee:status',
  IMPORT: 'employee:import',
  EXPORT: 'employee:export'
} as const

export const DICT_CATEGORY_PERMISSIONS = {
  CREATE: 'dict-category:create',
  EDIT: 'dict-category:edit',
  DELETE: 'dict-category:delete',
  STATUS: 'dict-category:status'
} as const

export const ROLE_PERMISSIONS = {
  CREATE: 'role:create',
  EDIT: 'role:edit',
  DELETE: 'role:delete',
  GROUP_CREATE: 'role:group:create',
  GROUP_EDIT: 'role:group:edit',
  GROUP_DELETE: 'role:group:delete',
  MEMBER_ADD: 'role:member:add',
  MEMBER_REMOVE: 'role:member:remove'
} as const

export const EVALUATION_PERMISSIONS = {
  DISPATCH: 'evaluation:dispatch',
  PROCESS: 'evaluation:process',
  REPLY: 'evaluation:reply',
  EXPORT: 'evaluation:export',
  COMPLAINT_EXPORT: 'complaint:export'
} as const

export const MORNING_CHECK_PERMISSIONS = {
  FACE_ENROLL: 'morning-check:faceEnroll',
  START: 'morning-check:start',
  ARCHIVE: 'morning-check:archive',
  CERT_CREATE: 'morning-check:cert:create',
  CERT_EDIT: 'morning-check:cert:edit',
  CERT_DELETE: 'morning-check:cert:delete',
  CERT_EXPORT: 'morning-check:cert:export'
} as const

export const DEVICE_PERMISSIONS = {
  CREATE: 'device:create',
  EDIT: 'device:edit',
  DELETE: 'device:delete'
} as const

export const PLAN_PERMISSIONS = {
  CREATE: 'plan:create',
  EDIT: 'plan:edit',
  DELETE: 'plan:delete',
  SUBMIT: 'plan:submit',
  APPROVE: 'plan:approve',
  ADJUST: 'plan:adjust',
  EXPORT: 'plan:export',
  IMPORT: 'plan:import'
} as const

export const PLAN_ADJUSTMENT_PERMISSIONS = {
  PLAN_ADJUSTMENT: 'plan-adjustment',
  APPROVE: 'plan-adjustment:approve',
  EXPORT: 'plan-adjustment:export'
} as const

export const RECIPE_PERMISSIONS = {
  CREATE: 'recipe:create',
  EDIT: 'recipe:edit',
  DELETE: 'recipe:delete'
} as const

export const VIDEO_PLAYBACK_PERMISSIONS = {
  VIDEO_PLAYBACK: 'video-playback',
  EXPORT: 'video-playback:export',
  DOWNLOAD: 'video-playback:download',
  DELETE: 'video-playback:delete',
  CLIP_EXTRACT: 'video-playback:clipExtract',
  CLIP_DELETE: 'video-playback:clipDelete',
  SCREENSHOT_CAPTURE: 'video-playback:screenshotCapture',
  SCREENSHOT_DELETE: 'video-playback:screenshotDelete'
} as const

export const BEHAVIOR_ANALYSIS_PERMISSIONS = {
  BEHAVIOR_ANALYSIS: 'behavior-analysis'
} as const

export const VIOLATION_PERMISSIONS = {
  VIOLATION: 'violation',
  HANDLE: 'violation:handle',
  REVIEW: 'violation:review'
} as const

export const ALERT_PERMISSIONS = {
  LIST: 'alert:list',
  DISPATCH: 'alert:dispatch',
  DISPATCH_TAB: 'alert:work-order',
  PROCESS: 'alert:process',
  REVIEW: 'alert:review',
  CLOSE: 'alert:close',
  EXPORT: 'alert:export'
} as const

export const ALERT_RULE_PERMISSIONS = {
  ALERT_RULE: 'alert-rule',
  CREATE: 'alert-rule:create',
  EDIT: 'alert-rule:edit',
  DELETE: 'alert-rule:delete',
  STATUS: 'alert-rule:status'
} as const

export const NOTIFICATION_PERMISSIONS = {
  LIST: 'notification',
  MARK_READ: 'notification:markRead',
  DELETE: 'notification:delete'
} as const

export const SAMPLE_PERMISSIONS = {
  VIEW: 'sample',
  CREATE: 'sample:create',
  HISTORY_SUPPLEMENT: 'sample:historySupplement',
  MANUAL_DISPOSAL_SUPPLEMENT: 'sample:manualDisposalSupplement',
  EDIT: 'sample:edit',
  DISPOSE: 'sample:dispose',
  VOID: 'sample:void',
  ARCHIVE: 'sample:archive',
  AI_EVALUATE: 'sample:aiEvaluate',
  EXPORT: 'sample:export',
  BATCH_VOID: 'sample:void',
  BATCH_ARCHIVE: 'sample:archive'
} as const

export const INTEGRATION_PERMISSIONS = {
  VIEW: 'integration-management',
  CREATE: 'integration-management:create',
  EDIT: 'integration-management:edit',
  DELETE: 'integration-management:delete',
  STATUS: 'integration-management:status',
  TEST: 'integration-management:test',
  SYNC: 'integration-management:sync',
  RETRY: 'integration-management:retry',
  VIEW_LOG: 'integration-management:view-log',
  VIEW_CALLBACK: 'integration-management:view-callback'
} as const
