<template>
  <div class="face-camera">
    <!-- 视频预览 -->
    <div class="camera-preview">
      <video
        ref="videoRef"
        autoplay
        playsinline
        muted
        class="video-element"
        :class="{ 'mirrored': mirrored }"
        @loadedmetadata="onVideoLoaded"
      />
      <canvas ref="canvasRef" class="capture-canvas" />

      <!-- 人脸框提示 -->
      <div v-if="showGuide" class="face-guide">
        <div class="face-oval"></div>
      </div>

      <!-- 状态提示 -->
      <div v-if="status !== 'idle'" class="status-overlay">
        <div class="status-content" :class="statusClass">
          <el-icon v-if="status === 'capturing'" class="is-loading">
            <Loading />
          </el-icon>
          <el-icon v-else-if="status === 'success'">
            <CircleCheck />
          </el-icon>
          <el-icon v-else-if="status === 'failed'">
            <CircleClose />
          </el-icon>
          <span>{{ statusText }}</span>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="camera-controls">
      <el-button v-if="!isStreaming" type="primary" @click="startCamera">
        <el-icon><VideoCamera /></el-icon>
        开启摄像头
      </el-button>

      <template v-else>
        <el-button @click="capturePhoto" :loading="status === 'capturing'" type="primary">
          <el-icon><Camera /></el-icon>
          拍照识别
        </el-button>

        <el-button @click="stopCamera">
          <el-icon><VideoPause /></el-icon>
          关闭
        </el-button>
      </template>
    </div>

    <!-- 捕获的照片预览 -->
    <div v-if="capturedImage" class="captured-preview">
      <img :src="capturedImage" alt="captured face" />
      <div class="preview-actions">
        <el-button size="small" @click="retakePhoto">重拍</el-button>
        <el-button size="small" type="primary" @click="confirmPhoto">确认</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, CircleCheck, CircleClose, VideoCamera, Camera, VideoPause } from '@element-plus/icons-vue'
import type { FaceRecognitionStatus, CameraConfig } from '@/types/face'

interface Props {
  showGuide?: boolean
  mirrored?: boolean
  config?: Partial<CameraConfig>
}

const props = withDefaults(defineProps<Props>(), {
  showGuide: true,
  mirrored: true,
  config: () => ({})
})

const emit = defineEmits<{
  (e: 'capture', imageBase64: string): void
  (e: 'error', message: string): void
}>()

// 默认配置
const defaultConfig: CameraConfig = {
  width: 640,
  height: 480,
  facingMode: 'user',
  quality: 0.8
}

const mergedConfig = { ...defaultConfig, ...props.config }

// refs
const videoRef = ref<HTMLVideoElement>()
const canvasRef = ref<HTMLCanvasElement>()

// 状态
const isStreaming = ref(false)
const status = ref<FaceRecognitionStatus>('idle')
const capturedImage = ref<string>()

// 计算属性
const statusText = computed(() => {
  const texts: Record<FaceRecognitionStatus, string> = {
    idle: '',
    capturing: '正在拍照...',
    recognizing: '正在识别...',
    success: '识别成功',
    failed: '识别失败'
  }
  return texts[status.value]
})

const statusClass = computed(() => {
  const classes: Record<FaceRecognitionStatus, string> = {
    idle: '',
    capturing: 'is-capturing',
    recognizing: 'is-capturing',
    success: 'is-success',
    failed: 'is-failed'
  }
  return classes[status.value]
})

let mediaStream: MediaStream | null = null

// 开启摄像头
const startCamera = async () => {
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: mergedConfig.width },
        height: { ideal: mergedConfig.height },
        facingMode: mergedConfig.facingMode
      },
      audio: false
    })

    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
      isStreaming.value = true
      status.value = 'idle'
    }
  } catch (error: any) {
    console.error('摄像头启动失败:', error)
    let message = '无法访问摄像头'
    if (error.name === 'NotAllowedError') {
      message = '请允许访问摄像头权限'
    } else if (error.name === 'NotFoundError') {
      message = '未找到摄像头设备'
    }
    ElMessage.error(message)
    emit('error', message)
  }
}

// 关闭摄像头
const stopCamera = () => {
  if (mediaStream) {
    mediaStream.getTracks().forEach(track => track.stop())
    mediaStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
  isStreaming.value = false
  status.value = 'idle'
  capturedImage.value = undefined
}

// 拍照
const capturePhoto = () => {
  if (!videoRef.value || !canvasRef.value) return

  status.value = 'capturing'

  const video = videoRef.value
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')

  if (!ctx) return

  canvas.width = video.videoWidth
  canvas.height = video.videoHeight

  // 镜像处理
  if (props.mirrored) {
    ctx.translate(canvas.width, 0)
    ctx.scale(-1, 1)
  }

  ctx.drawImage(video, 0, 0)

  // 转为base64
  capturedImage.value = canvas.toDataURL('image/jpeg', mergedConfig.quality)
  const base64 = capturedImage.value.split(',')[1]

  status.value = 'idle'

  // 直接触发capture事件
  emit('capture', base64)
}

// 重拍
const retakePhoto = () => {
  capturedImage.value = undefined
}

// 确认照片
const confirmPhoto = () => {
  if (capturedImage.value) {
    const base64 = capturedImage.value.split(',')[1]
    emit('capture', base64)
  }
}

const onVideoLoaded = () => {
  // 视频加载完成
}

onUnmounted(() => {
  stopCamera()
})

// 暴露方法
defineExpose({
  startCamera,
  stopCamera,
  capturePhoto,
  setStatus: (newStatus: FaceRecognitionStatus) => {
    status.value = newStatus
  }
})
</script>

<style lang="scss" scoped>
.face-camera {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.camera-preview {
  position: relative;
  width: 100%;
  max-width: 480px;
  aspect-ratio: 4/3;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.video-element {
  width: 100%;
  height: 100%;
  object-fit: cover;

  &.mirrored {
    transform: scaleX(-1);
  }
}

.capture-canvas {
  display: none;
}

.face-guide {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.face-oval {
  width: 200px;
  height: 260px;
  border: 3px dashed rgba(255, 255, 255, 0.6);
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% {
    border-color: rgba(255, 255, 255, 0.6);
  }
  50% {
    border-color: rgba(64, 158, 255, 0.8);
  }
}

.status-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

.status-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #fff;
  font-size: 16px;

  .el-icon {
    font-size: 48px;
  }

  &.is-capturing {
    color: #409eff;
  }

  &.is-success {
    color: #67c23a;
  }

  &.is-failed {
    color: #f56c6c;
  }
}

.camera-controls {
  display: flex;
  gap: 12px;
}

.captured-preview {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;

  img {
    max-width: 100%;
    max-height: calc(100% - 60px);
    object-fit: contain;
  }

  .preview-actions {
    display: flex;
    gap: 12px;
    margin-top: 12px;
  }
}
</style>
