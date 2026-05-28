<template>
  <view class="page">
    <template v-if="isCreate || isEdit">
      <view class="photo-box" @tap="choosePhoto">
        <image v-if="imageBase64" class="preview" :src="imageBase64" mode="aspectFit" />
        <view v-else class="placeholder">
          <text class="icon">📷</text>
          <text class="tip">{{ isEdit ? '点击更换参考图' : '从相册选择穿搭图' }}</text>
        </view>
      </view>

      <view class="form">
        <view class="field">
          <text class="label">标题</text>
          <input
            v-model="form.name"
            class="input"
            type="text"
            placeholder="选填，如：通勤参考"
            maxlength="30"
            :adjust-position="true"
            :cursor-spacing="120"
          />
        </view>

        <view class="field">
          <text class="label">主色</text>
          <text class="hint inline">可多选</text>
          <view class="chips">
            <view
              v-for="c in tagColors"
              :key="'p' + c"
              class="chip"
              :class="{ active: isTagSelected('primary', c) }"
              @tap="toggleTag('primary', c)"
            >
              <view class="dot" :style="{ background: COLOR_HEX[c] }" />
              <text>{{ c }}</text>
            </view>
          </view>
        </view>

        <view class="field">
          <text class="label">辅色</text>
          <view class="chips">
            <view
              v-for="c in tagColors"
              :key="'s' + c"
              class="chip"
              :class="{ active: isTagSelected('secondary', c) }"
              @tap="toggleTag('secondary', c)"
            >
              <view class="dot" :style="{ background: COLOR_HEX[c] }" />
              <text>{{ c }}</text>
            </view>
          </view>
        </view>

        <view class="field">
          <text class="label">点缀色</text>
          <view class="chips">
            <view
              v-for="c in tagColors"
              :key="'a' + c"
              class="chip"
              :class="{ active: isTagSelected('accent', c) }"
              @tap="toggleTag('accent', c)"
            >
              <view class="dot" :style="{ background: COLOR_HEX[c] }" />
              <text>{{ c }}</text>
            </view>
          </view>
        </view>

        <view class="field">
          <text class="label">风格</text>
          <view class="chips row">
            <view
              v-for="s in INSPIRATION_STYLES"
              :key="s"
              class="chip text-only"
              :class="{ active: form.style === s }"
              @tap="form.style = form.style === s ? '' : s"
            >
              {{ s }}
            </view>
          </view>
        </view>

        <view class="field">
          <text class="label">季节</text>
          <view class="chips row">
            <view
              v-for="s in SEASONS"
              :key="s"
              class="chip text-only"
              :class="{ active: form.season === s }"
              @tap="form.season = form.season === s ? '' : s"
            >
              {{ s }}
            </view>
          </view>
        </view>

        <view class="field">
          <text class="label">场合</text>
          <input
            v-model="form.occasion"
            class="input"
            type="text"
            placeholder="选填，如：上班、周末出游"
            maxlength="30"
            :adjust-position="true"
            :cursor-spacing="120"
          />
        </view>

        <view class="field">
          <text class="label">备注</text>
          <textarea
            v-model="form.note"
            class="textarea"
            placeholder="选填"
            maxlength="200"
            :adjust-position="true"
            :cursor-spacing="120"
            :auto-height="false"
          />
        </view>

        <button class="btn primary save-bar" @tap="onSaveForm">
          {{ isEdit ? '保存修改' : '保存灵感' }}
        </button>

        <text class="label section">关联衣柜</text>
        <text class="hint block">选中后点击标签切换「我有类似」/「想买」</text>

        <view class="cloth-filter-row">
          <picker class="filter-picker" :range="clothSeasonOpts" :value="clothSeasonIdx" @change="onClothSeasonPick">
            <view class="chip" :class="{ active: !!clothFilterSeason }">{{ clothSeasonLabel }} ▾</view>
          </picker>
          <picker class="filter-picker" :range="clothTypeOpts" :value="clothTypeIdx" @change="onClothTypePick">
            <view class="chip" :class="{ active: !!clothFilterType }">{{ clothTypeLabel }} ▾</view>
          </picker>
          <picker class="filter-picker" :range="clothColorOpts" :value="clothColorIdx" @change="onClothColorPick">
            <view class="chip" :class="{ active: !!clothFilterColor }">{{ clothColorLabel }} ▾</view>
          </picker>
          <text
            v-if="clothFilterSeason || clothFilterType || clothFilterColor"
            class="filter-reset"
            @tap="resetClothFilter"
          >
            重置
          </text>
          <text class="filter-stats">
            已选{{ linkCount }}·{{ filteredClothes.length }}/{{ allClothes.length }}
          </text>
        </view>

        <view class="pick-grid">
          <view
            v-for="c in filteredClothes"
            :key="c.id"
            class="pick-item"
            :class="{ selected: linkMap[c.id] }"
            @tap="toggleLink(c.id)"
          >
            <image
              v-if="getClothImageSrc(c)"
              class="pick-img"
              :src="getClothImageSrc(c)"
              mode="aspectFill"
            />
            <view v-else class="pick-img placeholder">无图</view>
            <text class="pick-name">{{ c.name }}</text>
            <view
              v-if="linkMap[c.id]"
              class="relation-tag"
              @tap.stop="toggleRelation(c.id)"
            >
              {{ LINK_RELATION_LABEL[linkMap[c.id]] }}
            </view>
          </view>
        </view>
      </view>
    </template>

    <swiper
      v-else-if="inspiration && orderedIds.length"
      class="detail-swiper"
      :current="pagerIndex"
      :disable-touch="orderedIds.length <= 1"
      @change="onInspPagerChange"
    >
      <swiper-item v-for="(id, idx) in orderedIds" :key="id">
        <scroll-view v-if="idx === pagerIndex && inspiration" scroll-y class="page">
      <view class="hero">
        <image
          v-if="heroSrc"
          class="hero-img"
          :src="heroSrc"
          mode="widthFix"
        />
        <view v-else class="hero-img placeholder">暂无图</view>
      </view>

      <view class="detail-block">
        <text class="title">{{ inspiration.name || inspiration.style || '灵感' }}</text>
        <view v-if="tagRows.length" class="tag-section">
          <view v-for="row in tagRows" :key="row.label" class="tag-row">
            <text class="tag-label">{{ row.label }}</text>
            <view class="tag-chips">
              <view
                v-for="c in row.colors"
                :key="c"
                class="tag-chip"
              >
                <view class="dot" :style="{ background: COLOR_HEX[c] || '#9e9e9e' }" />
                <text>{{ c }}</text>
              </view>
            </view>
          </view>
        </view>
        <text v-if="inspiration.style" class="line">风格：{{ inspiration.style }}</text>
        <text v-if="inspiration.season" class="line">季节：{{ inspiration.season }}</text>
        <text v-if="inspiration.occasion" class="line">场合：{{ inspiration.occasion }}</text>
        <text v-if="inspiration.note" class="note">{{ inspiration.note }}</text>
      </view>

      <view v-if="linkedClothes.length" class="links-block">
        <text class="section-title">关联单品</text>
        <view
          v-for="entry in linkedClothes"
          :key="entry.cloth.id"
          class="link-item"
          @tap="goClothDetail(entry.cloth.id)"
        >
          <image
            v-if="getClothImageSrc(entry.cloth)"
            class="link-img"
            :src="getClothImageSrc(entry.cloth)"
            mode="aspectFill"
          />
          <view v-else class="link-img placeholder">无图</view>
          <view class="link-info">
            <text class="link-name">{{ entry.cloth.name }}</text>
            <text class="link-rel" :class="entry.relation">{{ LINK_RELATION_LABEL[entry.relation] }}</text>
          </view>
        </view>
      </view>

      <button class="btn primary outline" @tap="enterEdit">编辑</button>
      <button class="btn del" @tap="onDelete">删除这条灵感</button>
        </scroll-view>
      </swiper-item>
    </swiper>

    <view v-else class="loading">加载中...</view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import {
  PRESET_COLORS,
  SEASONS,
  TYPES,
  COLOR_HEX,
  INSPIRATION_STYLES,
  LINK_RELATION_LABEL
} from '../../utils/constants.js'
import { getClothes } from '../../utils/storage.js'
import { filterClothes } from '../../utils/filter.js'
import {
  getInspirations,
  getInspirationById,
  addInspiration,
  updateInspiration,
  removeInspiration
} from '../../utils/inspirationStorage.js'
import { pickImageAsBase64, getClothImageSrc, getInspirationImageSrc } from '../../utils/image.js'
import {
  saveInspirationImage,
  deleteImage,
  inspirationImageRef,
  hydrateInspirationsImages,
  hydrateClothesImages
} from '../../utils/imageStore.js'
import { getImageLoadTick } from '../../utils/imageCache.js'
import { getInspirationBrowseIds } from '../../utils/browseOrder.js'

