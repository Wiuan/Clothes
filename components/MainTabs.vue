<template>
  <view class="main-tabs">
    <view
      class="tab"
      :class="{ active: current === 'wardrobe' }"
      @tap="switchTo('wardrobe')"
    >
      衣柜
    </view>
    <view
      class="tab"
      :class="{ active: current === 'match' }"
      @tap="switchTo('match')"
    >
      搭配
    </view>
    <view
      class="tab"
      :class="{ active: current === 'inspiration' }"
      @tap="switchTo('inspiration')"
    >
      灵感
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  current: { type: String, default: 'wardrobe' }
})

const TAB_URL = {
  wardrobe: '/pages/index/index',
  match: '/pages/match/match',
  inspiration: '/pages/inspiration/inspiration'
}

function switchTo(tab) {
  if (tab === props.current) return
  const url = TAB_URL[tab]
  if (url) uni.redirectTo({ url })
}
</script>

<style lang="scss" scoped>
.main-tabs {
  display: flex;
  background: #fff;
  padding: 6rpx 16rpx 0;
  border-bottom: 1rpx solid #eee;
}

.tab {
  flex: 1;
  text-align: center;
  font-size: 28rpx;
  color: #888;
  padding: 10rpx 0 14rpx;
  position: relative;

  &.active {
    color: #ff2442;
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      left: 50%;
      bottom: 0;
      transform: translateX(-50%);
      width: 48rpx;
      height: 6rpx;
      background: #ff2442;
      border-radius: 3rpx;
    }
  }
}
</style>
