<template>
  <view class="page">
    <!-- 新建 / 编辑 -->
    <template v-if="isCreate || isEdit">
      <view class="form-block">
        <text class="label">搭配名称</text>
        <input v-model="form.name" class="input" placeholder="例如：通勤一套" maxlength="30" />
      </view>
      <view class="form-block">
        <text class="label">备注</text>
        <input v-model="form.note" class="input" placeholder="选填" />
      </view>

      <button class="btn primary save-bar" @tap="onSaveForm">
        {{ isEdit ? '保存修改' : '保存搭配' }}
      </button>

      <text class="label section">选择衣服（已选 {{ selectedIds.length }} 件）</text>

      <view class="cloth-filter">
        <picker :range="clothSeasonOpts" :value="clothSeasonIdx" @change="onClothSeasonPick">
          <view class="fchip" :class="{ active: !!clothFilterSeason }">
            <text class="chip-k">季节</text>
            <text class="chip-v">{{ clothSeasonLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <picker :range="clothTypeOpts" :value="clothTypeIdx" @change="onClothTypePick">
          <view class="fchip" :class="{ active: !!clothFilterType }">
            <text class="chip-k">类型</text>
            <text class="chip-v">{{ clothTypeLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <picker :range="clothColorOpts" :value="clothColorIdx" @change="onClothColorPick">
          <view class="fchip" :class="{ active: !!clothFilterColor }">
            <text class="chip-k">颜色</text>
            <text class="chip-v">{{ clothColorLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <text
          v-if="clothFilterSeason || clothFilterType || clothFilterColor"
          class="cloth-filter-reset"
          @tap="resetClothFilter"
        >
          重置
        </text>
      </view>
      <text class="cloth-filter-hint">显示 {{ filteredClothes.length }} / {{ allClothes.length }} 件</text>

      <view class="pick-grid">
        <view
          v-for="c in filteredClothes"
          :key="c.id"
          class="pick-item"
          :class="{ selected: selectedSet.has(c.id) }"
          @tap="toggleSelect(c.id)"
        >
          <image
            v-if="getClothImageSrc(c)"
            class="pick-img"
            :src="getClothImageSrc(c)"
            mode="aspectFill"
          />
          <view v-else class="pick-img placeholder">无图</view>
          <text class="pick-name">{{ c.name }}</text>
          <view v-if="selectedSet.has(c.id)" class="check">✓</view>
        </view>
      </view>
    </template>

    <!-- 查看 -->
    <template v-else-if="match">
      <text class="title">{{ match.name }}</text>
      <text v-if="match.note" class="note">{{ match.note }}</text>

      <view class="outfit">
        <view
          v-for="cloth in outfitClothes"
          :key="cloth.id"
          class="outfit-item"
          @tap="goClothDetail(cloth.id)"
        >
          <image
            v-if="getClothImageSrc(cloth)"
            class="outfit-img"
            :src="getClothImageSrc(cloth)"
            mode="aspectFill"
          />
          <view v-else class="outfit-img placeholder">暂无图</view>
          <view class="outfit-info">
            <text class="oname">{{ cloth.name }}</text>
            <text class="ometa">{{ cloth.type }} · {{ cloth.season }}</text>
            <text class="otemp">{{ formatTempRange(cloth) }}</text>
          </view>
        </view>
      </view>

      <view v-if="!outfitClothes.length" class="warn">搭配中的衣服已被删除</view>

      <button class="btn primary outline" @tap="enterEdit">编辑搭配</button>
      <button class="btn del" @tap="onDeleteMatch">删除这套搭配</button>
    </template>

    <view v-else class="loading">加载中...</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import { PRESET_COLORS, SEASONS, TYPES } from '../../utils/constants.js'
import { getClothes } from '../../utils/storage.js'
import { filterClothes } from '../../utils/filter.js'
import { getMatchById, addMatch, updateMatch, removeMatch } from '../../utils/matchStorage.js'
import { getClothImageSrc } from '../../utils/image.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'
import { formatTempRange } from '../../utils/models.js'

const CLOTH_ALL = '全部'
const clothSeasonOpts = [CLOTH_ALL, ...SEASONS]
const clothTypeOpts = [CLOTH_ALL, ...TYPES]
const clothColorOpts = [CLOTH_ALL, ...PRESET_COLORS]

const isCreate = ref(false)
const isEdit = ref(false)
const match = ref(null)
const editMatchId = ref('')
const selectedIds = ref([])
const form = ref({ name: '', note: '' })

const selectedSet = computed(() => new Set(selectedIds.value))

const clothFilterSeason = ref('')
const clothFilterType = ref('')
const clothFilterColor = ref('')

const clothSeasonIdx = computed(() =>
  clothFilterSeason.value ? clothSeasonOpts.indexOf(clothFilterSeason.value) : 0
)
const clothTypeIdx = computed(() =>
  clothFilterType.value ? clothTypeOpts.indexOf(clothFilterType.value) : 0
)
const clothColorIdx = computed(() =>
  clothFilterColor.value ? clothColorOpts.indexOf(clothFilterColor.value) : 0
)
const clothSeasonLabel = computed(() => clothFilterSeason.value || CLOTH_ALL)
const clothTypeLabel = computed(() => clothFilterType.value || CLOTH_ALL)
const clothColorLabel = computed(() => clothFilterColor.value || CLOTH_ALL)

const filteredClothes = computed(() =>
  filterClothes(allClothes.value, {
    season: clothFilterSeason.value || undefined,
    type: clothFilterType.value || undefined,
    color: clothFilterColor.value || undefined
  })
)

function onClothSeasonPick(e) {
  const v = clothSeasonOpts[e.detail.value]
  clothFilterSeason.value = v === CLOTH_ALL ? '' : v
}
function onClothTypePick(e) {
  const v = clothTypeOpts[e.detail.value]
  clothFilterType.value = v === CLOTH_ALL ? '' : v
}
function onClothColorPick(e) {
  const v = clothColorOpts[e.detail.value]
  clothFilterColor.value = v === CLOTH_ALL ? '' : v
}
function resetClothFilter() {
  clothFilterSeason.value = ''
  clothFilterType.value = ''
  clothFilterColor.value = ''
}

const outfitClothes = computed(() => {
  if (!match.value) return []
  const all = getClothes()
  return match.value.clothIds.map((id) => all.find((c) => c.id === id)).filter(Boolean)
})

const allClothes = computed(() => getClothes().filter((c) => c.status !== 'discarded'))

onLoad((query) => {
  if (query.mode === 'create') {
    isCreate.value = true
    uni.setNavigationBarTitle({ title: '新建搭配' })
    return
  }
  if (query.mode === 'edit') {
    const id = query.id || ''
    const m = getMatchById(id)
    if (!m) {
      uni.showToast({ title: '搭配不存在', icon: 'none' })
      setTimeout(() => uni.navigateBack(), 800)
      return
    }
    isEdit.value = true
    editMatchId.value = m.id
    form.value = { name: m.name, note: m.note || '' }
    selectedIds.value = [...m.clothIds]
    uni.setNavigationBarTitle({ title: '编辑搭配' })
    return
  }
  const id = query.id || ''
  match.value = getMatchById(id)
  if (!match.value) {
    uni.showToast({ title: '搭配不存在', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
  } else {
    uni.setNavigationBarTitle({ title: '搭配详情' })
  }
})

onShow(async () => {
  await hydrateClothesImages(getClothes())
})

function enterEdit() {
  if (!match.value) return
  uni.redirectTo({
    url: `/pages/matchDetail/matchDetail?mode=edit&id=${match.value.id}`
  })
}

function toggleSelect(id) {
  const set = new Set(selectedIds.value)
  if (set.has(id)) set.delete(id)
  else set.add(id)
  selectedIds.value = [...set]
}

function onSaveForm() {
  if (!form.value.name.trim()) {
    uni.showToast({ title: '请填写名称', icon: 'none' })
    return
  }
  if (!selectedIds.value.length) {
    uni.showToast({ title: '请至少选一件衣服', icon: 'none' })
    return
  }
  if (isEdit.value) {
    const existing = getMatchById(editMatchId.value)
    updateMatch({
      ...existing,
      name: form.value.name.trim(),
      note: form.value.note.trim(),
      clothIds: [...selectedIds.value]
    })
    uni.showToast({ title: '已更新', icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/matchDetail/matchDetail?id=${editMatchId.value}` })
    }, 400)
    return
  }
  addMatch({
    id: `match_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
    name: form.value.name.trim(),
    note: form.value.note.trim(),
    clothIds: [...selectedIds.value],
    createdAt: Date.now()
  })
  uni.showToast({ title: '已保存', icon: 'success' })
  setTimeout(() => {
    uni.redirectTo({ url: '/pages/match/match' })
  }, 400)
}

function goClothDetail(id) {
  uni.navigateTo({ url: `/pages/detail/detail?id=${id}` })
}

function onDeleteMatch() {
  uni.showModal({
    title: '删除搭配',
    content: '确定删除这套搭配？',
    confirmColor: '#ff2442',
    success: (res) => {
      if (!res.confirm || !match.value) return
      removeMatch(match.value.id)
      uni.showToast({ title: '已删除', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 400)
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding: 24rpx;
  padding-bottom: 48rpx;
  box-sizing: border-box;
}

.form-block {
  background: #fff;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 16rpx;

  &.section {
    margin: 8rpx 0 16rpx;
  }
}

.input {
  font-size: 28rpx;
  padding: 16rpx 20rpx;
  background: #f7f7f8;
  border-radius: 12rpx;
}

.pick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}

.pick-item {
  position: relative;
  background: #fff;
  border-radius: 12rpx;
  overflow: hidden;
  border: 3rpx solid transparent;

  &.selected {
    border-color: #ff2442;
  }
}

.pick-img {
  width: 100%;
  height: 200rpx;
  display: block;
  background: #f0f0f0;

  &.placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #ccc;
    font-size: 22rpx;
  }
}

.pick-name {
  display: block;
  font-size: 22rpx;
  padding: 8rpx;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.check {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  background: #ff2442;
  color: #fff;
  border-radius: 50%;
  font-size: 22rpx;
  line-height: 36rpx;
  text-align: center;
}

.title {
  font-size: 40rpx;
  font-weight: 600;
  color: #222;
  display: block;
  margin-bottom: 12rpx;
}

.note {
  font-size: 26rpx;
  color: #888;
  display: block;
  margin-bottom: 24rpx;
}

.outfit-item {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 16rpx;
}

.outfit-img {
  width: 200rpx;
  height: 200rpx;
  flex-shrink: 0;
  background: #f0f0f0;

  &.placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    color: #aaa;
    font-size: 24rpx;
  }
}

.outfit-info {
  flex: 1;
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
}

.oname {
  font-size: 30rpx;
  font-weight: 600;
  color: #222;
}

.ometa {
  font-size: 24rpx;
  color: #888;
}

.otemp {
  font-size: 24rpx;
  color: #ff2442;
}

.warn {
  text-align: center;
  color: #999;
  padding: 40rpx;
  font-size: 26rpx;
}

.save-bar {
  margin: 0 0 20rpx;
  width: 100%;
  box-sizing: border-box;
}

.cloth-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  align-items: center;
  margin-bottom: 8rpx;
}

.fchip {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 14rpx;
  background: #fff;
  border-radius: 999rpx;
  border: 2rpx solid transparent;

  &.active {
    background: #fff5f6;
    border-color: #ffcdd2;
  }
}

.fchip .chip-k {
  font-size: 22rpx;
  color: #888;
}

.fchip .chip-v {
  font-size: 24rpx;
  color: #333;
}

.fchip.active .chip-v {
  color: #ff2442;
  font-weight: 600;
}

.fchip .chip-a {
  font-size: 20rpx;
  color: #bbb;
}

.cloth-filter-reset {
  font-size: 24rpx;
  color: #ff2442;
}

.cloth-filter-hint {
  display: block;
  font-size: 22rpx;
  color: #999;
  margin-bottom: 16rpx;
}

.btn {
  margin-top: 32rpx;
  border-radius: 999rpx;
  font-size: 30rpx;

  &.primary {
    background: #ff2442;
    color: #fff;
  }

  &.outline {
    background: #fff;
    color: #ff2442;
    border: 2rpx solid #ff2442;
  }

  &.del {
    background: #fff;
    color: #ff2442;
    border: 2rpx solid #ff2442;
  }
}

.loading {
  padding: 80rpx;
  text-align: center;
  color: #999;
}
</style>