const tagColors = PRESET_COLORS.filter((c) => c !== '其他')
const CLOTH_ALL = '全部'
const clothSeasonOpts = [CLOTH_ALL, ...SEASONS]
const clothTypeOpts = [CLOTH_ALL, ...TYPES]
const clothColorOpts = [CLOTH_ALL, ...PRESET_COLORS]

const clothFilterSeason = ref('')
const clothFilterType = ref('')
const clothFilterColor = ref('')

const isCreate = ref(false)
const isEdit = ref(false)
const inspiration = ref(null)
const editId = ref('')
const orderedIds = ref([])
const pagerIndex = ref(0)
const imageBase64 = ref('')
const imageChanged = ref(false)

const form = ref({
  name: '',
  style: '',
  season: '',
  occasion: '',
  note: '',
  colorTags: {
    primary: [],
    secondary: [],
    accent: []
  }
})

const linkMap = ref({})

const imgTick = computed(() => getImageLoadTick())
const heroSrc = computed(() => {
  imgTick.value
  return inspiration.value ? getInspirationImageSrc(inspiration.value) : ''
})

const linkCount = computed(() => Object.keys(linkMap.value).length)

const allClothes = computed(() => getClothes().filter((c) => c.status !== 'discarded'))

const clothSeasonIdx = computed(() =>
  clothFilterSeason.value ? clothSeasonOpts.indexOf(clothFilterSeason.value) : 0
)
const clothTypeIdx = computed(() =>
  clothFilterType.value ? clothTypeOpts.indexOf(clothFilterType.value) : 0
)
const clothColorIdx = computed(() =>
  clothFilterColor.value ? clothColorOpts.indexOf(clothFilterColor.value) : 0
)
const clothSeasonLabel = computed(() => clothFilterSeason.value || CLOTH_ALL)
const clothTypeLabel = computed(() => clothFilterType.value || CLOTH_ALL)
const clothColorLabel = computed(() => clothFilterColor.value || CLOTH_ALL)

