<template>
  <view class="sort-toggle" :class="{ active: selected }">
    <text class="sort-label" @tap="$emit('select')">{{ label }}</text>
    <view class="sort-arrow-hit" @tap.stop="$emit('toggle-dir')">
      <text class="sort-arrow">{{ ascending ? '↑' : '↓' }}</text>
    </view>
  </view>
</template>

<script setup>
defineProps({
  label: { type: String, default: '时间' },
  ascending: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})

defineEmits(['select', 'toggle-dir'])
</script>

<style lang="scss" scoped>
.sort-toggle {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-shrink: 0;
  min-height: 52rpx;
  padding: 0 4rpx 0 12rpx;
  background: #fff;
  border: 1rpx solid #eee;
  border-radius: 999rpx;
  box-sizing: border-box;

  &.active {
    background: #fff5f6;
    border-color: #ffcdd2;
  }
}

.sort-label {
  font-size: 22rpx;
  line-height: 52rpx;
  color: #333;
  white-space: nowrap;

  .active & {
    color: #ff2442;
    font-weight: 600;
  }
}

.sort-arrow-hit {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 40rpx;
  height: 52rpx;
  margin-left: 0;
}

.sort-arrow {
  font-size: 22rpx;
  line-height: 52rpx;
  color: #ff2442;
  font-weight: 700;
}
</style>
