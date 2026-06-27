/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_TITLE: string
  readonly VITE_BYPASS_LOGIN?: string
  readonly VITE_DEV_BYPASS_TOKEN?: string
  readonly VITE_DASHBOARD_DATA_SOURCE?: 'mock' | 'remote' | 'auto'
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
