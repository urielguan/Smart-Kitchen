import { createI18n } from 'vue-i18n'
import { readStorage, storageKeys } from '@/utils/storage'
import type { SupportedLocale } from '@/types'
import zhCN from './zh-CN'
import enUS from './en-US'

const locale = readStorage<SupportedLocale>(storageKeys.locale, 'zh-CN')

const i18n = createI18n({
  legacy: false,
  locale,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS
  }
})

export default i18n
