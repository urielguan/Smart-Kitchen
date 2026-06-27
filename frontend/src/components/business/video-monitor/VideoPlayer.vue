<script setup lang="ts">
import { ref, onMounted, onUnmounted, onActivated, onDeactivated, watch, computed } from 'vue'
import Hls from 'hls.js'
import {
  VideoPlay,
  VideoPause,
  Refresh,
  FullScreen,
  Mute,
  Microphone
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

interface Props {
  /** 视频流地址 */
  src?: string
  /** 播放器类型: hls/flv/native/mjpeg */
  type?: 'hls' | 'flv' | 'native' | 'mjpeg'
  /** 是否显示控制栏 */
  showControls?: boolean
  /** 是否自动播放 */
  autoplay?: boolean
  /** 是否静音 */
  muted?: boolean
  /** 海报图 */
  poster?: string
  /** 是否为直播流 */
  isLive?: boolean
  /** 播放器高度 */
  height?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'native',
  showControls: true,
  autoplay: false,
  muted: true,
  isLive: false,
  height: '100%'
})

const emit = defineEmits<{
  (e: 'ready'): void
  (e: 'play'): void
  (e: 'pause'): void
  (e: 'ended'): void
  (e: 'error', error: Error): void
  (e: 'timeupdate', currentTime: number): void
}>()

// 视频元素引用
const videoRef = ref<HTMLVideoElement | null>(null)
const mjpegRef = ref<HTMLImageElement | null>(null)

// hls.js 实例
let hlsInstance: Hls | null = null
let hlsRetryCount = 0
const HLS_MAX_RETRY = 5

// 播放状态
const isPlaying = ref(false)
const isMuted = ref(props.muted)
const isLoading = ref(false)
const hasError = ref(false)
const errorMessage = ref('')
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(80)

// 显示控制栏
const showControlBar = ref(true)
let hideControlTimer: ReturnType<typeof setTimeout> | null = null

// 进度百分比
const progress = computed(() => {
  if (duration.value === 0) return 0
  return (currentTime.value / duration.value) * 100
})

// 格式化时间
const formatTime = (seconds: number): string => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 初始化播放器
const initPlayer = async () => {
  if (!props.src) return
  if (props.type === 'mjpeg') {
    isLoading.value = false
    hasError.value = false
    errorMessage.value = ''
    isPlaying.value = true
    return
  }
  if (!videoRef.value) return

  // 先销毁旧的 hls 实例
  destroyHls()

  isLoading.value = true
  hasError.value = false
  errorMessage.value = ''

  try {
    if (props.type === 'hls') {
      // 强制设置 muted 以确保浏览器允许自动播放
      videoRef.value.muted = true

      const isSafariNativeHls = /Safari/i.test(navigator.userAgent) && !/Chrome|Chromium|Edg/i.test(navigator.userAgent)

      if (Hls.isSupported()) {
        // Chrome/Edge 等浏览器优先使用 hls.js，避免误走原生分支导致 m3u8 直接喂给 video 解复用失败。
        const hls = new Hls({
          enableWorker: true,
          lowLatencyMode: true,
          maxBufferLength: 10,
          maxMaxBufferLength: 15,
          liveSyncDurationCount: 2,
          liveMaxLatencyDurationCount: 5,
          liveDurationInfinity: true,
          progressive: true,
          fragLoadingTimeOut: 10000,
          fragLoadingMaxRetry: 6,
          fragLoadingMaxRetryTimeout: 64000,
          manifestLoadingTimeOut: 10000,
          manifestLoadingMaxRetry: 4,
          manifestLoadingMaxRetryTimeout: 16000,
          levelLoadingTimeOut: 10000,
          levelLoadingMaxRetry: 4
        })
        hlsRetryCount = 0
        hls.loadSource(props.src)
        hls.attachMedia(videoRef.value)
        hls.on(Hls.Events.MANIFEST_PARSED, () => {
          if (props.autoplay) play()
        })
        hls.on(Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) {
            hlsRetryCount++
            if (hlsRetryCount > HLS_MAX_RETRY) {
              console.warn(`[HLS] 超过最大重试次数(${HLS_MAX_RETRY})，重建播放器`)
              hlsRetryCount = 0
              // 自动重建播放器而不是显示错误
              setTimeout(() => initPlayer(), 1000)
              return
            }
            console.warn(`[HLS] 致命错误(${data.type})，第${hlsRetryCount}次恢复，详情:`, data.details)
            switch (data.type) {
              case Hls.ErrorTypes.NETWORK_ERROR:
                // 延迟重试，避免立即重试导致快速循环
                setTimeout(() => hls.startLoad(), 1000)
                break
              case Hls.ErrorTypes.MEDIA_ERROR:
                hls.recoverMediaError()
                break
              default:
                setTimeout(() => initPlayer(), 2000)
                break
            }
          }
        })
        // 恢复成功时重置计数
        hls.on(Hls.Events.FRAG_LOADED, () => {
          if (hlsRetryCount > 0) hlsRetryCount = 0
        })
        hlsInstance = hls
      } else if (isSafariNativeHls && videoRef.value.canPlayType('application/vnd.apple.mpegurl')) {
        // Safari 原生 HLS 支持
        videoRef.value.src = props.src
        if (props.autoplay) {
          videoRef.value.play().catch(() => { isPlaying.value = false })
        }
      } else {
        handleError(new Error('浏览器不支持 HLS 播放'))
      }
    } else if (props.type === 'flv') {
      // FLV流需要flv.js
      console.warn('FLV format requires flv.js library')
      videoRef.value.src = props.src
    } else {
      videoRef.value.src = props.src
    }

    if (props.autoplay && props.type !== 'hls') {
      await play()
    }
  } catch (e) {
    handleError(e as Error)
  } finally {
    isLoading.value = false
  }
}

