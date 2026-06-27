/** 目标人群选项 */
export const TARGET_GROUP_OPTIONS = [
  { value: 'adult', label: '普通成人', description: '健康成年人' },
  { value: 'elderly', label: '老年人', description: '60岁以上老人，需易消化、低盐低脂' },
  { value: 'child', label: '儿童', description: '3-12岁儿童，需高蛋白、高钙' },
  { value: 'teenager', label: '青少年', description: '13-18岁青少年，需高能量、高蛋白' },
  { value: 'patient', label: '病人', description: '需特殊饮食照护' },
  { value: 'worker', label: '体力劳动者', description: '需高能量、高蛋白' }
]

/** 健康状况选项 */
export const HEALTH_STATUS_OPTIONS = [
  { value: 'diabetes', label: '糖尿病', description: '需低糖、低GI饮食' },
  { value: 'hypertension', label: '高血压', description: '需低盐、低脂饮食' },
  { value: 'hyperlipidemia', label: '高血脂', description: '需低脂、低胆固醇饮食' },
  { value: 'obesity', label: '肥胖', description: '需低热量、高纤维饮食' },
  { value: 'gout', label: '痛风', description: '需低嘌呤饮食' },
  { value: 'kidney_disease', label: '肾病', description: '需低蛋白、低盐饮食' },
  { value: 'stomach_disease', label: '胃病', description: '需易消化、温和饮食' },
  { value: 'anemia', label: '贫血', description: '需高铁、高蛋白饮食' }
]

/** 目标人群映射 */
export const TARGET_GROUP_MAP: Record<string, string> = {
  adult: '普通成人',
  elderly: '老年人',
  child: '儿童',
  teenager: '青少年',
  patient: '病人',
  worker: '体力劳动者'
}

/** 健康状况映射 */
export const HEALTH_STATUS_MAP: Record<string, string> = {
  diabetes: '糖尿病',
  hypertension: '高血压',
  hyperlipidemia: '高血脂',
  obesity: '肥胖',
  gout: '痛风',
  kidney_disease: '肾病',
  stomach_disease: '胃病',
  anemia: '贫血'
}
