# Cloth 电子衣柜（uni-app + Vue3）

## V3.0 存储与跨设备（当前）

- **元数据**（名称、颜色、搭配等）在 `localStorage`，**不含**图片 base64
- **图片**单独存放：H5 用 IndexedDB，App 用 `_doc/cloth_images/`；每张与录入时相同压缩（宽 800px、约 400KB 内）
- **列表与详情**共用同一张图
- **导出 JSON v3**：`version: 3` + `clothes` + `matches` + **`images`** 字典
- **导入**：仅接受 **version 3** 导出包；导入后衣柜、搭配可正常显示

## V2 功能

- **衣柜**：三列紧凑卡片、季节/类型/颜色/当前温度筛选
- **录入**：预设 12 色 + 自定义颜色、适宜温度区间、尺寸
- **详情**：显示温度范围（如 `15-25℃`）
- **搭配**：多件衣服组成一套，三图预览，详情查看
- **顶部切换**：衣柜 | 搭配（非底部 Tab）
- **导入导出**：JSON v3（`clothes` + `matches` + `images`）

## 路由

| 页面 | 路径 |
|------|------|
| 衣柜列表 | `pages/index/index` |
| 搭配列表 | `pages/match/match` |
| 录入 | `pages/add/add` |
| 衣服详情 | `pages/detail/detail` |
| 搭配详情/新建 | `pages/matchDetail/matchDetail` |

## 存储

| Key | 内容 |
|-----|------|
| `wardrobe_clothes_v1` | 衣物数组 |
| `wardrobe_matches_v1` | 搭配数组 |

数据结构详见 [`utils/models.js`](utils/models.js)。V3 路线图见 [`docs/V3-ROADMAP.md`](docs/V3-ROADMAP.md)。

## 跨设备备份步骤

1. 设备 A：衣柜页 → **导出** → 得到 `wardrobe_export.json`（v3，体积含全部图片）
2. 传到设备 B（微信/网盘/U 盘）
3. 设备 B：衣柜页 → **导入** → 选择该 JSON
4. 等待「导入中…」完成，列表与搭配详情应均有图

## HBuilderX 运行

1. 导入项目根目录
2. 运行到浏览器或真机
3. 顶部 **衣柜 / 搭配** 切换

## 温度筛选说明

输入「当前温度」后：已设置区间的衣服按区间匹配；**未设置温度的衣服仍会显示**，便于你发现并补全数据。