const filteredClothes = computed(() =>
  filterClothes(allClothes.value, {
    season: clothFilterSeason.value || undefined,
    type: clothFilterType.value || undefined,
    color: clothFilterColor.value || undefined
  })
)

function onClothSeasonPick(e) {
  const v = clothSeasonOpts[e.detail.value]
  clothFilterSeason.value = v === CLOTH_ALL ? '' : v
}
function onClothTypePick(e) {
  const v = clothTypeOpts[e.detail.value]
  clothFilterType.value = v === CLOTH_ALL ? '' : v
}
function onClothColorPick(e) {
  const v = clothColorOpts[e.detail.value]
  clothFilterColor.value = v === CLOTH_ALL ? '' : v
}
function resetClothFilter() {
  clothFilterSeason.value = ''
  clothFilterType.value = ''
  clothFilterColor.value = ''
}

const tagRows = computed(() => {
  if (!inspiration.value) return []
  const t = inspiration.value.colorTags || {}
  const rows = []
  if (t.primary?.length) rows.push({ label: '主色', colors: t.primary })
  if (t.secondary?.length) rows.push({ label: '辅色', colors: t.secondary })
  if (t.accent?.length) rows.push({ label: '点缀', colors: t.accent })
  return rows
})

const linkedClothes = computed(() => {
  if (!inspiration.value) return []
  const all = getClothes()
  return (inspiration.value.links || [])
    .map((l) => {
      const cloth = all.find((c) => c.id === l.clothId)
      if (!cloth) return null
      return { cloth, relation: l.relation }
    })
    .filter(Boolean)
})

