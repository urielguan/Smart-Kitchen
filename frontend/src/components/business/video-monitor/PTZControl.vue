<script setup lang="ts">
import { ref } from 'vue'
import { ZoomIn, ZoomOut, CircleClose } from '@element-plus/icons-vue'
import type { PTZDirection } from '@/types/video-monitor'

interface Props {
  /** 是否显示 */
  visible?: boolean
  /** 是否禁用 */
  disabled?: boolean
  /** 设备是否支持云台 */
  ptzSupport?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  visible: true,
  disabled: false,
  ptzSupport: true
})

const emit = defineEmits<{
  (e: 'control', direction: PTZDirection, speed: number): void
  (e: 'close'): void
}>()

// 控制速度
const speed = ref(5)

// 当前按下的方向
const activeDirection = ref<PTZDirection | null>(null)

// 发送控制指令
const sendControl = (direction: PTZDirection) => {
  if (props.disabled || !props.ptzSupport) return

  activeDirection.value = direction
  emit('control', direction, speed.value)
}

// 停止控制
const stopControl = () => {
  if (props.disabled || !props.ptzSupport) return

  activeDirection.value = null
  emit('control', 'stop', speed.value)
}

// 关闭面板
const closePanel = () => {
  emit('close')
}

// 判断按钮是否激活
const isActive = (direction: PTZDirection): boolean => {
  return activeDirection.value === direction
}

// 获取按钮样式
const getButtonClass = (direction: PTZDirection): Record<string, boolean> => ({
  'is-active': isActive(direction),
  'is-disabled': props.disabled || !props.ptzSupport
})
</script>

<template>
  <Transition name="fade">
    <div v-if="visible" class="ptz-control-panel">
      <!-- 标题栏 -->
      <div class="panel-header">
        <span class="title">云台控制</span>
        <el-button
          type="info"
          :icon="CircleClose"
          circle
          size="small"
          @click="closePanel"
        />
      </div>

      <!-- 不支持提示 -->
      <div v-if="!ptzSupport" class="not-support-tip">
        该设备不支持云台控制
      </div>

      <template v-else>
        <!-- 方向控制 -->
        <div class="direction-control">
          <!-- 上 -->
          <div class="direction-row">
            <el-button
              :type="isActive('up') ? 'primary' : 'default'"
              circle
              :disabled="disabled || !ptzSupport"
              :class="getButtonClass('up')"
              @mousedown="sendControl('up')"
              @mouseup="stopControl"
              @mouseleave="stopControl"
            >
              <span class="direction-icon">↑</span>
            </el-button>
          </div>

          <!-- 左 中 右 -->
          <div class="direction-row">
            <el-button
              :type="isActive('left') ? 'primary' : 'default'"
              circle
              :disabled="disabled || !ptzSupport"
              :class="getButtonClass('left')"
              @mousedown="sendControl('left')"
              @mouseup="stopControl"
              @mouseleave="stopControl"
            >
              <span class="direction-icon">←</span>
            </el-button>
            <div class="center-indicator">
              <span class="center-dot"></span>
            </div>
            <el-button
              :type="isActive('right') ? 'primary' : 'default'"
              circle
              :disabled="disabled || !ptzSupport"
              :class="getButtonClass('right')"
              @mousedown="sendControl('right')"
              @mouseup="stopControl"
              @mouseleave="stopControl"
            >
              <span class="direction-icon">→</span>
            </el-button>
          </div>

          <!-- 下 -->
          <div class="direction-row">
            <el-button
              :type="isActive('down') ? 'primary' : 'default'"
              circle
              :disabled="disabled || !ptzSupport"
              :class="getButtonClass('down')"
              @mousedown="sendControl('down')"
              @mouseup="stopControl"
              @mouseleave="stopControl"
            >
              <span class="direction-icon">↓</span>
            </el-button>
          </div>
        </div>

        <!-- 变焦控制 -->
        <div class="zoom-control">
          <el-button
            :type="isActive('zoom_in') ? 'primary' : 'default'"
            :disabled="disabled"
            :class="getButtonClass('zoom_in')"
            @mousedown="sendControl('zoom_in')"
            @mouseup="stopControl"
            @mouseleave="stopControl"
          >
            <el-icon><ZoomIn /></el-icon>
            <span>放大</span>
          </el-button>
          <el-button
            :type="isActive('zoom_out') ? 'primary' : 'default'"
            :disabled="disabled"
            :class="getButtonClass('zoom_out')"
            @mousedown="sendControl('zoom_out')"
            @mouseup="stopControl"
            @mouseleave="stopControl"
          >
            <el-icon><ZoomOut /></el-icon>
            <span>缩小</span>
          </el-button>
        </div>

        <!-- 速度控制 -->
        <div class="speed-control">
          <span class="label">控制速度</span>
          <el-slider
            v-model="speed"
            :min="1"
            :max="10"
            :step="1"
            :disabled="disabled"
            show-stops
          />
          <span class="value">{{ speed }}</span>
        </div>
      </template>
    </div>
  </Transition>
</template>

<style lang="scss" scoped>
.ptz-control-panel {
  position: absolute;
  bottom: 80px;
  right: 16px;
  width: 200px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.85);
  border-radius: 8px;
  color: #fff;
  z-index: 10;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);

  .title {
    font-size: 14px;
    font-weight: 500;
  }
}

.not-support-tip {
  text-align: center;
  padding: 20px 0;
  color: rgba(255, 255, 255, 0.6);
  font-size: 13px;
}

.direction-control {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;

  .direction-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .direction-icon {
    font-size: 18px;
    font-weight: bold;
  }

  .center-indicator {
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;

    .center-dot {
      width: 8px;
      height: 8px;
      background: rgba(255, 255, 255, 0.5);
      border-radius: 50%;
    }
  }

  :deep(.el-button.is-circle) {
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
    color: #fff;

    &:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.2);
      border-color: rgba(255, 255, 255, 0.3);
    }

    &.is-active {
      background: $primary-color;
      border-color: $primary-color;
    }

    &.is-disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }
}

.zoom-control {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;

  :deep(.el-button) {
    flex: 1;
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
    color: #fff;

    &:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.2);
    }

    &.is-active {
      background: $primary-color;
      border-color: $primary-color;
    }

    .el-icon {
      margin-right: 4px;
    }
  }
}

.speed-control {
  display: flex;
  align-items: center;
  gap: 8px;

  .label {
    font-size: 12px;
    white-space: nowrap;
  }

  .value {
    width: 20px;
    text-align: center;
    font-size: 12px;
    font-weight: 500;
  }

  :deep(.el-slider) {
    flex: 1;

    .el-slider__runway {
      background: rgba(255, 255, 255, 0.2);
    }

    .el-slider__bar {
      background: $primary-color;
    }

    .el-slider__button {
      border-color: $primary-color;
    }
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}
</style>
