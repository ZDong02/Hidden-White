<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Field, Select, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@/components/business'
import { coffeeApi, type CoffeeMemberRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

const list = useListPage<CoffeeMemberRow, { keyword: string }>({
  query: { keyword: '' },
  fetch: params => coffeeApi.members.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'displayName', title: '昵称', minWidth: 130 },
  { key: 'phone', title: '手机号', width: 130 },
  { key: 'level', title: '等级', width: 110 },
  { key: 'points', title: '积分', width: 90 },
  { key: 'coupons', title: '优惠券', width: 90 },
  { key: 'growthValue', title: '成长值', width: 90 },
  { key: 'birthday', title: '生日', width: 90 },
  { key: 'cardNumber', title: '会员卡号', width: 140 },
  { key: 'actions', title: '操作', width: 90, className: 'col-actions' },
]

interface MemberForm {
  displayName: string
  level: string
  points: number | null
  coupons: number | null
  growthValue: number | null
  /** 生日月/日：null 表示未填写；只采集月日，不采集出生年份 */
  birthdayMonth: number | null
  birthdayDay: number | null
}
const dialog = ref(false)
const saving = ref(false)
const editingId = ref('')
const form = reactive<MemberForm>({
  displayName: '', level: '', points: 0, coupons: 0, growthValue: 0,
  birthdayMonth: null, birthdayDay: null,
})
const errors = ref<Partial<Record<keyof MemberForm, string>>>({})

const monthOptions = Array.from({ length: 12 }, (_, index) => ({ label: `${index + 1} 月`, value: index + 1 }))
/** 2 月给 29 天：闰年出生的人不该在管理端就被挡掉，平年由服务端归一 */
const dayOptions = computed(() => {
  const month = form.birthdayMonth
  if (!month) return []
  const total = month === 2 ? 29 : [4, 6, 9, 11].includes(month) ? 30 : 31
  return Array.from({ length: total }, (_, index) => ({ label: `${index + 1} 日`, value: index + 1 }))
})

function onMonthChange(value: string | number | null | undefined) {
  const month = typeof value === 'number' ? value : null
  form.birthdayMonth = month
  const total = dayOptions.value.length
  if (!month) {
    form.birthdayDay = null
  } else if (form.birthdayDay && form.birthdayDay > total) {
    // 1 月 31 日切到 2 月：日不能被悬空
    form.birthdayDay = total
  }
}

function openEdit(row: CoffeeMemberRow) {
  editingId.value = row.id
  Object.assign(form, {
    displayName: row.displayName,
    level: row.level,
    points: row.points,
    coupons: row.coupons,
    growthValue: row.growthValue,
    birthdayMonth: row.birthdayMonth ?? null,
    birthdayDay: row.birthdayDay ?? null,
  })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    displayName: [required('请输入会员昵称')],
    level: [required('请输入会员等级')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await coffeeApi.members.update(editingId.value, {
      displayName: form.displayName.trim(),
      level: form.level.trim(),
      points: Number(form.points) || 0,
      coupons: Number(form.coupons) || 0,
      growthValue: Number(form.growthValue) || 0,
      birthdayMonth: form.birthdayMonth,
      birthdayDay: form.birthdayDay,
    })
    pushToast('会员资料已更新')
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '会员保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: CoffeeMemberRow) {
  if (row.id === 'guest') {
    pushToast('游客账号是点单端系统内置账号，不能删除', 'danger')
    return
  }
  const ok = await confirm({
    title: '删除会员',
    message: `确认删除会员「${row.displayName}」吗？存在订单记录的会员无法删除。`,
    confirmText: '删除', danger: true,
  })
  if (!ok) return
  try {
    await coffeeApi.members.remove(row.id)
    pushToast('会员已删除')
    await list.load()
  } catch (error) {
    toastError(error, '会员删除失败')
  }
}
</script>

<template>
  <ListPage
    title="会员管理"
    desc="维护点单端会员的等级、积分与优惠券；账号、手机号与密码由点单端注册登录流程管理。"
    table-title="会员列表"
    :list="list" :columns="columns" :min-width="900"
  >
    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="昵称 / 手机号 / 卡号 / 会员 ID" clearable @enter="list.search()" />
      </Field>
    </template>

    <template #cell-displayName="{ row }">
      <span class="cell-title">{{ row.displayName }}</span>
      <span v-if="row.id === 'guest'" class="muted" style="display: block; font-size: 12px">系统内置游客</span>
    </template>
    <template #cell-phone="{ row }">{{ row.phone || '—' }}</template>
    <template #cell-level="{ row }"><Tag tone="info">{{ row.level }}</Tag></template>
    <template #cell-points="{ row }"><span class="mono tnum">{{ row.points }}</span></template>
    <template #cell-coupons="{ row }"><span class="mono tnum">{{ row.coupons }}</span></template>
    <template #cell-growthValue="{ row }"><span class="mono tnum">{{ row.growthValue }}</span></template>
    <template #cell-birthday="{ row }">
      <span v-if="row.birthdayMonth" class="mono">{{ row.birthdayMonth }}-{{ String(row.birthdayDay).padStart(2, '0') }}</span>
      <span v-else class="muted">未填写</span>
    </template>
    <template #cell-cardNumber="{ row }"><span class="mono muted">{{ row.cardNumber }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="coffee:member:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="coffee:member:remove" icon-only icon="trash" label="删除" danger :disabled="row.id === 'guest'" @click="remove(row)" />
      </div>
    </template>

    <FormDialog :open="dialog" title="编辑会员" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="昵称" required :error="errors.displayName">
        <TextInput v-model="form.displayName" :invalid="!!errors.displayName" />
      </Field>
      <Field label="等级" required :error="errors.level">
        <TextInput v-model="form.level" placeholder="普通会员 / 金卡会员" :invalid="!!errors.level" />
      </Field>
      <Field label="积分">
        <TextInput v-model="form.points" type="number" min="0" />
      </Field>
      <Field label="优惠券">
        <TextInput v-model="form.coupons" type="number" min="0" />
      </Field>
      <Field label="成长值">
        <TextInput v-model="form.growthValue" type="number" min="0" />
      </Field>
      <Field label="生日" hint="只记月/日，不采集出生年份；会员本人一年可改一次，管理端修改同样刷新计时">
        <div class="birthday-row">
          <Select
            :model-value="form.birthdayMonth"
            :options="monthOptions"
            placeholder="月份"
            clearable
            @update:model-value="onMonthChange"
          />
          <Select
            v-model="form.birthdayDay"
            :options="dayOptions"
            :disabled="!form.birthdayMonth"
            placeholder="日期"
            clearable
          />
        </div>
      </Field>
    </FormDialog>
  </ListPage>
</template>

<style scoped>
/* 月 / 日两个下拉并排：单列下拉会让生日这一项占掉整行高度 */
.birthday-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
</style>
