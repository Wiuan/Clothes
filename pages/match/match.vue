<template>
  <view class="page">
    <MainTabs current="match" />

    <view v-if="matches.length" class="list">
      <MatchCard
        v-for="m in matches"
        :key="m.id"
        :item="m"
        @click="goDetail"
      />
    </view>

    <view v-else class="empty">
      <text class="empty-text">还没有搭配</text>
      <text class="empty-hint">点击右下角创建第一套</text>
    </view>

    <view class="fab" @tap="goCreate">+</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import MainTabs from '../../components/MainTabs.vue'
import MatchCard from '../../components/MatchCard.vue'
import { getMatches } from '../../utils/matchStorage.js'
import { getClothes } from '../../utils/storage.js'
import { hydrateClothesImages } from '../../utils/imageStore.js'

const matches = ref([])

onShow(async () => {
  matches.value = getMatches()
  await hydrateClothesImages(getClothes())
})

function goDetail(item) {
  uni.navigateTo({ url: `/pages/matchDetail/matchDetail?id=${item.id}` })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/matchDetail/matchDetail?mode=create' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

.list {
  padding: 20rpx 24rpx;
}

.empty {
  padding: 160rpx 40rpx;
  text-align: center;
}

.empty-text {
  display: block;
  font-size: 30rpx;
  color: #999;
}

.empty-hint {
  display: block;
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #bbb;
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
