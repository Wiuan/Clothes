<template>
  <view class="card" @tap="$emit('click', item)">
    <view class="img-wrap">
      <image
        v-if="imageSrc"
        class="thumb"
        :src="imageSrc"
        mode="aspectFill"
        lazy-load
      />
      <view v-else class="thumb placeholder">无图</view>
      <view v-if="hasWantToBuy" class="badge">想买</view>
    </view>
    <view class="info">
      <text class="name">{{ displayName }}</text>
      <view class="meta">
        <view class="color-dots">
          <view
            v-for="(hex, i) in colorDots"
            :key="i"
            class="color-dot"
            :style="{ background: hex }"
          />
        </view>
        <text v-if="item.style" class="style-tag">{{ item.style }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { getInspirationImageSrc } from '../utils/image.js'
import { getImageLoadTick } from '../utils/imageCache.js'
import { getColorHexByName } from '../utils/color.js'

const props = defineProps({
  item: { type: Object, required: true }
})

defineEmits(['click'])

const imgTick = computed(() => getImageLoadTick())
const imageSrc = computed(() => {
  imgTick.value
  return getInspirationImageSrc(props.item)
})

const displayName = computed(() => {
  const n = props.item.name
  if (n) return n
  if (props.item.style) return props.item.style
  return '灵感'
})

const colorDots = computed(() => {
  const prim = props.item.colorTags?.primary || []
  return prim.slice(0, 3).map((c) => getColorHexByName(c, {}))
})

const hasWantToBuy = computed(() =>
  (props.item.links || []).some((l) => l.relation === 'want_to_buy')
)
</script>

<style lang="scss" scoped>
.card {
  background: #fff;
  border-radius: 12rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.img-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
}

.thumb {
  width: 100%;
  height: 100%;
  display: block;
  background: #f0f0f0;
}

.thumb.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #bbb;
}

.badge {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  font-size: 20rpx;
  color: #fff;
  background: #ff2442;
  padding: 4rpx 10rpx;
  border-radius: 8rpx;
}

.info {
  padding: 12rpx 14rpx 16rpx;
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
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 8rpx;
}

.color-dots {
  display: flex;
  gap: 4rpx;
}

.color-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  border: 1rpx solid rgba(0, 0, 0, 0.08);
}

.style-tag {
  font-size: 20rpx;
  color: #888;
}
</style>
