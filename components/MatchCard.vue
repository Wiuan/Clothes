<template>
  <view class="card" @tap="$emit('click', item)">
    <view class="thumbs">
      <image
        v-for="(src, i) in thumbSrcs"
        :key="i"
        class="thumb"
        :src="src || ''"
        mode="aspectFill"
      />
      <view v-for="n in emptySlots" :key="'e' + n" class="thumb empty" />
    </view>
    <view class="info">
      <text class="name">{{ item.name }}</text>
      <text class="sub">{{ item.clothIds.length }} 件</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { getClothById } from '../utils/storage.js'
import { getClothImageSrc } from '../utils/image.js'
import { getImageLoadTick } from '../utils/imageCache.js'

const props = defineProps({
  item: { type: Object, required: true }
})

defineEmits(['click'])

const imgTick = computed(() => getImageLoadTick())

const thumbSrcs = computed(() => {
  imgTick.value
  const ids = (props.item.clothIds || []).slice(0, 3)
  return ids.map((id) => {
    const cloth = getClothById(id)
    return cloth ? getClothImageSrc(cloth) : ''
  })
})

const emptySlots = computed(() => {
  const n = Math.min(3, props.item.clothIds?.length || 0)
  return Math.max(0, 3 - n)
})
</script>

<style lang="scss" scoped>
.card {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.thumbs {
  display: flex;
  gap: 4rpx;
  padding: 12rpx 12rpx 0;
}

.thumb {
  flex: 1;
  height: 160rpx;
  border-radius: 8rpx;
  background: #f0f0f0;
}

.thumb.empty {
  background: #f5f5f5;
}

.info {
  padding: 16rpx 20rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.name {
  font-size: 28rpx;
  font-weight: 600;
  color: #222;
}

.sub {
  font-size: 24rpx;
  color: #999;
}
</style>
