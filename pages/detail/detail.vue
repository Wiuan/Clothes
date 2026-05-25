<template>

  <view v-if="item" class="page">

    <image

      v-if="imageSrc"

      class="hero"

      :src="imageSrc"

      mode="widthFix"

      @tap="previewImage"

    />

    <view v-else class="hero placeholder">暂无照片</view>



    <view class="panel">

      <view class="title-row">

        <text class="title">{{ item.name }}</text>

        <text v-if="isDiscarded" class="badge-discard">已扔掉</text>

      </view>



      <view class="tags">

        <view
          v-for="(c, i) in colorTags"
          :key="i"
          class="tag color"
        >
          <view class="dot" :style="{ background: colorHexFor(c) }" />
          <text>{{ c }}</text>
        </view>

        <text class="tag">{{ item.season }}</text>

        <text class="tag">{{ item.type }}</text>

        <text class="tag temp">{{ tempText }}</text>

      </view>

      <view v-if="!isDiscarded" class="wear-block">
        <text class="section-title">穿着统计</text>
        <view class="wear-grid">
          <view class="wear-cell">
            <text class="k">近30天</text>
            <text class="v">{{ wearCounts.c30 }} 次</text>
          </view>
          <view class="wear-cell">
            <text class="k">近一年</text>
            <text class="v">{{ wearCounts.c365 }} 次</text>
          </view>
          <view class="wear-cell">
            <text class="k">累计</text>
            <text class="v">{{ wearCounts.cAll }} 次</text>
          </view>
        </view>
        <text class="wear-last">上次穿着：{{ lastWearText }}</text>
        <button
          v-if="!wornToday"
          class="wear-checkin-btn"
          @tap="onCheckinToday"
        >
          今天穿了这件
        </button>
        <view v-else class="wear-checkin-done">
          <text class="done-icon">✓</text>
          <text>今日已记录</text>
        </view>
        <text class="wear-go" @tap="goWearStats">查看穿着排行 →</text>
      </view>

      <view v-if="!isDiscarded && colorRec.ready" class="color-rec-block">
        <text class="section-title">颜色搭配参考</text>
        <text class="rec-hint">已关联的灵感优先展示；其余为同季节且主色相近的灵感</text>

        <text class="rec-subtitle">相关灵感</text>
        <scroll-view v-if="colorRec.matchedInspirations.length" scroll-x class="rec-scroll" :show-scrollbar="false">
          <view
            v-for="insp in colorRec.matchedInspirations"
            :key="insp.id"
            class="rec-card insp"
            @tap="goInspiration(insp.id)"
          >
            <image
              v-if="inspThumb(insp)"
              class="rec-img"
              :src="inspThumb(insp)"
              mode="aspectFill"
            />
            <view v-else class="rec-img placeholder">无图</view>
            <text class="rec-name">{{ inspDisplayName(insp) }}</text>
            <view v-if="inspLinked(insp)" class="rec-badge">已关联</view>
            <view v-if="!insp.season" class="rec-badge warn">未填季节</view>
          </view>
        </scroll-view>
        <text v-else class="rec-empty">暂无匹配灵感，请为灵感填写主色标签</text>
        <text v-if="colorRec.matchedTotal > colorRec.matchedInspirations.length" class="rec-more">
          还有 {{ colorRec.matchedTotal - colorRec.matchedInspirations.length }} 条，可在灵感库筛选查看
        </text>

        <text class="rec-subtitle section-gap">可搭配单品</text>
        <text v-if="colorRec.palette.length" class="palette-line">
          搭配色：{{ colorRec.palette.join(' · ') }}
        </text>
        <scroll-view v-if="colorRec.companions.length" scroll-x class="rec-scroll" :show-scrollbar="false">
          <view
            v-for="c in colorRec.companions"
            :key="c.id"
            class="rec-card cloth"
            @tap="goClothDetail(c.id)"
          >
            <image
              v-if="getClothImageSrc(c)"
              class="rec-img"
              :src="getClothImageSrc(c)"
              mode="aspectFill"
            />
            <view v-else class="rec-img placeholder">无图</view>
            <text class="rec-name">{{ c.name }}</text>
            <text class="rec-meta">{{ c.type }}</text>
          </view>
        </scroll-view>
        <text v-else-if="colorRec.matchedInspirations.length" class="rec-empty">
          相关灵感暂无辅色/点缀，或衣柜里还没有同搭配色的{{ item.season }}单品
        </text>
        <text v-if="colorRec.companionTotal > colorRec.companions.length" class="rec-more">
          还有 {{ colorRec.companionTotal - colorRec.companions.length }} 件可搭配
        </text>
      </view>

      <view v-else-if="!isDiscarded && colorRec.ready === false && item" class="color-rec-block muted">
        <text class="section-title">颜色搭配参考</text>
        <text class="rec-empty">请先为这件衣服填写颜色，才能匹配灵感库</text>
      </view>

      <view v-for="sec in detailSections" :key="sec.title" class="section">

        <text class="section-title">{{ sec.title }}</text>

        <view class="size-grid">

          <view

            v-for="row in sec.rows"

            :key="row.label"

            class="size-cell"

            :class="{ full: row.full }"

          >

            <text class="k">{{ row.label }}</text>

            <text class="v">{{ row.value || '-' }}</text>

          </view>

        </view>

      </view>

    </view>



    <button class="edit-btn" type="primary" @tap="onEdit">编辑</button>



    <button v-if="!isDiscarded" class="discard-btn" @tap="onMarkDiscard">标记为已扔掉</button>

    <button v-else class="restore-btn" @tap="onRestore">恢复至衣柜</button>



    <button class="del-btn" @tap="onDelete">永久删除</button>

  </view>

  <view v-else class="loading">加载中...</view>

