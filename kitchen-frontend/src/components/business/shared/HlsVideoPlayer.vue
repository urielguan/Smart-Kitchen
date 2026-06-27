<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useHlsVideo } from '@/composables/useHlsVideo'

const props = withDefaults(defineProps<{
  src: string
  autoPlay?: boolean
  muted?: boolean
  title?: string
}>(), {
  autoPlay: true,
  muted: true,
  title: ''
})

const videoRef = ref<HTMLVideoElement | null>(null)
const srcRef = computed(() => props.src)
const { loading, error, init } = useHlsVideo(videoRef, srcRef)

const showReload = computed(() => error.value !== null)

const handleReload = () => {
  init()
}

onMounted(() => {
  if (props.src) init()
})
</script>

<template>
  <div class="hls-player">
    <div v-if="title" class="hls-player__title">{{ title }}</div>
    <div class="hls-player__wrap">
      <video
        ref="videoRef"
        class="hls-player__video"
        :autoplay="autoPlay"
        :muted="muted"
        playsinline
      />
      <!-- Loading overlay -->
      <div v-if="loading && src" class="hls-player__overlay">
        <div class="hls-player__spinner" />
        <span class="hls-player__overlay-text">加载中...</span>
      </div>
      <!-- Error overlay -->
      <div v-if="error" class="hls-player__overlay hls-player__overlay--error">
        <span class="hls-player__overlay-text">{{ error }}</span>
        <button class="hls-player__reload-btn" @click="handleReload">重试</button>
      </div>
      <!-- Empty state -->
      <div v-if="!src" class="hls-player__overlay">
        <span class="hls-player__overlay-text">暂无视频源</span>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/assets/styles/kds-theme' as *;

.hls-player {
  display: flex;
  flex-direction: column;
  border-radius: $kds-radius-md;
  overflow: hidden;
  background: #000;
  border: 1px solid $kds-border;
}

.hls-player__title {
  font-size: 11px;
  font-weight: 600;
  color: $kds-text-dim;
  padding: 6px 10px;
  background: $kds-surface;
  border-bottom: 1px solid $kds-border;
}

.hls-player__wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
}

.hls-player__video {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.hls-player__overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.6);
  z-index: 2;

  &--error {
    background: rgba(0, 0, 0, 0.8);
  }
}

.hls-player__overlay-text {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.hls-player__spinner {
  width: 24px;
  height: 24px;
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-top-color: #4f8cff;
  border-radius: 50%;
  animation: hls-spin 0.8s linear infinite;
}

.hls-player__reload-btn {
  font-size: 11px;
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  cursor: pointer;
  transition: all 0.15s;

  &:hover { background: rgba(255, 255, 255, 0.2); }
}

@keyframes hls-spin {
  to { transform: rotate(360deg); }
}
</style>
