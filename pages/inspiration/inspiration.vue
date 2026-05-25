<template>
  <view class="page">
    <MainTabs current="inspiration" />

    <view class="filter-panel">
      <view class="filter-row">
        <picker :range="styleOpts" :value="styleIdx" @change="onStylePick">
          <view class="chip" :class="{ active: filterStyle !== ALL }">
            <text class="chip-k">风格</text>
            <text class="chip-v">{{ styleLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <picker :range="seasonOpts" :value="seasonIdx" @change="onSeasonPick">
          <view class="chip" :class="{ active: filterSeason !== ALL }">
            <text class="chip-k">季节</text>
            <text class="chip-v">{{ seasonLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
        <picker :range="colorOpts" :value="colorIdx" @change="onColorPick">
          <view class="chip" :class="{ active: filterColor !== ALL }">
            <text class="chip-k">颜色</text>
            <text class="chip-v">{{ colorLabel }}</text>
            <text class="chip-a">▾</text>
          </view>
        </picker>
      </view>
      <view class="filter-row second">
        <view class="chip toggle" :class="{ on: wantToBuyOnly }" @tap="wantToBuyOnly = !wantToBuyOnly">
          含「想买」
        </view>
        <text class="reset" @tap="resetFilter">重置</text>
        <text class="count">共 {{ displayList.length }} 条</text>
      </view>
    </view>

    <view v-if="displayList.length" class="grid">
      <InspirationCard
        v-for="item in displayList"
        :key="item.id"
        :item="item"
        @click="goDetail"
      />
    </view>

    <view v-else class="empty">
      <text class="empty-text">{{ emptyText }}</text>
      <text class="empty-hint">收藏穿搭参考图，标记颜色与想买单品</text>
    </view>

    <view class="fab" @tap="goCreate">+</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MainTabs from '../../components/MainTabs.vue'
import InspirationCard from '../../components/InspirationCard.vue'
import {
  getInspirations,
  filterInspirations,
  getAllPrimaryColors
} from '../../utils/inspirationStorage.js'
import { INSPIRATION_STYLES, SEASONS, PRESET_COLORS } from '../../utils/constants.js'
import { hydrateInspirationsImages } from '../../utils/imageStore.js'
import { getClothes } from '../../utils/storage.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'

const ALL = '全部'
const list = ref([])

const filterStyle = ref(ALL)
const filterSeason = ref(ALL)
const filterColor = ref(ALL)
const wantToBuyOnly = ref(false)

const styleOpts = [ALL, ...INSPIRATION_STYLES]
const seasonOpts = [ALL, ...SEASONS]

const styleIdx = computed(() => Math.max(0, styleOpts.indexOf(filterStyle.value)))
const seasonIdx = computed(() => Math.max(0, seasonOpts.indexOf(filterSeason.value)))

const colorOpts = computed(() => {
  const used = getAllPrimaryColors(list.value)
  const ordered = PRESET_COLORS.filter((c) => used.includes(c))
  const extra = used.filter((c) => !PRESET_COLORS.includes(c))
  return [ALL, ...ordered, ...extra]
})

const colorIdx = computed(() => Math.max(0, colorOpts.value.indexOf(filterColor.value)))

const styleLabel = computed(() => filterStyle.value)
const seasonLabel = computed(() => filterSeason.value)
const colorLabel = computed(() => filterColor.value)

const displayList = computed(() =>
  filterInspirations(list.value, {
    style: filterStyle.value,
    season: filterSeason.value,
    primaryColor: filterColor.value,
    wantToBuyOnly: wantToBuyOnly.value
  })
)

const emptyText = computed(() => {
  if (!list.value.length) return '还没有灵感'
  return '没有符合筛选的灵感'
})

onShow(async () => {
  list.value = getInspirations()
  await hydrateInspirationsImages(list.value)
  await hydrateClothesImages(getClothes())
})

function onStylePick(e) {
  filterStyle.value = styleOpts[e.detail.value] || ALL
}

function onSeasonPick(e) {
  filterSeason.value = seasonOpts[e.detail.value] || ALL
}

function onColorPick(e) {
  filterColor.value = colorOpts.value[e.detail.value] || ALL
}

function resetFilter() {
  filterStyle.value = ALL
  filterSeason.value = ALL
  filterColor.value = ALL
  wantToBuyOnly.value = false
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/inspirationDetail/inspirationDetail?id=${item.id}` })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/inspirationDetail/inspirationDetail?mode=create' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding-bottom: 140rpx;
}

.filter-panel {
  background: #fff;
  padding: 16rpx 24rpx;
  border-bottom: 1rpx solid #eee;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  align-items: center;

  &.second {
    margin-top: 12rpx;
  }
}

.chip {
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: 24rpx;
  color: #444;
  background: #f5f5f5;
  padding: 10rpx 16rpx;
  border-radius: 8rpx;
  border: 2rpx solid transparent;

  &.active {
    background: #fff5f6;
    border-color: #ffcdd2;
  }

  &.toggle.on {
    background: #ffe8ec;
    color: #ff2442;
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

  .chip.active & {
    color: #ff2442;
    font-weight: 600;
  }
}

.chip-a {
  font-size: 20rpx;
  color: #bbb;
}

.reset {
  font-size: 24rpx;
  color: #ff2442;
  margin-left: auto;
}

.count {
  font-size: 24rpx;
  color: #999;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
  padding: 20rpx 24rpx;
}

.empty {
  padding: 120rpx 48rpx;
  text-align: center;
}

.empty-text {
  display: block;
  font-size: 32rpx;
  color: #666;
}

.empty-hint {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #aaa;
}

.fab {
  position: fixed;
  right: 40rpx;
  bottom: 80rpx;
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: #ff2442;
  color: #fff;
  font-size: 56rpx;
  line-height: 100rpx;
  text-align: center;
  box-shadow: 0 8rpx 24rpx rgba(255, 36, 66, 0.35);
}
</style>
