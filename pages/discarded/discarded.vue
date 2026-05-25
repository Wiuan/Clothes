<template>
  <view class="page">
    <view class="tip-bar">
      <text>已扔掉的衣物不在主衣柜显示，可在此查看尺寸等信息</text>
    </view>

    <view class="filter-panel">
      <view class="filter-row">
        <picker :range="seasonOpts" :value="seasonIdx" @change="onSeasonPick">
          <view class="chip">{{ seasonLabel }} ▾</view>
        </picker>
        <picker :range="typeOpts" :value="typeIdx" @change="onTypePick">
          <view class="chip">{{ typeLabel }} ▾</view>
        </picker>
        <text class="reset" @tap="resetFilter">重置</text>
      </view>
    </view>

    <view class="count-bar">共 {{ displayList.length }} 件已扔掉</view>

    <view v-if="displayList.length" class="grid">
      <ClothCard
        v-for="item in displayList"
        :key="item.id"
        :item="item"
        size="compact"
        @click="goDetail"
      />
    </view>

    <view v-else class="empty">
      <text class="empty-text">暂无已扔掉的衣物</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ClothCard from '../../components/ClothCard.vue'
import { getClothes } from '../../utils/storage.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'
import { getImageLoadTick } from '../../utils/imageCache.js'
import { filterClothes } from '../../utils/filter.js'
import { SEASONS, TYPES } from '../../utils/constants.js'

const ALL = '全部'
const seasonOpts = [ALL, ...SEASONS]
const typeOpts = [ALL, ...TYPES]

const list = ref([])
const filterSeason = ref('')
const filterType = ref('')

const seasonIdx = computed(() => (filterSeason.value ? seasonOpts.indexOf(filterSeason.value) : 0))
const typeIdx = computed(() => (filterType.value ? typeOpts.indexOf(filterType.value) : 0))
const seasonLabel = computed(() => filterSeason.value || ALL)
const typeLabel = computed(() => filterType.value || ALL)

const displayList = computed(() =>
  filterClothes(list.value, {
    season: filterSeason.value,
    type: filterType.value,
    onlyDiscarded: true
  })
)

const imgTick = ref(0)

onShow(async () => {
  list.value = getClothes()
  await hydrateClothesImages(list.value)
  imgTick.value = getImageLoadTick()
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

function resetFilter() {
  filterSeason.value = ''
  filterType.value = ''
}

function goDetail(item) {
  uni.navigateTo({ url: `/pages/detail/detail?id=${item.id}` })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding-bottom: 48rpx;
}

.tip-bar {
  background: #fff8e6;
  padding: 20rpx 24rpx;
  font-size: 24rpx;
  color: #996600;
  line-height: 1.5;
}

.filter-panel {
  background: #fff;
  padding: 16rpx 20rpx;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12rpx;
}

.chip {
  font-size: 24rpx;
  padding: 8rpx 18rpx;
  background: #f7f7f8;
  border-radius: 999rpx;
}

.reset {
  margin-left: auto;
  font-size: 24rpx;
  color: #ff2442;
}

.count-bar {
  padding: 12rpx 20rpx;
  font-size: 22rpx;
  color: #888;
}

.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  padding: 12rpx 16rpx;
}

.empty {
  padding: 120rpx 40rpx;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}
</style>
