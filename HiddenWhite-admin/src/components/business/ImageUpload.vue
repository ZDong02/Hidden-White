<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@/components/Icon.vue'
import { coffeeApi, type CoffeeImageSlot, type CoffeeImageTarget, type CoffeeImageUpload } from '@/api'
import { pushToast, toastError } from '@/composables/toast'

/**
 * 商品图片上传控件。
 *
 * <p>替代原先手填路径的输入框：本地选图 → 预校验 → 上传 → 写入返回的地址。
 * 目标宽高由后端按用途（列表图 / 详情图）独立配置，前端只做展示与预校验，
 * 实际裁剪与压缩全部在服务端一次完成，避免多次编码损伤画质。
 */
const props = withDefaults(defineProps<{
  /** 已上传的图片地址；空串表示未设置 */
  modelValue?: string
  /** 图片用途：list 列表图 / detail 详情图 */
  slot: CoffeeImageSlot
  /** 目标规格；未传时由组件自行拉取 */
  target?: CoffeeImageTarget
  /** 已从服务端读取的规格（避免重复请求） */
  maxBytes?: number
  disabled?: boolean
  /**
   * 自定义上传通道。默认走商品图片接口（/coffee/images/upload）。
   *
   * 积分运营复用同一个控件，但上传接口挂在自己的权限码（redeem:product:add）下：
   * 让「能维护兑换品」的账号不需要额外具备「商品管理」权限才能传图。
   */
  uploader?: (file: File, slot: CoffeeImageSlot) => Promise<CoffeeImageUpload>
}>(), {
  modelValue: '',
  target: undefined,
  maxBytes: undefined,
  disabled: false,
  uploader: undefined,
})

const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

/** 允许上传的格式，与服务端白名单保持一致。 */
const ACCEPT = 'image/jpeg,image/png,image/webp'

const uploading = ref(false)
const dragging = ref(false)
const errorText = ref('')
const infoText = ref('')

/** 文件输入 id：页面上有列表图 / 详情图两个实例，id 必须唯一，
 *  否则点击其中一个的 label 会打开另一个的文件选择框。 */
const fileInputId = `image-file-${Math.random().toString(36).slice(2, 9)}`

/** 当前目标规格：优先用外部传入（来自服务端 /coffee/images/spec），
 *  否则退回与服务端配置一致的默认值——规格拉取失败时也能给出正确提示。 */
const effectiveTarget = computed<CoffeeImageTarget>(() => {
  if (props.target) return props.target
  return props.slot === 'detail'
    ? { width: 1200, height: 636, mode: 'cover' }
    : { width: 284, height: 240, mode: 'cover' }
})

/** 展示用的图片地址：后端返回的是 /uploads/... 相对路径，管理端同源可直接用 */
const previewUrl = computed(() => props.modelValue || '')

/**
 * 拖拽进入时高亮
 *
 * <p>文件选择框由 <label for> 原生触发，不用 JS 调 input.click()：
 * 后者仅在用户手势事件中有效，键盘操作与程序化触发会被浏览器拒绝
 * （File chooser dialog can only be shown with a user activation）。
 */
function onDragOver(event: DragEvent) {
  if (props.disabled || uploading.value) return
  event.preventDefault()
  dragging.value = true
}

/** 拖拽离开时取消高亮 */
function onDragLeave() {
  dragging.value = false
}

/** 拖拽放下：取第一个文件 */
function onDrop(event: DragEvent) {
  dragging.value = false
  if (props.disabled || uploading.value) return
  event.preventDefault()
  const file = event.dataTransfer?.files?.[0]
  if (file) void handleFile(file)
}

/** 选择文件后进入校验与上传流程 */
async function onPick(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  // 重置 input，保证连续选择同一文件也能再次触发 change
  target.value = ''
  if (file) await handleFile(file)
}

/** 校验并上传 */
async function handleFile(file: File) {
  errorText.value = ''
  infoText.value = ''

  const formatError = checkFormat(file)
  if (formatError) {
    errorText.value = formatError
    return
  }
  const sizeError = checkSize(file)
  if (sizeError) {
    errorText.value = sizeError
    return
  }

  uploading.value = true
  try {
    const upload = props.uploader ?? coffeeApi.images.upload
    const result = await upload(file, props.slot)
    emit('update:modelValue', result.url)
    infoText.value = describeResult(result.width, result.height, result.ratioChanged)
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '上传失败，请重试'
    toastError(error, '图片上传失败')
  } finally {
    uploading.value = false
  }
}

/** 格式校验：只看扩展名与 MIME，服务端会再按真实字节复核一次 */
function checkFormat(file: File) {
  const byName = /\.(jpe?g|png|webp)$/i.test(file.name)
  const byType = ACCEPT.split(',').includes(file.type)
  if (!byName && !byType) {
    return '仅支持 JPG / PNG / WebP 格式的图片'
  }
  return ''
}

/** 大小校验 */
function checkSize(file: File) {
  const limit = props.maxBytes
  if (limit && file.size > limit) {
    return `图片大小超过 ${formatSize(limit)}，请压缩后再上传`
  }
  return ''
}

/** 处理结果说明 */
function describeResult(width: number, height: number, ratioChanged: boolean) {
  const size = `${width} × ${height}`
  return ratioChanged
    ? `已按目标比例裁剪为 ${size}`
    : `已处理为 ${size}`
}

/** 字节数转可读文案 */
function formatSize(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`
  return `${Math.round(bytes / 1024)}KB`
}

/** 清除已上传的图片 */
function clear() {
  if (props.disabled || uploading.value) return
  emit('update:modelValue', '')
  errorText.value = ''
  infoText.value = ''
  pushToast('已清除图片')
}
</script>

<template>
  <div class="image-upload">
    <label
      class="image-drop"
      :class="{ 'is-dragging': dragging, 'is-disabled': disabled, 'has-image': !!previewUrl }"
      :for="fileInputId"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <template v-if="previewUrl">
        <img class="image-preview" :src="previewUrl" alt="商品图片预览" />
      </template>
      <template v-else-if="uploading">
        <span class="image-placeholder"><Icon name="refresh" :size="20" /></span>
        <span class="image-hint">上传中…</span>
      </template>
      <template v-else>
        <span class="image-placeholder"><Icon name="upload" :size="20" /></span>
        <span class="image-hint">点击选择或拖拽图片到此处</span>
      </template>
    </label>

    <input
      :id="fileInputId"
      class="image-file"
      type="file"
      :accept="ACCEPT"
      :disabled="disabled"
      @change="onPick"
    />

    <div class="image-meta">
      <span class="field-hint">
        目标 {{ effectiveTarget.width }} × {{ effectiveTarget.height }}
        <span class="muted">（{{ effectiveTarget.mode === 'cover' ? '居中裁剪' : '等比缩放' }}）</span>
      </span>
      <div class="image-actions">
        <button v-if="previewUrl" type="button" class="image-clear" :disabled="disabled || uploading" @click="clear">
          清除
        </button>
      </div>
    </div>

    <p v-if="errorText" class="field-error" role="alert">{{ errorText }}</p>
    <p v-else-if="infoText" class="field-hint">{{ infoText }}</p>
  </div>
</template>