// 销毁 hls 实例
const destroyHls = () => {
  if (hlsInstance) {
    hlsInstance.destroy()
    hlsInstance = null
  }
}

// 播放
const play = async () => {
  if (props.type === 'mjpeg') {
    isPlaying.value = true
    emit('play')
    return
  }
  if (!videoRef.value) return

  try {
    // 确保静音状态生效（浏览器允许 muted 自动播放）
    if (props.autoplay) {
      videoRef.value.muted = true
    }
    await videoRef.value.play()
    isPlaying.value = true
    emit('play')
  } catch (e: any) {
    // 浏览器阻止自动播放时，延迟重试一次（确保 muted 生效）
    if (e.name === 'NotAllowedError') {
      isPlaying.value = false
      if (props.autoplay) {
        setTimeout(() => {
          if (videoRef.value && !isPlaying.value) {
            videoRef.value.muted = true
            videoRef.value.play().then(() => {
              isPlaying.value = true
              emit('play')
            }).catch(() => { /* 放弃重试 */ })
          }
        }, 500)
      }
    } else {
      handleError(e as Error)
    }
  }
}

// 暂停
const pause = () => {
  if (props.type === 'mjpeg') {
    isPlaying.value = false
    emit('pause')
    return
  }
  if (!videoRef.value) return

  videoRef.value.pause()
  isPlaying.value = false
  emit('pause')
}

// 切换播放/暂停
const togglePlay = () => {
  if (isPlaying.value) {
    pause()
  } else {
    play()
  }
}

// 切换静音
const toggleMute = () => {
  if (props.type === 'mjpeg') return
  if (!videoRef.value) return

  videoRef.value.muted = !videoRef.value.muted
  isMuted.value = videoRef.value.muted
}

// 设置音量
const setVolume = (val: number) => {
  if (props.type === 'mjpeg') return
  if (!videoRef.value) return

  videoRef.value.volume = val / 100
  volume.value = val
}

// 全屏
const toggleFullscreen = async () => {
  const target = videoRef.value || mjpegRef.value
  if (!target) return

  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await target.requestFullscreen()
    }
  } catch (e) {
    ElMessage.warning('全屏功能不可用')
  }
}

// 跳转到指定时间
const seekTo = (time: number) => {
  if (props.type === 'mjpeg') return
  if (!videoRef.value) return
  videoRef.value.currentTime = time
}

// 进度条点击
const handleProgressClick = (e: MouseEvent) => {
  if (props.type === 'mjpeg') return
  if (!videoRef.value || props.isLive) return

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const percent = (e.clientX - rect.left) / rect.width
  const time = percent * duration.value
  seekTo(time)
}

// 重载
const reload = () => {
  initPlayer()
}

// 错误处理
const handleError = (error: Error) => {
  hasError.value = true
  errorMessage.value = error.message || '视频加载失败'
  emit('error', error)
}

// 事件处理
const onLoadedMetadata = () => {
  if (videoRef.value) {
    duration.value = videoRef.value.duration
  }
  isLoading.value = false
  emit('ready')
}

