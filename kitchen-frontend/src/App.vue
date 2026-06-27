<script setup lang="ts">
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useCookStore } from '@/stores/modules/cook'
import TerminalLayout from '@/components/layout/TerminalLayout.vue'

const route = useRoute()
const showLayout = computed(() => !route.meta.hiddenLayout)
const appStore = useAppStore()
const cookStore = useCookStore()

const updateOnlineStatus = () => {
  appStore.setOnline(navigator.onLine)
}

onMounted(() => {
  updateOnlineStatus()
  window.addEventListener('online', updateOnlineStatus)
  window.addEventListener('offline', updateOnlineStatus)
})

onUnmounted(() => {
  window.removeEventListener('online', updateOnlineStatus)
  window.removeEventListener('offline', updateOnlineStatus)
})

watch(
  () => appStore.online,
  async (value) => {
    if (value) {
      await cookStore.syncOfflineQueue()
    }
  }
)
</script>

<template>
  <TerminalLayout v-if="showLayout">
    <RouterView />
  </TerminalLayout>
  <RouterView v-else />
</template>