</template>



<script setup>

import { ref, computed } from 'vue'

import { onLoad, onShow } from '@dcloudio/uni-app'

import { getClothById, getClothes, removeCloth, setClothStatus } from '../../utils/storage.js'
import { getInspirations } from '../../utils/inspirationStorage.js'

import { removeClothFromMatches } from '../../utils/matchStorage.js'

import { getClothImageSrc, getInspirationImageSrc } from '../../utils/image.js'
import { hydrateClothesImages, hydrateInspirationsImages } from '../../utils/imageStore.js'
import { buildColorRecommendations } from '../../utils/colorRecommend.js'
import { getImageLoadTick } from '../../utils/imageCache.js'

import { getItemColors, getColorHexByName } from '../../utils/color.js'

import { formatTempRange } from '../../utils/models.js'

import { isDiscarded as checkDiscarded, CLOTH_STATUS } from '../../utils/constants.js'

import { buildClothDetailSections } from '../../utils/clothFields.js'
import {
  getWearLogs,
  addWearLog,
  todayDateStr,
  isClothWornOnDate
} from '../../utils/checkinStorage.js'
import {
  countWearsForCloth,
  getLastWearDate,
  formatWearDate,
  resolveWearPeriod
} from '../../utils/wearStats.js'

const item = ref(null)
const wearCounts = ref({ c30: 0, c365: 0, cAll: 0 })
const lastWearText = ref('从未穿着')
const wornToday = ref(false)
const colorRec = ref({
  ready: false,
  matchedInspirations: [],
  companions: [],
  palette: [],
  matchedTotal: 0,
  companionTotal: 0
})

let clothId = ''



const colorTags = computed(() => getItemColors(item.value))

const imgTick = ref(0)
const imageSrc = computed(() => {
  imgTick.value
  return getClothImageSrc(item.value)
})

function colorHexFor(name) {
  return getColorHexByName(name, item.value?.colorHexMap)
}

const tempText = computed(() => formatTempRange(item.value))

const isDiscarded = computed(() => checkDiscarded(item.value))

const detailSections = computed(() => buildClothDetailSections(item.value))



onLoad((query) => {

  clothId = query.id || ''

  loadItem()

})



onShow(() => {

  if (clothId) loadItem()

})



function refreshWearStats() {
  if (!clothId) return
  const logs = getWearLogs()
  const p30 = resolveWearPeriod('30d')
  const p365 = resolveWearPeriod('365d')
  const pAll = resolveWearPeriod('all')
  wearCounts.value = {
    c30: countWearsForCloth(clothId, logs, { startMs: p30.startMs, endMs: p30.endMs }),
    c365: countWearsForCloth(clothId, logs, { startMs: p365.startMs, endMs: p365.endMs }),
    cAll: countWearsForCloth(clothId, logs, { startMs: pAll.startMs, endMs: pAll.endMs })
  }
  lastWearText.value = formatWearDate(getLastWearDate(clothId, logs))
  wornToday.value = isClothWornOnDate(clothId)
}

function onCheckinToday() {
  if (!clothId || wornToday.value) return
  addWearLog({
    date: todayDateStr(),
    type: 'single',
    clothIds: [clothId]
  })
  wornToday.value = true
  refreshWearStats()
  uni.showToast({ title: '已记录今日穿着', icon: 'success' })
}

