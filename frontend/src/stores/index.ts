import { createPinia } from 'pinia'

const pinia = createPinia()

export default pinia

// 导出所有 store
export * from './modules/user'
export * from './modules/material'
export * from './modules/recipe'
export * from './modules/warehouse'
export * from './modules/inbound'
export * from './modules/cook'
export * from './modules/device'
export * from './modules/sample'
