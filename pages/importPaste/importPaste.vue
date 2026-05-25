<template>
  <view class="page">
    <text class="tip">将电脑上导出的 wardrobe_export.json 全文粘贴到下方，然后点「开始导入」。</text>
    <textarea
      v-model="text"
      class="textarea"
      placeholder="粘贴 JSON 内容…"
      :maxlength="-1"
      :auto-height="false"
    />
    <button class="btn primary" type="primary" :disabled="importing" @tap="onImport">
      {{ importing ? '导入中…' : '开始导入' }}
    </button>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { importJsonFromText } from '../../utils/io.js'

const text = ref('')
const importing = ref(false)

async function onImport() {
  const raw = text.value.trim()
  if (!raw) {
    uni.showToast({ title: '请先粘贴内容', icon: 'none' })
    return
  }
  importing.value = true
  try {
    const result = await importJsonFromText(raw)
    const clothCount = Array.isArray(result.clothes) ? result.clothes.length : 0
    const parts = [`${clothCount} 件`]
    if (result.matchCount) parts.push(`${result.matchCount} 套搭配`)
    if (result.inspirationCount) parts.push(`${result.inspirationCount} 条灵感`)
    uni.showToast({ title: `已导入 ${parts.join('、')}`, icon: 'success' })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
  } catch {
    /* io 内已提示 */
  } finally {
    importing.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: #f7f7f8;
  box-sizing: border-box;
}

.tip {
  display: block;
  font-size: 26rpx;
  color: #666;
  line-height: 1.5;
  margin-bottom: 20rpx;
}

.textarea {
  width: 100%;
  height: 60vh;
  padding: 20rpx;
  background: #fff;
  border-radius: 12rpx;
  font-size: 24rpx;
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

.btn {
  border-radius: 12rpx;
}
</style>
