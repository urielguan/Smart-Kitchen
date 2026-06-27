import { ref, watch, onUnmounted, type Ref } from 'vue'
import Hls from 'hls.js'

export function useHlsVideo(videoRef: Ref<HTMLVideoElement | null>, src: Ref<string>) {
  const loading = ref(true)
  const error = ref<string | null>(null)

  let hls: Hls | null = null
  let retryCount = 0
  const MAX_RETRIES = 5

  const destroy = () => {
    if (hls) {
      hls.destroy()
      hls = null
    }
    loading.value = true
    error.value = null
    retryCount = 0
  }

  const init = () => {
    destroy()
    if (!videoRef.value || !src.value) return

    loading.value = true
    error.value = null

    if (Hls.isSupported()) {
      hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        maxBufferLength: 10,
        maxMaxBufferLength: 20,
        liveSyncDurationCount: 3,
        liveMaxLatencyDurationCount: 6,
      })

      hls.loadSource(src.value)
      hls.attachMedia(videoRef.value)

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        loading.value = false
        retryCount = 0
      })

      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              if (retryCount < MAX_RETRIES) {
                retryCount++
                hls?.startLoad()
              } else {
                error.value = '网络错误，无法加载视频流'
                loading.value = false
              }
              break
            case Hls.ErrorTypes.MEDIA_ERROR:
              hls?.recoverMediaError()
              break
            default:
              error.value = '视频播放失败'
              loading.value = false
              destroy()
              break
          }
        }
      })
    } else if (videoRef.value.canPlayType('application/vnd.apple.mpegurl')) {
      // Safari native HLS support
      videoRef.value.src = src.value
      videoRef.value.addEventListener('loadedmetadata', () => { loading.value = false })
      videoRef.value.addEventListener('error', () => {
        error.value = '视频播放失败'
        loading.value = false
      })
    } else {
      error.value = '浏览器不支持 HLS 播放'
      loading.value = false
    }
  }

  watch(src, () => init())
  onUnmounted(() => destroy())

  return { loading, error, init, destroy }
}
