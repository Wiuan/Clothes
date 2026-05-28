# 电子衣橱 · 原生 Android 版

独立 **Kotlin + Jetpack Compose** 工程，与根目录 uni-app 项目**并行**（不替换 H5 / 旧 App 基座）。

## 为何单独做原生版

HBuilder 基座上 `plus.io` / `plus.downloader` 对约 10MB 级 ZIP 写入不稳定（`status=400`、FileWriter 卡死等）。原生 Android 使用 `FileOutputStream` + `ZipOutputStream` 写入 `Download/`，与系统文件管理器行为一致。

## 环境要求

- Android Studio Ladybug / Koala 或更新
- JDK 17
- Android SDK 34

## aapt2 下载 400（Could not GET ... aapt2 ... Bad Request）

Sync 里能看到 `Wardrobe: 使用本机 AAPT2`，但 **Build 仍拉 Maven** 时，说明仅靠 `System.setProperty` 不够；AGP 8.2 只认 **`gradle.properties`** 里的：

```properties
android.aapt2FromMavenOverride=D:/你的SDK/build-tools/34.0.0/aapt2.exe
```

本工程 **Sync 时会根据 `local.properties` 的 `sdk.dir` 自动写入** 上述一行到 `gradle.properties`。

**请按顺序：**

1. **File → Sync Project with Gradle Files**（确认 `gradle.properties` 里已有 `android.aapt2FromMavenOverride=...`）
2. **Build → Clean Project** → **Rebuild Project**
3. 仍失败：**File → Invalidate Caches → Restart**；Terminal 执行 `gradle --stop` 后再 Build

## 依赖下载（其他库）

1. 若 Sync 慢：Android Studio → **Settings → Gradle**，关闭指向失效镜像的 **Init script**。
2. **File → Invalidate Caches → Restart** 后再 Sync。
3. 需要代理时让 `dl.google.com` 可访问，或用手机热点。

## Gradle 分发包下载超时（Connect timed out）

`gradle-wrapper.properties` 使用 **腾讯云** `gradle-8.2-bin.zip`。仍失败可改华为云：

`https://repo.huaweicloud.com/gradle/gradle-8.2-bin.zip`

## Android 插件找不到（Plugin com.android.application was not found）

1. 确认 `settings.gradle.kts` 含 `google()`、`gradlePluginPortal()`。
2. 关闭全局 Gradle Init 里仅指向失效镜像的脚本后 **Sync**。
3. 当前版本：**AGP 8.2.2 + Gradle 8.2**（`gradle-wrapper` 仍可用腾讯云分发 Gradle 本体）。

## 打开项目

1. Android Studio → **Open** → 选择本目录 `android-native`
2. 等待 Gradle Sync
3. 连接真机或模拟器 → Run `app`

## 功能（当前 MVP）

| 功能 | 状态 |
|------|------|
| 衣柜主界面（与 uni-app 同款布局） | ✅ |
| 添加 / 编辑衣物（尺码/买入/材质/自定义色） | ✅ |
| 详情页颜色搭配参考 | ✅ |
| 灵感筛选（风格/季节/颜色/想买） | ✅ |
| 批量编辑含适宜温度 | ✅ |
| 搭配列表与新建/编辑 | ✅ |
| 灵感列表与新建/编辑 | ✅ |
| 今日穿着、穿着统计 | ✅ |
| 已扔掉列表、批量编辑/删除 | ✅ |
| ZIP 导入导出（含穿着记录） | ✅ |

## 数据格式

与 uni-app **JSON v3 / ZIP** 包兼容（`manifest.json` + `images/*.jpg`），便于 PC 导出后拷到手机用原生 App 导入。

## 包名

`com.cloth.wardrobe`

## 与 uni-app 的关系

- **H5**：继续用仓库根目录 `npm run dev:h5`
- **旧 App 基座**：仍可调试，但 ZIP 导出建议改用本原生工程
- **新 Android 发布**：用本工程打 release APK / AAB