onLoad((query) => {
  if (query.mode === 'create') {
    isCreate.value = true
    uni.setNavigationBarTitle({ title: '添加灵感' })
    return
  }
  if (query.id) {
    editId.value = query.id
    loadInspiration(query.id)
  }
})

onShow(async () => {
  await hydrateClothesImages(getClothes())
  if (inspiration.value) {
    await hydrateInspirationsImages([inspiration.value])
  }
})

function buildInspBrowseIds() {
  const cached = getInspirationBrowseIds(editId.value)
  if (cached) {
    orderedIds.value = cached
    pagerIndex.value = cached.indexOf(editId.value)
    return
  }
  orderedIds.value = getInspirations()
    .slice()
    .sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0))
    .map((i) => i.id)
  const idx = orderedIds.value.indexOf(editId.value)
  pagerIndex.value = idx >= 0 ? idx : 0
}

function updateInspNavTitle() {
  if (isCreate.value || isEdit.value) return
  if (orderedIds.value.length <= 1) {
    uni.setNavigationBarTitle({ title: '灵感详情' })
    return
  }
  uni.setNavigationBarTitle({
    title: `灵感详情 (${pagerIndex.value + 1}/${orderedIds.value.length})`
  })
}

function onInspPagerChange(e) {
  const idx = e.detail.current
  if (idx === pagerIndex.value) return
  pagerIndex.value = idx
  const id = orderedIds.value[idx]
  if (id) loadInspiration(id, false)
}

async function loadInspiration(id, resetPager = true) {
  editId.value = id
  const item = getInspirationById(id)
  if (!item) {
    orderedIds.value = []
    uni.showToast({ title: '灵感不存在', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 800)
    return
  }
  inspiration.value = item
  await hydrateInspirationsImages([item])
  if (resetPager) buildInspBrowseIds()
  else {
    const idx = orderedIds.value.indexOf(id)
    if (idx >= 0) pagerIndex.value = idx
  }
  updateInspNavTitle()
}

function enterEdit() {
  if (!inspiration.value) return
  isEdit.value = true
  isCreate.value = false
  editId.value = inspiration.value.id
  imageBase64.value = getInspirationImageSrc(inspiration.value)
  imageChanged.value = false
  const t = inspiration.value.colorTags || {}
  form.value = {
    name: inspiration.value.name || '',
    style: inspiration.value.style || '',
    season: inspiration.value.season || '',
    occasion: inspiration.value.occasion || '',
    note: inspiration.value.note || '',
    colorTags: {
      primary: [...(t.primary || [])],
      secondary: [...(t.secondary || [])],
      accent: [...(t.accent || [])]
    }
  }
  const map = {}
  for (const l of inspiration.value.links || []) {
    map[l.clothId] = l.relation === 'want_to_buy' ? 'want_to_buy' : 'have_similar'
  }
  linkMap.value = map
  uni.setNavigationBarTitle({ title: '编辑灵感' })
}

function isTagSelected(group, color) {
  return (form.value.colorTags[group] || []).includes(color)
}

function toggleTag(group, color) {
  const arr = [...(form.value.colorTags[group] || [])]
  const i = arr.indexOf(color)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(color)
  form.value.colorTags[group] = arr
}

function toggleLink(clothId) {
  const map = { ...linkMap.value }
  if (map[clothId]) delete map[clothId]
  else map[clothId] = 'have_similar'
  linkMap.value = map
}

function toggleRelation(clothId) {
  const r = linkMap.value[clothId]
  if (!r) return
  linkMap.value = {
    ...linkMap.value,
    [clothId]: r === 'want_to_buy' ? 'have_similar' : 'want_to_buy'
  }
}

function pickImageFrom(sourceType) {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType,
    success: async (res) => {
      const path = res.tempFilePaths[0]
      try {
        uni.showLoading({ title: '处理中...', mask: true })
        imageBase64.value = await pickImageAsBase64(path)
        imageChanged.value = true
      } catch (e) {
        const msg = e && e.message ? String(e.message) : ''
        uni.showToast({
          title: msg && msg.length < 24 ? msg : '图片处理失败，请换一张或重试',
          icon: 'none'
        })
      } finally {
        uni.hideLoading()
      }
    },
    fail: (err) => {
      const msg = err && err.errMsg ? String(err.errMsg) : ''
      if (/camera|Camera|模块/.test(msg)) {
        uni.showToast({
          title: '拍照需含 Camera 模块的自定义基座，请用相册或重新打包',
          icon: 'none',
          duration: 3200
        })
      } else if (!msg.includes('cancel')) {
        uni.showToast({ title: '选择图片失败', icon: 'none' })
      }
    }
  })
}