function goWearStats() {
  uni.navigateTo({ url: '/pages/wearStats/wearStats' })
}

function refreshColorRec() {
  if (!item.value || checkDiscarded(item.value)) {
    colorRec.value = {
      ready: false,
      matchedInspirations: [],
      companions: [],
      palette: [],
      matchedTotal: 0,
      companionTotal: 0
    }
    return
  }
  colorRec.value = buildColorRecommendations(
    item.value,
    getInspirations(),
    getClothes().filter((c) => c.status !== 'discarded')
  )
}

function inspThumb(insp) {
  imgTick.value
  return getInspirationImageSrc(insp)
}

function inspDisplayName(insp) {
  return insp.name || insp.style || '灵感'
}

function inspLinked(insp) {
  return insp.links?.some((l) => l.clothId === clothId)
}

function goInspiration(id) {
  uni.navigateTo({ url: `/pages/inspirationDetail/inspirationDetail?id=${id}` })
}

function goClothDetail(id) {
  if (id === clothId) return
  uni.navigateTo({ url: `/pages/detail/detail?id=${id}` })
}

async function loadItem() {
  item.value = getClothById(clothId)
  if (!item.value) {
    uni.showToast({ title: '未找到该衣服', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
    return
  }
  const inspirations = getInspirations()
  await hydrateClothesImages(getClothes())
  await hydrateInspirationsImages(inspirations)
  imgTick.value = getImageLoadTick()
  refreshWearStats()
  refreshColorRec()
}



function previewImage() {

  const src = imageSrc.value

  if (!src) return

  uni.previewImage({ urls: [src] })

}



function onEdit() {

  uni.navigateTo({ url: `/pages/add/add?id=${clothId}` })

}



function onMarkDiscard() {

  uni.showModal({

    title: '标记为已扔掉',

    content: '将从主衣柜隐藏，可在「已扔掉」中查看尺寸等信息',

    confirmColor: '#ff2442',

    success: (res) => {

      if (!res.confirm) return

      setClothStatus(clothId, CLOTH_STATUS.DISCARDED)

      uni.showToast({ title: '已移入已扔掉', icon: 'success' })

      setTimeout(() => uni.navigateBack(), 400)

    }

  })

}



function onRestore() {

  setClothStatus(clothId, CLOTH_STATUS.ACTIVE)

  uni.showToast({ title: '已恢复至衣柜', icon: 'success' })

  loadItem()

}



function onDelete() {

  uni.showModal({

    title: '永久删除',

    content: '删除后无法恢复，相关搭配中会移除该衣服',

    confirmColor: '#ff2442',

    success: (res) => {

      if (!res.confirm) return

      removeClothFromMatches(clothId)

      removeCloth(clothId)

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

  padding-bottom: 48rpx;

}



.hero {

  width: 100%;

  display: block;

  background: #eee;

}



.hero.placeholder {

  height: 500rpx;

  display: flex;

  align-items: center;

  justify-content: center;

  color: #aaa;

  font-size: 28rpx;

}



.panel {

  margin: 24rpx;

  padding: 32rpx;

  background: #fff;

  border-radius: 20rpx;

}



.title-row {

  display: flex;

  align-items: center;

  gap: 16rpx;

  flex-wrap: wrap;

}



.title {

  font-size: 40rpx;

  font-weight: 600;

  color: #222;

}



.badge-discard {

  font-size: 22rpx;

  color: #996600;

  background: #fff8e6;

  padding: 6rpx 16rpx;

  border-radius: 8rpx;

}



.tags {

  display: flex;

  flex-wrap: wrap;

  gap: 16rpx;

  margin-top: 24rpx;

}



.tag {

  font-size: 24rpx;

  color: #666;

  background: #f5f5f5;

  padding: 8rpx 20rpx;

  border-radius: 8rpx;

}



.tag.color {

  display: flex;

  align-items: center;

  gap: 10rpx;

}



.tag.temp {

  color: #ff2442;

  background: #fff0f3;

}



.dot {

  width: 20rpx;

  height: 20rpx;

  border-radius: 50%;

  border: 1rpx solid rgba(0, 0, 0, 0.08);

}



.section {

  margin-top: 32rpx;

  padding-top: 24rpx;

  border-top: 1rpx solid #f0f0f0;

}



.wear-block {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f0f0f0;
}

.wear-grid {
  display: flex;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.wear-cell {
  flex: 1;
  background: #f9f9f9;
  border-radius: 12rpx;
  padding: 16rpx;
  text-align: center;
}

.wear-cell .k {
  display: block;
  font-size: 22rpx;
  color: #888;
  margin-bottom: 6rpx;
}

.wear-cell .v {
  font-size: 28rpx;
  font-weight: 600;
  color: #ff2442;
}

.wear-last {
  display: block;
  font-size: 26rpx;
  color: #666;
  margin-bottom: 12rpx;
}

.wear-checkin-btn {
  margin: 16rpx 0 12rpx;
  width: 100%;
  border-radius: 999rpx;
  font-size: 28rpx;
  background: #ff2442;
  color: #fff;
  border: none;
  line-height: 2.2;
}

.wear-checkin-done {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  margin: 16rpx 0 12rpx;
  padding: 18rpx 0;
  background: #f0faf0;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #2e7d32;
}

.wear-checkin-done .done-icon {
  font-weight: 700;
}

.wear-go {
  display: block;
  font-size: 26rpx;
  color: #1e88e5;
}

.color-rec-block {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f0f0f0;

  &.muted .rec-empty {
    margin-top: 8rpx;
  }
}

.rec-hint {
  display: block;
  font-size: 22rpx;
  color: #999;
  line-height: 1.5;
  margin-bottom: 16rpx;
}

.rec-subtitle {
  display: block;
  font-size: 26rpx;
  font-weight: 600;
  color: #444;
  margin-bottom: 12rpx;

  &.section-gap {
    margin-top: 24rpx;
  }
}

.palette-line {
  display: block;
  font-size: 24rpx;
  color: #666;
  margin-bottom: 12rpx;
}

.rec-scroll {
  white-space: nowrap;
  width: 100%;
  margin-bottom: 8rpx;
}

.rec-card {
  display: inline-block;
  vertical-align: top;
  width: 200rpx;
  margin-right: 16rpx;
  position: relative;
  white-space: normal;
}

.rec-img {
  width: 200rpx;
  height: 200rpx;
  border-radius: 12rpx;
  background: #f0f0f0;
  display: block;
}

.rec-img.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #ccc;
}

.rec-name {
  display: block;
  font-size: 24rpx;
  color: #333;
  margin-top: 8rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rec-meta {
  display: block;
  font-size: 22rpx;
  color: #999;
}

.rec-badge {
  position: absolute;
  top: 8rpx;
  left: 8rpx;
  font-size: 20rpx;
  padding: 4rpx 10rpx;
  border-radius: 6rpx;
  background: rgba(255, 36, 66, 0.9);
  color: #fff;

  &.warn {
    top: auto;
    bottom: 48rpx;
    background: rgba(0, 0, 0, 0.55);
  }
}

.rec-empty {
  display: block;
  font-size: 24rpx;
  color: #bbb;
  margin-bottom: 8rpx;
  line-height: 1.5;
}

.rec-more {
  display: block;
  font-size: 22rpx;
  color: #aaa;
  margin-bottom: 8rpx;
}

.section-title {

  font-size: 28rpx;

  font-weight: 600;

  color: #333;

  display: block;

  margin-bottom: 20rpx;

}



.size-grid {

  display: flex;

  flex-wrap: wrap;

  gap: 16rpx;

}



.size-cell {

  width: calc(50% - 8rpx);

  background: #f9f9f9;

  padding: 20rpx;

  border-radius: 12rpx;

  box-sizing: border-box;



  &.full {

    width: 100%;

  }

}



.k {

  display: block;

  font-size: 24rpx;

  color: #999;

}



.v {

  display: block;

  margin-top: 8rpx;

  font-size: 30rpx;

  font-weight: 600;

  color: #222;

  word-break: break-all;

}



.edit-btn {

  margin: 32rpx 24rpx 0;

  background: #ff2442;

  border-radius: 999rpx;

  font-size: 30rpx;

}



.discard-btn,

.restore-btn {

  margin: 20rpx 24rpx 0;

  background: #fff;

  color: #996600;

  border: 2rpx solid #e6c200;

  border-radius: 999rpx;

  font-size: 30rpx;

}



.restore-btn {

  color: #43a047;

  border-color: #43a047;

}



.del-btn {

  margin: 20rpx 24rpx 0;

  background: #fff;

  color: #ff2442;

  border: 2rpx solid #ff2442;

  border-radius: 999rpx;

  font-size: 30rpx;

}



.loading {

  padding: 80rpx;

  text-align: center;

  color: #999;

}

</style>

