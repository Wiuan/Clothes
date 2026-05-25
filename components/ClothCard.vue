<template>
  <view
    class="card"
    :class="{ selectable, selected }"
    @tap="$emit('click', item)"
  >
    <view v-if="selectable" class="select-mark" :class="{ on: selected }">
      <text v-if="selected">✓</text>
    </view>
    <view class="img-wrap">
      <view v-if="wornToday" class="wear-dot" title="今日已穿" />
      <image
        v-if="imageSrc"
        class="thumb"
        :src="imageSrc"
        mode="aspectFill"
        lazy-load
      />
      <view v-else class="thumb placeholder">无图</view>
    </view>
    <view class="info">
      <text class="name">{{ item.name || '未命名' }}</text>
      <view class="meta">
        <view class="color-dots">
          <view
            v-for="(hex, i) in colorHexList"
            :key="i"
            class="color-dot"
            :style="{ background: hex }"
          />
        </view>
        <text class="color-label">{{ colorLabel }}</text>
        <text class="season-tag">{{ item.season }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { getClothImageSrc } from '../utils/image.js'
import { getImageLoadTick } from '../utils/imageCache.js'
import { getItemColorHexList, getItemColorLabel } from '../utils/color.js'

const props = defineProps({
  item: { type: Object, required: true },
  /** compact | default */
  size: { type: String, default: 'compact' },
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
  /** 今日穿着记录中是否包含该件 */
  wornToday: { type: Boolean, default: false }
})

defineEmits(['click'])

const imgTick = computed(() => getImageLoadTick())
const imageSrc = computed(() => {
  imgTick.value
  return getClothImageSrc(props.item)
})
const colorHexList = computed(() => getItemColorHexList(props.item, 3))
const colorLabel = computed(() => getItemColorLabel(props.item))
</script>

<style lang="scss" scoped>
.card {
  position: relative;
  background: #fff;
  border-radius: 12rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);

  &.selectable.selected {
    box-shadow: 0 0 0 3rpx #ff2442;
  }
}

.wear-dot {
  position: absolute;
  top: 10rpx;
  left: 10rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #ff2442;
  border: 2rpx solid #fff;
  box-shadow: 0 2rpx 6rpx rgba(255, 36, 66, 0.45);
  z-index: 2;
}

.select-mark {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  border: 2rpx solid #ccc;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #fff;

  &.on {
    background: #ff2442;
    border-color: #ff2442;
  }
}

/* 3:4 竖图，一屏约 4–6 件（三列） */
.img-wrap {
  position: relative;
  width: 100%;
  padding-bottom: 133.33%;
  height: 0;
  background: #f5f5f5;
  overflow: hidden;
}

.thumb {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ccc;
  font-size: 20rpx;
}

.info {
  padding: 10rpx 10rpx 12rpx;
}

.name {
  font-size: 22rpx;
  color: #222;
  font-weight: 500;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.35;
}

.meta {
  display: flex;
  align-items: center;
  margin-top: 8rpx;
  gap: 6rpx;
  min-height: 28rpx;
}

.color-dots {
  display: flex;
  align-items: center;
  gap: 4rpx;
  flex-shrink: 0;
}

.color-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  border: 1rpx solid rgba(0, 0, 0, 0.08);
}

.color-label {
  font-size: 18rpx;
  color: #888;
  max-width: 72rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.season-tag {
  font-size: 18rpx;
  color: #999;
  background: #f5f5f5;
  padding: 2rpx 8rpx;
  border-radius: 4rpx;
  margin-left: auto;
  flex-shrink: 0;
}
</style>