function choosePhoto() {
  uni.showActionSheet({
    itemList: ['从相册选择', '拍照'],
    success: (res) => {
      pickImageFrom(res.tapIndex === 1 ? ['camera'] : ['album'])
    }
  })
}

function buildLinks() {
  return Object.entries(linkMap.value).map(([clothId, relation]) => ({
    clothId,
    relation: relation === 'want_to_buy' ? 'want_to_buy' : 'have_similar'
  }))
}

async function onSaveForm() {
  if (!imageBase64.value && !isEdit.value) {
    uni.showToast({ title: '请选择参考图', icon: 'none' })
    return
  }

  const id = isCreate.value ? `insp_${Date.now()}` : editId.value
  let imageRef = inspiration.value?.imageRef || inspirationImageRef(id)

  uni.showLoading({ title: '保存中...', mask: true })
  try {
  if (imageChanged.value && imageBase64.value) {
    const saved = await saveInspirationImage(id, imageBase64.value)
    imageRef = saved.imageRef
  }

  const payload = {
    id,
    name: form.value.name.trim(),
    imageRef,
    colorTags: {
      primary: [...form.value.colorTags.primary],
      secondary: [...form.value.colorTags.secondary],
      accent: [...form.value.colorTags.accent]
    },
    style: form.value.style,
    season: form.value.season,
    occasion: form.value.occasion.trim(),
    note: form.value.note.trim(),
    links: buildLinks(),
    createdAt: inspiration.value?.createdAt || Date.now(),
    source: 'album'
  }

  if (isCreate.value) {
    addInspiration(payload)
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } else {
    updateInspiration(payload)
    isEdit.value = false
    inspiration.value = getInspirationById(id)
    await hydrateInspirationsImages([inspiration.value])
    uni.setNavigationBarTitle({ title: '灵感详情' })
    uni.showToast({ title: '已更新', icon: 'success' })
  }
  } catch (e) {
    const msg = e && e.message ? String(e.message) : ''
    uni.showToast({
      title: msg && msg.length < 24 ? msg : '保存失败，请重试',
      icon: 'none'
    })
  } finally {
    uni.hideLoading()
  }
}

function goClothDetail(id) {
  uni.navigateTo({ url: `/pages/detail/detail?id=${id}` })
}

function onDelete() {
  uni.showModal({
    title: '删除灵感',
    content: '确定删除这条灵感？图片也会移除。',
    success: async (res) => {
      if (!res.confirm || !inspiration.value) return
      const ref = inspiration.value.imageRef || inspirationImageRef(inspiration.value.id)
      await deleteImage(ref)
      removeInspiration(inspiration.value.id)
      uni.showToast({ title: '已删除', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 500)
    }
  })
}
</script>

<style lang="scss" scoped>
.detail-swiper {
  height: 100vh;
  width: 100%;
}

.page {
  min-height: 100vh;
  background: #f7f7f8;
  padding-bottom: 48rpx;
  box-sizing: border-box;
}

.photo-box {
  width: 100%;
  height: 200rpx;
  background: #f7f7f8;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview {
  width: 100%;
  height: 100%;
}

.placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
}

.icon {
  font-size: 64rpx;
}

.tip {
  margin-top: 16rpx;
  font-size: 28rpx;
}

.form {
  padding: 24rpx;
  background: #fff;
}

.field {
  margin-bottom: 28rpx;
}

.label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 12rpx;
}

.label.section {
  margin-top: 16rpx;
}

