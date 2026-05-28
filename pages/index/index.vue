<template>
  <view class="page">
    <MainTabs current="wardrobe" />

    <!-- 筛选区 -->
    <view class="filter-panel">
      <view class="filter-row">
        <picker :range="seasonOpts" :value="seasonIdx" @change="onSeasonPick">
          <view class="chip" :class="{ active: !!filterSeason }">
            <text class="chip-k">季节</text>
            <text class="chip-v">{{ seasonLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <picker :range="typeOpts" :value="typeIdx" @change="onTypePick">
          <view class="chip" :class="{ active: !!filterType }">
            <text class="chip-k">类型</text>
            <text class="chip-v">{{ typeLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <picker :range="colorOpts" :value="colorIdx" @change="onColorPick">
          <view class="chip" :class="{ active: !!filterColor }">
            <text class="chip-k">颜色</text>
            <text class="chip-v">{{ colorLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
      </view>

      <view class="sort-row">
        <SortToggle
          v-for="f in WARDROBE_SORT_FIELDS"
          :key="f"
          :label="SORT_FIELD_LABELS[f]"
          :ascending="sortField === f ? sortAsc : defaultSortAsc(f)"
          :selected="sortField === f"
          @select="selectSort(f)"
          @toggle-dir="toggleSortDir(f)"
        />
        <view class="sort-row-tail">
          <text class="temp-label">温度</text>
          <input
            v-model="filterTemp"
            class="temp-input"
            type="number"
            placeholder="℃"
            @confirm="onTempConfirm"
          />
          <text v-if="filterTemp !== ''" class="temp-hint">适合{{ displayList.length }}件</text>
          <text class="reset" @tap="resetFilter">重置</text>
        </view>
      </view>
    </view>

    <view class="io-bar">
      <text class="io-btn wear-btn" @tap="goCheckin">今日</text>
      <text class="io-divider">|</text>
      <text class="io-btn wear-btn" @tap="goWearStats">统计</text>
      <text class="io-divider">|</text>
      <text class="io-btn" @tap="onExport">导出</text>
      <text class="io-divider">|</text>
      <text class="io-btn" @tap="onImport">导入</text>
      <text class="io-divider">|</text>
      <text class="io-btn" @tap="toggleBatchMode">{{ batchMode ? '取消' : '批量' }}</text>
      <text class="io-divider">|</text>
      <text class="io-btn discard-link" @tap="goDiscarded">已扔掉</text>
      <text class="count">共 {{ displayList.length }} 件</text>
    </view>

    <!-- 三列紧凑网格 -->
    <view v-if="displayList.length" class="grid" :key="gridLayoutKey">
      <ClothCard
        v-for="item in displayList"
        :key="item.id"
        :item="item"
        size="compact"
        :selectable="batchMode"
        :selected="selectedSet.has(item.id)"
        :worn-today="todayWornSet.has(item.id)"
        @click="onCardClick"
      />
    </view>

    <view v-else class="empty">
      <text class="empty-text">{{ emptyText }}</text>
      <text class="empty-hint">点击右下角 + 添加</text>
    </view>

    <view v-if="!batchMode" class="fab" @tap="goAdd">+</view>

    <view v-if="batchMode" class="batch-bar">
      <text class="batch-count">已选 {{ selectedIds.length }}</text>
      <text class="batch-all" @tap="toggleSelectAll">{{ allSelected ? '取消全选' : '全选' }}</text>
      <button
        class="batch-go"
        type="primary"
        :disabled="!selectedIds.length"
        @tap="goBatchEdit"
      >
        编辑
      </button>
      <button
        class="batch-del"
        :disabled="!selectedIds.length"
        @tap="onBatchDelete"
      >
        删除
      </button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MainTabs from '../../components/MainTabs.vue'
import ClothCard from '../../components/ClothCard.vue'
import SortToggle from '../../components/SortToggle.vue'
import { getClothes, batchRemoveClothes } from '../../utils/storage.js'
import { removeClothFromMatches } from '../../utils/matchStorage.js'
import { filterClothes } from '../../utils/filter.js'
import { sortClothes, SORT_FIELD_LABELS, WARDROBE_SORT_FIELDS } from '../../utils/sortClothes.js'
import { PRESET_COLORS, SEASONS, TYPES } from '../../utils/constants.js'
import { exportData, importData } from '../../utils/io.js'
import { setBatchEditIds, consumeBatchEditDone } from '../../utils/batch.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'
import { getImageLoadTick } from '../../utils/imageCache.js'
import { getWornClothIdSetForDate } from '../../utils/checkinStorage.js'
import { setClothBrowseIds } from '../../utils/browseOrder.js'

const ALL = '全部'
const seasonOpts = [ALL, ...SEASONS]
const typeOpts = [ALL, ...TYPES]
const colorOpts = [ALL, ...PRESET_COLORS]
const list = ref([])
const filterSeason = ref('')
const filterType = ref('')
const filterColor = ref('')
const filterTemp = ref('')
const sortField = ref('created')
const sortAsc = ref(false)
const batchMode = ref(false)
const selectedIds = ref([])
const todayWornTick = ref(0)

const todayWornSet = computed(() => {
  todayWornTick.value
  return getWornClothIdSetForDate()
})

const seasonIdx = computed(() => (filterSeason.value ? seasonOpts.indexOf(filterSeason.value) : 0))
const typeIdx = computed(() => (filterType.value ? typeOpts.indexOf(filterType.value) : 0))
const colorIdx = computed(() => (filterColor.value ? colorOpts.indexOf(filterColor.value) : 0))
const seasonLabel = computed(() => filterSeason.value || ALL)
const typeLabel = computed(() => filterType.value || ALL)
const colorLabel = computed(() => filterColor.value || ALL)
const displayList = computed(() => {
  const filtered = filterClothes(list.value, {
    season: filterSeason.value,
    type: filterType.value,
    color: filterColor.value,
    currentTemp: filterTemp.value
  })
  return sortClothes(filtered, sortField.value, sortAsc.value)
})

const gridLayoutKey = computed(() =>
  [sortField.value, sortAsc.value, filterSeason.value, filterType.value, filterColor.value, filterTemp.value].join(
    '|'
  )
)

const selectedSet = computed(() => new Set(selectedIds.value))

const allSelected = computed(
  () =>
    displayList.value.length > 0 &&
    displayList.value.every((c) => selectedSet.value.has(c.id))
)

const activeCount = computed(
  () => list.value.filter((c) => c.status !== 'discarded').length
)

const emptyText = computed(() => {
  if (!activeCount.value) return '还没有衣服'
  if (filterTemp.value !== '') return '没有适合该温度的衣服'
  return '没有符合条件的衣服'
})

const imgTick = ref(0)

onShow(async () => {
  list.value = getClothes()
  await hydrateClothesImages(list.value)
  imgTick.value = getImageLoadTick()
  todayWornTick.value++
  if (consumeBatchEditDone()) {
    selectedIds.value = []
  }
})

function pickVal(options, index) {
  const v = options[Number(index)]
  return v === ALL ? '' : v
}

function onSeasonPick(e) {
  filterSeason.value = pickVal(seasonOpts, e.detail.value)
}
function onTypePick(e) {
  filterType.value = pickVal(typeOpts, e.detail.value)
}
function onColorPick(e) {
  filterColor.value = pickVal(colorOpts, e.detail.value)
}
function scrollListTop() {
  nextTick(() => {
    setTimeout(() => {
      uni.pageScrollTo({ scrollTop: 0, duration: 0 })
    }, 32)
  })
}

watch(
  () => [sortField.value, sortAsc.value, filterSeason.value, filterType.value, filterColor.value, filterTemp.value],
  scrollListTop
)

function defaultSortAsc(field) {
  return field !== 'created'
}

function selectSort(field) {
  if (sortField.value === field) return
  sortField.value = field
  sortAsc.value = defaultSortAsc(field)
}

function toggleSortDir(field) {
  if (sortField.value !== field) {
    sortField.value = field
    sortAsc.value = defaultSortAsc(field)
    return
  }
  sortAsc.value = !sortAsc.value
}

function onTempConfirm() {
  /* v-model 已更新，computed 自动重算 */
}

function resetFilter() {
  filterSeason.value = ''
  filterType.value = ''
  filterColor.value = ''
  filterTemp.value = ''
  sortField.value = 'created'
  sortAsc.value = false
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  selectedIds.value = []
}

function onCardClick(item) {
  if (batchMode.value) {
    const set = new Set(selectedIds.value)
    if (set.has(item.id)) set.delete(item.id)
    else set.add(item.id)
    selectedIds.value = [...set]
    return
  }
  setClothBrowseIds(displayList.value.map((c) => c.id))
  uni.navigateTo({ url: `/pages/detail/detail?id=${item.id}` })
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = displayList.value.map((c) => c.id)
  }
}

function goBatchEdit() {
  if (!selectedIds.value.length) return
  setBatchEditIds(selectedIds.value)
  uni.navigateTo({ url: '/pages/batchEdit/batchEdit' })
}

function onBatchDelete() {
  const n = selectedIds.value.length
  if (!n) return
  uni.showModal({
    title: '批量删除',
    content: `确定永久删除选中的 ${n} 件衣服？相关搭配中会移除这些衣服，且无法恢复。`,
    confirmColor: '#ff2442',
    success: (res) => {
      if (!res.confirm) return
      selectedIds.value.forEach((id) => removeClothFromMatches(id))
      batchRemoveClothes(selectedIds.value)
      selectedIds.value = []
      batchMode.value = false
      list.value = getClothes()
      uni.showToast({ title: '已删除', icon: 'success' })
    }
  })
}

function goDiscarded() {
  uni.navigateTo({ url: '/pages/discarded/discarded' })
}

function goCheckin() {
  uni.navigateTo({ url: '/pages/checkin/checkin' })
}

function goWearStats() {
  uni.navigateTo({ url: '/pages/wearStats/wearStats' })
}

function goAdd() {
  uni.navigateTo({ url: '/pages/add/add' })
}

async function onExport() {
  try {
    const result = await exportData()
    // #ifdef H5
    uni.showModal({
      title: '导出成功',
      content: `文件已下载：\n${result?.filePath || 'wardrobe_export.zip'}`,
      showCancel: false
    })
    // #endif
    // #ifdef APP-PLUS
    // 成功提示已在导出实现内弹窗
    void result
    // #endif
    // #ifndef H5
    // #ifndef APP-PLUS
    uni.showToast({ title: '导出成功', icon: 'success' })
    // #endif
    // #endif
  } catch (e) {
    if (e && e.message !== 'empty') {
      const msg = e && e.message ? String(e.message) : ''
      console.error('onExport', e)
      uni.showModal({
        title: '导出失败',
        content: msg || '请查看控制台日志',
        showCancel: false
      })
    }
  }
}

async function onImport() {
  try {
    const result = await importData()
    list.value = result.clothes || result
    await hydrateClothesImages(list.value)
    imgTick.value = getImageLoadTick()
    /* 导入条数已在 utils/io.js 弹窗中展示（衣物+搭配+灵感+穿着记录） */
  } catch {
    /* 已提示 */
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

.filter-panel {
  background: #fff;
  padding: 10rpx 16rpx 8rpx;
  position: sticky;
  top: 0;
  z-index: 9;
}

.filter-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 8rpx;
}

.sort-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: 10rpx;
  margin-top: 8rpx;
}

.sort-row-tail {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;
  margin-left: auto;
  flex: 1;
  min-width: 200rpx;
  justify-content: flex-end;
}

.chip {
  display: flex;
  align-items: center;
  gap: 4rpx;
  font-size: 22rpx;
  color: #333;
  padding: 6rpx 10rpx;
  background: #fff;
  border-radius: 999rpx;
  border: 1rpx solid #eee;
  flex: 1;
  min-width: 0;
  justify-content: center;

  &.active {
    background: #fff5f6;
    border-color: #ffcdd2;
  }
}

.chip-k {
  font-size: 22rpx;
  color: #888;
}

.chip-v {
  font-size: 24rpx;
  color: #333;
  max-width: 88rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  .chip.active & {
    color: #ff2442;
    font-weight: 600;
  }
}

.chip-a {
  font-size: 20rpx;
  color: #bbb;
}

.temp-label {
  font-size: 22rpx;
  color: #666;
  flex-shrink: 0;
}

.temp-input {
  width: 88rpx;
  font-size: 22rpx;
  padding: 6rpx 12rpx;
  background: #fff;
  border: 1rpx solid #eee;
  border-radius: 8rpx;
  text-align: center;
  box-sizing: border-box;
}

.temp-hint {
  font-size: 20rpx;
  color: #ff2442;
  flex-shrink: 0;
}

.sort-row-tail .reset {
  font-size: 22rpx;
  color: #ff2442;
  flex-shrink: 0;
}

.io-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  padding: 8rpx 16rpx;
  font-size: 22rpx;
  color: #666;
  row-gap: 8rpx;
}

.io-btn.wear-btn {
  color: #ff2442;
  font-weight: 600;
}

.io-btn {
  color: #1e88e5;
}

.io-divider {
  margin: 0 12rpx;
  color: #ddd;
}

.count {
  margin-left: auto;
}

/* 三列：约一屏 4–6 件（视屏高） */
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  padding: 12rpx 16rpx 24rpx;
}

.empty {
  padding: 120rpx 40rpx;
  text-align: center;
}

.empty-text {
  display: block;
  font-size: 28rpx;
  color: #999;
}

.empty-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #bbb;
}

.batch-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.06);
  z-index: 20;
}

.batch-count {
  font-size: 26rpx;
  color: #333;
  flex-shrink: 0;
}

.batch-all {
  font-size: 26rpx;
  color: #ff2442;
  flex-shrink: 0;
}

.batch-go {
  flex: 1;
  margin: 0;
  background: #ff2442;
  border-radius: 999rpx;
  font-size: 26rpx;
  padding: 0 16rpx;
}

.batch-del {
  flex: 0 0 auto;
  margin: 0;
  background: #fff;
  color: #ff2442;
  border: 2rpx solid #ff2442;
  border-radius: 999rpx;
  font-size: 26rpx;
  padding: 0 28rpx;
  line-height: 2.2;
}

.discard-link {
  color: #996600;
}

.fab {
  position: fixed;
  right: 32rpx;
  bottom: calc(32rpx + env(safe-area-inset-bottom));
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #ff2442;
  color: #fff;
  font-size: 52rpx;
  line-height: 92rpx;
  text-align: center;
  box-shadow: 0 6rpx 20rpx rgba(255, 36, 66, 0.35);
  z-index: 20;
}
</style>