const onMjpegLoad = () => {
  isLoading.value = false
  isPlaying.value = true
  hasError.value = false
  emit('ready')
}

const onTimeUpdate = () => {
  if (videoRef.value) {
    currentTime.value = videoRef.value.currentTime
    emit('timeupdate', currentTime.value)
  }
}

const onEnded = () => {
  isPlaying.value = false
  emit('ended')
}

const onError = () => {
  handleError(new Error('视频加载失败'))
}

// 显示/隐藏控制栏
const showControlsTemporarily = () => {
  showControlBar.value = true
  if (hideControlTimer) {
    clearTimeout(hideControlTimer)
  }
  hideControlTimer = setTimeout(() => {
    if (isPlaying.value) {
      showControlBar.value = false
    }
  }, 3000)
}

// 监听src变化
watch(() => props.src, () => {
  if (props.src) {
    initPlayer()
  }
})

// 监听静音状态
watch(() => props.muted, (val) => {
  isMuted.value = val
  if (videoRef.value) {
    videoRef.value.muted = val
  }
})

onMounted(() => {
  if (props.src) {
    initPlayer()
  }
})

// keep-alive 激活时重建播放器（页面从缓存恢复）
onActivated(() => {
  if (props.src) {
    initPlayer()
  }
})

// keep-alive 缓存时销毁 HLS 释放资源
onDeactivated(() => {
  destroyHls()
  isPlaying.value = false
})

onUnmounted(() => {
  if (hideControlTimer) {
    clearTimeout(hideControlTimer)
  }
  destroyHls()
})

// 暴露方法
defineExpose({
  play,
  pause,
  togglePlay,
  seekTo,
  reload,
  toggleFullscreen,
  /** 抓取当前视频帧，返回 JPEG Blob */
  captureFrame: (): Promise<Blob> => {
    return new Promise((resolve, reject) => {
      if (!videoRef.value) {
        if (!mjpegRef.value) {
          reject(new Error('视频未加载'))
          return
        }
        const img = mjpegRef.value
        const canvas = document.createElement('canvas')
        canvas.width = img.naturalWidth || 1280
        canvas.height = img.naturalHeight || 720
        const ctx = canvas.getContext('2d')
        if (!ctx) {
          reject(new Error('Canvas 不支持'))
          return
        }
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
        canvas.toBlob(
          (blob) => {
            if (blob) {
              resolve(blob)
            } else {
              reject(new Error('截图生成失败'))
            }
          },
          'image/jpeg',
          0.9
        )
        return
      }
      const video = videoRef.value
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth || 1280
      canvas.height = video.videoHeight || 720
      const ctx = canvas.getContext('2d')
      if (!ctx) {
        reject(new Error('Canvas 不支持'))
        return
      }
      ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
      canvas.toBlob(
        (blob) => {
          if (blob) {
            resolve(blob)
          } else {
            reject(new Error('截图生成失败'))
          }
        },
        'image/jpeg',
        0.9
      )
    })
  }
})
</script>

