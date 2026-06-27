import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadSampleImage } from '@/api/modules/sample'
import type { Ref } from 'vue'

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'video/mp4', 'application/pdf']
const MAX_SIZE_MB = 50

export function useSampleUpload(targetArray: Ref<string[]>) {
  const uploading = ref(false)

  const handleImageUpload = async (file: File): Promise<boolean> => {
    if (!ALLOWED_TYPES.includes(file.type)) {
      ElMessage.error('支持 JPG、PNG、GIF、WebP、MP4、PDF 格式')
      return false
    }
    if (file.size / 1024 / 1024 >= MAX_SIZE_MB) {
      ElMessage.error('文件大小不能超过 50MB')
      return false
    }
    try {
      uploading.value = true
      const res = await uploadSampleImage(file)
      if (res.code === 'SUCCESS' && res.data) {
        targetArray.value.push(res.data.imageUrl)
      }
    } catch (error: any) {
      ElMessage.error(error.message || '图片上传失败')
    } finally {
      uploading.value = false
    }
    return false
  }

  const removeImage = (index: number) => {
    targetArray.value.splice(index, 1)
  }

  return {
    uploading,
    handleImageUpload,
    removeImage
  }
}
