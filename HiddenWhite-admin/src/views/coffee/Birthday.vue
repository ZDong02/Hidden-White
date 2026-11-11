<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Field, Select, Switch, Tag, TextInput, type Column } from '@/components/ui'
import { ListPage, PermissionButton } from '@/components/business'
import { birthdayApi, type BirthdayConfig, type BirthdayGiftRow } from '@/api'
import { useListPage } from '@/composables/list'
import { hasPermission } from '@/directives/permission'
import { pushToast, toastError } from '@/composables/toast'

/**
 * 生日礼遇运营页。
 *
 * 左栏是配置（发放物 / 金额 / 窗口 / 文案），右侧是领取记录。
 * 配置改动会立刻影响点单端——所有字段都必须经过服务端校验，
 * 前端只做「明显填错」的第一道拦截（负数、空标题），
 * 真正的规则（窗口模式、金额上限）以后端为准。
 */
const currentYear = new Date().getFullYear()

const config = ref<BirthdayConfig | null>(null)
const form = reactive({
  enabled: true,
  pointsAmount: 200,
  windowMode: 'MONTH' as 'MONTH' | 'AROUND',
  windowDays: 3,
  title: '生日礼遇',
  subtitle: '生日专属惊喜',
  description: '',
})
const saving = ref(false)
const errors = ref<Record<string, string>>({})

const canEdit = computed(() => hasPermission('coffee:birthday:edit'))

const windowOptions = [
  { label: '生日当月整月', value: 'MONTH' },
  { label: '生日前后 N 天', value: 'AROUND' },
]

async function loadConfig() {
  try {
    const data = await birthdayApi.config()
    config.value = data
    Object.assign(form, {
      enabled: data.enabled,
      pointsAmount: data.pointsAmount,
      windowMode: data.windowMode,
      windowDays: data.windowDays,
      title: data.title,
      subtitle: data.subtitle,
      description: data.description,
    })
  } catch (error) {
    toastError(error, '生日礼遇配置加载失败')
  }
}

