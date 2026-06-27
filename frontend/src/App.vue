<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppLayout from '@/components/layout/AppLayout.vue'

const route = useRoute()

/** 是否显示布局 */
const showLayout = computed(() => {
  return !route.meta?.hiddenLayout && route.path !== '/login'
})
</script>

<template>
  <AppLayout v-if="showLayout">
    <RouterView />
  </AppLayout>
  <RouterView v-else />
</template>

<style>
#app {
  height: 100vh;
}
</style>