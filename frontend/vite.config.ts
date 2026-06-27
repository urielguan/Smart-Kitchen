import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

const gatewayTarget = process.env.GATEWAY_TARGET || 'http://localhost:8080'
const scmServiceTarget = process.env.SCM_SERVICE_TARGET || 'http://localhost:8082'
const sysServiceTarget = process.env.SYS_SERVICE_TARGET || 'http://localhost:8089'
const recipeServiceTarget = process.env.RECIPE_SERVICE_TARGET || 'http://localhost:8084'
const deviceMediaTarget = process.env.DEVICE_MEDIA_TARGET || 'http://localhost:8088'
const apiProxyTimeout = Number(process.env.VITE_PROXY_TIMEOUT || 120000)

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api/v1/sys/notifications': {
        target: sysServiceTarget,
        changeOrigin: true,
        timeout: apiProxyTimeout,
        proxyTimeout: apiProxyTimeout
      },
      '/api/v1/scm': {
        target: scmServiceTarget,
        changeOrigin: true,
        timeout: apiProxyTimeout,
        proxyTimeout: apiProxyTimeout
      },
      '/api/v1/recipe': {
        target: recipeServiceTarget,
        changeOrigin: true,
        timeout: apiProxyTimeout,
        proxyTimeout: apiProxyTimeout
      },
      '/api': {
        target: gatewayTarget,
        changeOrigin: true,
        timeout: apiProxyTimeout,
        proxyTimeout: apiProxyTimeout
      },
      '/hls': {
        target: deviceMediaTarget,
        changeOrigin: true
      },
      '/recordings': {
        target: deviceMediaTarget,
        changeOrigin: true
      },
      '/clips': {
        target: deviceMediaTarget,
        changeOrigin: true
      },
      '/screenshots': {
        target: deviceMediaTarget,
        changeOrigin: true
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/assets/styles/variables.scss" as *;`
      }
    }
  }
})