async function submit() {
  errors.value = {}
  if (!form.title.trim()) errors.value.title = '请输入标题'
  if (!Number.isFinite(form.pointsAmount) || form.pointsAmount < 1) errors.value.pointsAmount = '发放积分必须大于 0'
  if (form.windowMode === 'AROUND' && (form.windowDays < 0 || form.windowDays > 30)) {
    errors.value.windowDays = '前后天数需在 0-30 之间'
  }
  if (Object.keys(errors.value).length) return

  saving.value = true
  try {
    await birthdayApi.saveConfig({
      enabled: form.enabled,
      giftType: 'POINTS',
      pointsAmount: Number(form.pointsAmount),
      windowMode: form.windowMode,
      windowDays: Number(form.windowDays),
      title: form.title.trim(),
      subtitle: form.subtitle.trim(),
      description: form.description.trim(),
    })
    pushToast('生日礼遇配置已保存')
    await loadConfig()
  } catch (error) {
    toastError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---------------- 领取记录 ---------------- */

const list = useListPage<BirthdayGiftRow, { year: number | string; keyword: string }>({
  query: { year: currentYear, keyword: '' },
  fetch: params => birthdayApi.gifts(params) as Promise<any>,
  pageSize: 10,
})

const columns: Column[] = [
  { key: 'displayName', title: '会员', minWidth: 140 },
  { key: 'phone', title: '手机号', width: 130 },
  { key: 'amount', title: '发放积分', width: 100 },
  { key: 'giftYear', title: '归属年份', width: 100 },
  { key: 'pointsLogId', title: '积分流水', width: 100 },
  { key: 'createdAt', title: '领取时间', width: 150 },
]

const yearOptions = Array.from({ length: 4 }, (_, index) => {
  const year = currentYear - index
  return { label: `${year} 年`, value: year }
})

onMounted(async () => {
  await loadConfig()
  await list.load()
})
</script>

<template>
  <ListPage
    title="生日礼遇"
    desc="会员生日当月的专属礼遇。发放物与窗口都在这里配置，改完立即对点单端生效。"
    table-title="领取记录"
    :list="list"
    :columns="columns"
    :min-width="820"
  >
    <template #side>
      <section class="bd-config">
        <h3 class="bd-config-title">礼遇配置</h3>
        <p class="bd-config-note">
          生日只采集月/日，不采集出生年份；每位会员每个自然年只能领取一次，
          领取记录由数据库唯一键兜住，不依赖前端防重。
        </p>

        <!-- 侧栏只有 256px 宽：读数用「标签 + 数字」的单行结构，三列小卡在这里会挤成竖排字 -->
        <dl class="bd-stats">
          <div class="bd-stat">
            <dt>已填生日会员</dt>
            <dd class="mono">{{ config?.birthdayMemberCount ?? 0 }}</dd>
          </div>
          <div class="bd-stat">
            <dt>年度已领人次</dt>
            <dd class="mono">{{ config?.giftCount ?? 0 }}</dd>
          </div>
          <div class="bd-stat">
            <dt>年度发放积分</dt>
            <dd class="mono">{{ config?.pointsTotal ?? 0 }}</dd>
          </div>
        </dl>

        <Field label="是否开放">
          <Switch v-model="form.enabled" :label="form.enabled ? '点单端可领取' : '已关闭，点单端显示筹备中'" />
        </Field>

        <Field label="发放物" hint="赠饮券需要「0 元兑换单 + 核销」联动，将在下一版本开放">
          <Select
            :model-value="'POINTS'"
            :options="[{ label: '积分（当前唯一可用）', value: 'POINTS' }, { label: '赠饮券（未开放）', value: 'DRINK', disabled: true }]"
            disabled
          />
        </Field>

        <Field label="发放积分" required :error="errors.pointsAmount" hint="100 积分约等于 3 元，200 积分约 6 元">
          <TextInput v-model="form.pointsAmount" type="number" :invalid="!!errors.pointsAmount" />
        </Field>

        <Field label="领取窗口">
          <Select v-model="form.windowMode" :options="windowOptions" />
        </Field>

        <Field v-if="form.windowMode === 'AROUND'" label="生日前后天数" :error="errors.windowDays">
          <TextInput v-model="form.windowDays" type="number" :invalid="!!errors.windowDays" />
        </Field>

        <Field label="标题" required :error="errors.title" hint="点单端卡片与弹层的标题">
          <TextInput v-model="form.title" :maxlength="40" :invalid="!!errors.title" />
        </Field>

        <Field label="副标题" hint="留在权益卡上的第二行，建议 10 个字以内">
          <TextInput v-model="form.subtitle" :maxlength="60" />
        </Field>

        <Field label="领取说明" hint="展示在领取弹层的深色卡里">
          <TextInput v-model="form.description" :maxlength="200" />
        </Field>

        <PermissionButton
          perm="coffee:birthday:edit"
          variant="primary"
          :disabled="saving || !canEdit"
          style="width: 100%; margin-top: 8px;"
          @click="submit"
        >
          {{ saving ? '保存中…' : '保存配置' }}
        </PermissionButton>
      </section>
    </template>

    <template #search>
      <Field label="年份">
        <Select v-model="list.query.year" :options="yearOptions" />
      </Field>
      <Field label="关键词" wide>
        <TextInput
          v-model="list.query.keyword"
          icon="search"
          placeholder="会员昵称 / 手机号 / 会员 ID"
          clearable
          @enter="list.search()"
        />
      </Field>
    </template>

    <template #cell-displayName="{ row }">
      <span class="bd-member">{{ row.displayName }}</span>
      <span class="bd-member-id mono">{{ row.memberId.slice(0, 8) }}</span>
    </template>

    <template #cell-phone="{ row }">{{ row.phone || '—' }}</template>

    <template #cell-amount="{ row }">
      <span class="mono tnum">+{{ row.amount }}</span>
    </template>

    <template #cell-giftYear="{ row }">
      <Tag tone="neutral">{{ row.giftYear }}</Tag>
    </template>

    <template #cell-pointsLogId="{ row }">
      <span class="mono muted">{{ row.pointsLogId ?? '—' }}</span>
    </template>

    <template #cell-createdAt="{ row }">
      <span class="mono muted">{{ row.createdAt }}</span>
    </template>
  </ListPage>
</template>

<style scoped>
.bd-config { padding: 18px 18px 8px; }
.bd-config-title { font-size: 15px; font-weight: var(--fw-medium); }
.bd-config-note {
  margin: 8px 0 16px;
  color: var(--ink-soft, #6f675c);
  font-size: 12px;
  line-height: 1.6;
}
.bd-stats {
  display: grid;
  gap: 6px;
  margin: 0 0 18px;
}
.bd-stat {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--line, #ece7db);
  border-radius: 8px;
  background: var(--card-sub, #faf8f3);
}
.bd-stat dt { font-size: 12px; color: var(--ink-soft, #6f675c); }
.bd-stat dd { margin: 0; font-size: 15px; color: var(--ink, #191710); }
.bd-member { display: block; }
.bd-member-id { font-size: 11px; color: var(--ink-faint, #8a8477); }
</style>
