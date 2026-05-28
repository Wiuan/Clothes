<template>
  <view class="page">
    <view class="filter-panel">
      <view class="period-row">
        <view
          v-for="p in WEAR_PERIOD_OPTS"
          :key="p.key"
          class="period-chip"
          :class="{ active: periodKey === p.key }"
          @tap="periodKey = p.key"
        >
          {{ p.label }}
        </view>
      </view>
      <view class="season-row">
        <picker class="filter-picker" :range="seasonOpts" :value="seasonIdx" @change="onSeasonPick">
          <view class="chip" :class="{ active: !!filterSeason }">{{ seasonLabel }} ▾</view>
        </picker>
        <text class="summary">
          {{ periodLabel }}·{{ rankList.length }}件·未穿{{ unwornCount }}
        </text>
      </view>
      <view class="sort-row">
        <view class="sort-chip" :class="{ active: sortAsc }" @tap="sortAsc = true">少→多</view>
        <view class="sort-chip" :class="{ active: !sortAsc }" @tap="sortAsc = false">多→少</view>
      </view>
      <text class="tip-inline">{{ sortTip }}</text>
    </view>

    <view v-if="rankList.length" class="list">
      <view
        v-for="row in rankList"
        :key="row.cloth.id"
        class="row"
        :class="{ unworn: row.count === 0 }"
        @tap="goDetail(row.cloth.id)"
      >
        <image
          v-if="getClothImageSrc(row.cloth)"
          class="thumb"
          :src="getClothImageSrc(row.cloth)"
          mode="aspectFill"
        />
        <view v-else class="thumb placeholder">无图</view>
        <view class="info">
          <text class="name">{{ row.cloth.name }}</text>
          <text class="meta">{{ row.cloth.season }} · {{ row.cloth.type }}</text>
          <text class="last">上次：{{ formatWearDate(row.lastDate) }}</text>
        </view>
        <view class="count-wrap">
          <text class="count" :class="{ zero: row.count === 0 }">{{ row.count }}</text>
          <text class="count-unit">次</text>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <text>没有符合条件的衣服</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { SEASONS } from '../../utils/constants.js'
import { getClothes } from '../../utils/storage.js'
import { getWearLogs } from '../../utils/checkinStorage.js'
import {
  WEAR_PERIOD_OPTS,
  resolveWearPeriod,
  buildWearRankList,
  formatWearDate
} from '../../utils/wearStats.js'
import { getClothImageSrc } from '../../utils/image.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'

const ALL = '全部'
const seasonOpts = [ALL, ...SEASONS]

const periodKey = ref('365d')
const filterSeason = ref('')
const sortAsc = ref(true)
const logs = ref([])

const seasonIdx = computed(() =>
  filterSeason.value ? seasonOpts.indexOf(filterSeason.value) : 0
)
const seasonLabel = computed(() => filterSeason.value || ALL)

const period = computed(() => resolveWearPeriod(periodKey.value))
const periodLabel = computed(() => period.value.label)

const rankList = computed(() =>
  buildWearRankList(getClothes().filter((c) => c.status !== 'discarded'), logs.value, {
    startMs: period.value.startMs,
    endMs: period.value.endMs,
    season: filterSeason.value,
    ascending: sortAsc.value
  })
)

const unwornCount = computed(() => rankList.value.filter((r) => r.count === 0).length)

const sortTip = computed(() =>
  sortAsc.value
    ? '按穿着次数从少到多，方便找出「压箱底」的衣服'
    : '按穿着次数从多到少，查看最常穿衣物'
)

function onSeasonPick(e) {
  const v = seasonOpts[e.detail.value]
  filterSeason.value = v === ALL ? '' : v
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/detail/detail?id=${id}` })
}

onShow(async () => {
  logs.value = getWearLogs()
  await hydrateClothesImages(getClothes())
})
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding-bottom: env(safe-area-inset-bottom);
}

.filter-panel {
  background: #fff;
  padding: 14rpx 18rpx;
  border-bottom: 1rpx solid #eee;
}

.period-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 10rpx;
}

.period-chip {
  padding: 6rpx 16rpx;
  font-size: 22rpx;
  color: #666;
  background: #f7f7f8;
  border-radius: 999rpx;
  border: 2rpx solid transparent;

  &.active {
    color: #ff2442;
    background: #fff5f6;
    border-color: #ffcdd2;
    font-weight: 600;
  }
}

.season-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8rpx;
}

.filter-picker {
  flex: 0 0 auto;
}

.season-row .chip {
  font-size: 22rpx;
  padding: 6rpx 12rpx;
  background: #fff;
  border-radius: 999rpx;
  border: 1rpx solid #eee;
  color: #333;
  white-space: nowrap;

  &.active {
    color: #ff2442;
    font-weight: 600;
    background: #fff5f6;
    border-color: #ffcdd2;
  }
}

.summary {
  flex: 1;
  min-width: 0;
  font-size: 20rpx;
  color: #999;
  text-align: right;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sort-row {
  display: flex;
  gap: 8rpx;
  margin-top: 10rpx;
}

.sort-chip {
  padding: 6rpx 16rpx;
  font-size: 22rpx;
  color: #666;
  background: #f7f7f8;
  border-radius: 999rpx;
  border: 2rpx solid transparent;

  &.active {
    color: #ff2442;
    background: #fff5f6;
    border-color: #ffcdd2;
    font-weight: 600;
  }
}

.tip-inline {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #888;
  line-height: 1.4;
}

.list {
  padding: 6rpx 18rpx 18rpx;
}

.row {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 12rpx;
  padding: 10rpx;
  margin-bottom: 6rpx;

  &.unworn {
    background: #fffafa;
    border: 1rpx solid #ffe0e6;
  }
}

.thumb {
  width: 80rpx;
  height: 80rpx;
  border-radius: 12rpx;
  flex-shrink: 0;
  background: #f0f0f0;
}

.thumb.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #ccc;
}

.info {
  flex: 1;
  margin-left: 12rpx;
  min-width: 0;
}

.name {
  font-size: 24rpx;
  font-weight: 600;
  color: #222;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  font-size: 22rpx;
  color: #999;
  display: block;
  margin-top: 0;
}

.last {
  font-size: 20rpx;
  color: #bbb;
  display: block;
  margin-top: 2rpx;
}

.row.unworn .last {
  color: #ff2442;
}

.count-wrap {
  text-align: right;
  flex-shrink: 0;
  margin-left: 12rpx;
}

.count {
  font-size: 32rpx;
  font-weight: 700;
  color: #ff2442;
  display: block;
  line-height: 1;

  &.zero {
    color: #ccc;
  }
}

.row.unworn .count.zero {
  color: #ff2442;
}

.count-unit {
  font-size: 20rpx;
  color: #999;
}

.empty {
  padding: 100rpx 32rpx;
  text-align: center;
  color: #999;
  font-size: 24rpx;
}
</style>
