<template>
  <Dialog
    :visible="visible"
    :title="dialog ? dialog.title : ''"
    :content="dialog ? dialog.content : ''"
    :confirm-text="dialog ? dialog.confirmText : '确定'"
    :cancel-text="dialog ? dialog.cancelText : '取消'"
    :show-cancel="dialog ? dialog.showCancel : true"
    :danger="Boolean(dialog && dialog.danger)"
    :editable="Boolean(dialog && dialog.type === 'prompt')"
    :placeholder="dialog ? dialog.placeholder : ''"
    :maxlength="dialog && dialog.maxlength ? dialog.maxlength : 100"
    :model-value="dialog && dialog.value ? dialog.value : ''"
    :mask-closable="Boolean(dialog && dialog.maskClosable)"
    @confirm="handleConfirm"
    @cancel="handleCancel"
  />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import Dialog from './Dialog.vue'
import {
  pendingDialog,
  registerDialogHost,
  settleDialog,
  unregisterDialogHost
} from '../../store/dialog'

/**
 * 对话框宿主：把 store/dialog 里的待办弹窗渲染成 ui/Dialog。
 *
 * 每个可能弹窗的页面在**页面层**放一个即可（不要放进 swiper-item / 滚动容器）。
 * 全端只有这里调用 settleDialog，保证「谁渲染谁结清」，调用方只管 await。
 */
const dialog = computed(() => pendingDialog.value)
const visible = computed(() => Boolean(pendingDialog.value))

onMounted(registerDialogHost)
onBeforeUnmount(unregisterDialogHost)

const handleConfirm = (value) => {
  settleDialog(true, typeof value === 'string' ? value : null)
}

const handleCancel = () => {
  settleDialog(false)
}
</script>