<template>
  <div
    class="video-player-wrapper"
    :style="{ height }"
    @mousemove="showControlsTemporarily"
    @mouseleave="showControlBar = false"
  >
    <!-- 视频元素 -->
    <video
      v-if="type !== 'mjpeg'"
      ref="videoRef"
      class="video-element"
      :poster="poster"
      :muted="muted"
      playsinline
      webkit-playsinline
      @loadedmetadata="onLoadedMetadata"
      @timeupdate="onTimeUpdate"
      @ended="onEnded"
      @error="onError"
      @waiting="isLoading = true"
      @playing="isLoading = false"
    />

    <img
      v-else-if="src"
      ref="mjpegRef"
      class="video-element video-element--mjpeg"
      :src="src"
      :alt="'MJPEG stream'"
      @load="onMjpegLoad"
      @error="onError"
    />

    <!-- 加载中 -->
    <div v-if="isLoading" class="loading-overlay">
      <el-icon class="is-loading" :size="48"><Refresh /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 错误提示 -->
    <div v-if="hasError" class="error-overlay">
      <el-icon :size="48"><VideoPlay /></el-icon>
      <span>{{ errorMessage }}</span>
      <el-button type="primary" size="small" @click="reload">重试</el-button>
    </div>

    <!-- 无视频源 -->
    <div v-if="!src && !hasError" class="empty-overlay">
      <el-icon :size="48"><VideoPlay /></el-icon>
      <span>暂无视频信号</span>
    </div>

    <!-- 直播标识 -->
    <div v-if="isLive && isPlaying" class="live-badge">
      <span class="live-dot"></span>
      直播
    </div>

    <!-- 控制栏 -->
    <div
      v-if="showControls && !hasError && src"
      class="control-bar"
      :class="{ visible: showControlBar || !isPlaying }"
    >
      <!-- 播放/暂停按钮 -->
      <div class="control-left">
        <el-button
          v-if="type !== 'mjpeg'"
          type="primary"
          :icon="isPlaying ? VideoPause : VideoPlay"
          circle
          size="small"
          @click="togglePlay"
        />
        <el-button
          v-else
          type="primary"
          :icon="Refresh"
          circle
          size="small"
          @click="reload"
        />
      </div>

      <!-- 进度条 (非直播) -->
      <div v-if="!isLive" class="control-center">
        <span class="time">{{ formatTime(currentTime) }}</span>
        <div class="progress-bar" @click="handleProgressClick">
          <div class="progress-played" :style="{ width: `${progress}%` }"></div>
          <div class="progress-handle" :style="{ left: `${progress}%` }"></div>
        </div>
        <span class="time">{{ formatTime(duration) }}</span>
      </div>

      <!-- 直播进度 -->
      <div v-else class="control-center live-time">
        <span>{{ new Date().toLocaleTimeString() }}</span>
      </div>

      <!-- 音量和全屏 -->
      <div class="control-right">
        <el-popover
          v-if="type !== 'mjpeg'"
          placement="top"
          :width="40"
          trigger="hover"
        >
          <template #reference>
            <el-button
              :icon="isMuted ? Mute : Microphone"
              circle
              size="small"
              @click="toggleMute"
            />
          </template>
          <div class="volume-slider">
            <el-slider
              v-model="volume"
              vertical
              :height="'80px'"
              @input="setVolume"
            />
          </div>
        </el-popover>

        <el-button
          :icon="FullScreen"
          circle
          size="small"
          @click="toggleFullscreen"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.video-player-wrapper {
  position: relative;
  width: 100%;
  background: #000;
  border-radius: 4px;
  overflow: hidden;

  &:hover {
    .control-bar {
      opacity: 1;
    }
  }
}

.video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.loading-overlay,
.error-overlay,
.empty-overlay,
.click-play-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-size: 14px;

  .el-icon {
    color: rgba(255, 255, 255, 0.8);
  }
}

.click-play-overlay {
  cursor: pointer;
  z-index: 10;

  &:hover {
    background: rgba(0, 0, 0, 0.5);

    .el-icon {
      color: #fff;
      transform: scale(1.1);
    }
  }

  .el-icon {
    transition: color 0.2s, transform 0.2s;
  }
}

.live-badge {
  position: absolute;
  top: 10px;
  right: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(245, 108, 108, 0.9);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  border-radius: 4px;

  .live-dot {
    width: 6px;
    height: 6px;
    background: #fff;
    border-radius: 50%;
    animation: pulse 1.5s infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.control-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.7));
  opacity: 0;
  transition: opacity 0.3s;

  &.visible {
    opacity: 1;
  }

  .control-left,
  .control-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .control-center {
    flex: 1;
    display: flex;
    align-items: center;
    margin: 0 16px;

    &.live-time {
      justify-content: center;
      color: #fff;
      font-size: 14px;
    }
  }

  .time {
    color: #fff;
    font-size: 12px;
    min-width: 45px;
  }

  .progress-bar {
    flex: 1;
    height: 4px;
    background: rgba(255, 255, 255, 0.3);
    border-radius: 2px;
    margin: 0 8px;
    cursor: pointer;
    position: relative;

    .progress-played {
      height: 100%;
      background: $primary-color;
      border-radius: 2px;
    }

    .progress-handle {
      position: absolute;
      top: 50%;
      width: 12px;
      height: 12px;
      background: #fff;
      border-radius: 50%;
      transform: translate(-50%, -50%);
      opacity: 0;
      transition: opacity 0.2s;
    }

    &:hover .progress-handle {
      opacity: 1;
    }
  }
}

.volume-slider {
  display: flex;
  justify-content: center;
  padding: 8px 0;
}

:deep(.el-button.is-circle) {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: #fff;

  &:hover {
    background: rgba(255, 255, 255, 0.3);
  }
}
</style>