.hint {
  font-size: 24rpx;
  color: #999;

  &.inline {
    margin-left: 12rpx;
    font-weight: normal;
  }

  &.block {
    display: block;
    margin-bottom: 16rpx;
  }
}

.input {
  display: block;
  width: 100%;
  height: 88rpx;
  min-height: 88rpx;
  line-height: 48rpx;
  font-size: 28rpx;
  padding: 20rpx 24rpx;
  background: #f7f7f8;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.textarea {
  display: block;
  width: 100%;
  height: 200rpx;
  min-height: 200rpx;
  line-height: 1.5;
  font-size: 28rpx;
  padding: 20rpx 24rpx;
  background: #f7f7f8;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: #f5f5f5;
  border-radius: 32rpx;
  font-size: 26rpx;
  color: #555;
  border: 2rpx solid transparent;

  &.active {
    border-color: #ff2442;
    background: #fff5f6;
    color: #ff2442;
  }

  &.text-only {
    padding: 12rpx 28rpx;
  }
}

.dot {
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
}

.pick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.pick-item {
  position: relative;
  background: #f7f7f8;
  border-radius: 12rpx;
  overflow: hidden;
  border: 2rpx solid transparent;

  &.selected {
    border-color: #ff2442;
  }
}

.pick-img {
  width: 100%;
  height: 160rpx;
  display: block;
  background: #eee;
}

.pick-img.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  color: #bbb;
}

.pick-name {
  display: block;
  font-size: 22rpx;
  padding: 8rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.relation-tag {
  position: absolute;
  bottom: 36rpx;
  left: 8rpx;
  right: 8rpx;
  text-align: center;
  font-size: 20rpx;
  background: rgba(255, 36, 66, 0.9);
  color: #fff;
  padding: 4rpx 0;
  border-radius: 6rpx;
}

.hero {
  background: #fff;
}

.hero-img {
  width: 100%;
  display: block;
}

.hero-img.placeholder {
  height: 400rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #bbb;
  background: #f0f0f0;
}

.detail-block {
  background: #fff;
  padding: 24rpx;
  margin-top: 16rpx;
}

.title {
  font-size: 36rpx;
  font-weight: 600;
  color: #222;
}

.tag-section {
  margin-top: 20rpx;
}

.tag-row {
  margin-bottom: 12rpx;
}

.tag-label {
  font-size: 24rpx;
  color: #888;
  margin-right: 12rpx;
}

.tag-chips {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 8rpx;
}

.tag-chip {
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: 26rpx;
  background: #f5f5f5;
  padding: 6rpx 16rpx;
  border-radius: 24rpx;
}

.line {
  display: block;
  margin-top: 12rpx;
  font-size: 28rpx;
  color: #555;
}

.note {
  display: block;
  margin-top: 16rpx;
  font-size: 28rpx;
  color: #666;
  line-height: 1.5;
}

.links-block {
  background: #fff;
  margin-top: 16rpx;
  padding: 24rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
  display: block;
}

.link-item {
  display: flex;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f0f0;
}

.link-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
  background: #f0f0f0;
}

.link-rel {
  font-size: 24rpx;
  color: #43a047;

  &.want_to_buy {
    color: #ff2442;
  }
}

.btn {
  margin: 24rpx;
  border-radius: 12rpx;
}

.btn.primary {
  background: #ff2442;
  color: #fff;
}

.save-bar {
  margin: 16rpx 0 20rpx;
  width: 100%;
  box-sizing: border-box;
}

.cloth-filter-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 10rpx;
  flex-wrap: nowrap;
}

.filter-picker {
  flex: 0 0 auto;
}

.chip {
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

.filter-reset {
  font-size: 20rpx;
  color: #ff2442;
  flex-shrink: 0;
}

.filter-stats {
  margin-left: auto;
  font-size: 20rpx;
  color: #999;
  white-space: nowrap;
  flex-shrink: 0;
}

.btn.outline {
  background: #fff;
  color: #ff2442;
  border: 1rpx solid #ff2442;
}

.btn.del {
  background: #fff;
  color: #e53935;
  border: 1rpx solid #eee;
}

.loading {
  padding: 80rpx;
  text-align: center;
  color: #999;
}
</style>
