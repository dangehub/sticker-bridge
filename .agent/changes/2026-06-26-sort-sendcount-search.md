## Goal

完善表情选择器的搜索、排序和筛选能力，让用户能高效找到想要的图片。

## Background

当前数据层已有：
- `StickerItem.modificationTime` — Eagle 元数据中的添加时间戳
- `search(query)` — 按名称/标签/备注搜索（UI placeholder 禁用中）
- `filter(folderId, tags)` — 按文件夹 + 标签筛选

当前 UI 已有：
- ✅ **文件夹筛选** — FilterChip 横向滚动，单选
- ✅ **标签筛选** — FilterChip 横向滚动，多选（AND 交集筛选）
- ❌ **搜索** — OutlinedTextField placeholder，disabled
- ❌ **排序** — 无
- ❌ **发送次数追踪** — 无

```
当前流程：
数据源 → StickerItem[]
             ↓
       folder + tags 筛选
             ↓
       展示（无序）
```

## Three new capabilities

### 1. 搜索（启用）

搜索框已留好 placeholder，需要启用并连接到 `search(query)`。
输入时实时过滤，匹配 name / annotation / tags。

### 2. 排序

用户可在表情列表中切换排序方式：

| 排序 | 依据 | 数据来源 |
|------|------|---------|
| 🕐 最新 | `modificationTime` 降序 | `StickerItem.modificationTime`（已有）|
| 🔥 最常用 | `sendCount` 降序 | `SendCountTracker`（新增）|
| 📄 名称 | `name` 升序 | `StickerItem.name`（已有）|

排序在 UI 层完成，不影响数据源。

### 3. 发送次数追踪

每次发送表情时记录发送次数，供「最常用」排序使用。

```
发送表情
  → sendTracker.increment(itemId)
  → 下次加载时，StickerItem.sendCount 被填充
  → 按发送次数排序可见
```

**存储**：SharedPreferences + JSON 映射（key = sticker.id, value = count）

## Files affected

### 新增
- `app/src/main/java/com/example/stickerhelper/SendCountTracker.kt`
  — 发送次数持久化（SharedPreferences）

### 修改
- `app/src/main/java/com/example/stickerhelper/data/StickerItem.kt`
  — 添加 `sendCount: Int = 0` 字段
- `app/src/main/java/com/example/stickerhelper/StickerPickerActivity.kt`
  — 搜索框启用 + 排序切换 + 发送计数

### 不改
- `StickerSource.kt` — 排序是 UI 层责任，不改接口
- `EagleSource.kt` — 已有 modificationTime
- `PluginStickerSource.kt` — 不变
- `PluginProtocol.kt` — 不变

## Design

### SendCountTracker

```kotlin
class SendCountTracker(context: Context) {
    fun getCount(itemId: String): Int
    fun increment(itemId: String)
    fun getAll(): Map<String, Int>
}
```

存储格式：
```json
{
  "MQURLJXZ5PX4L": 12,
  "MQURK3Z79K595": 5
}
```

### 排序逻辑（在 UI 层）

```kotlin
enum class SortMode {
    NEWEST,      // modificationTime desc
    MOST_USED,   // sendCount desc
    NAME,        // name asc
}

fun sortStickers(items: List<StickerItem>, mode: SortMode): List<StickerItem> {
    return when (mode) {
        SortMode.NEWEST -> items.sortedByDescending { it.modificationTime }
        SortMode.MOST_USED -> items.sortedByDescending { it.sendCount }
        SortMode.NAME -> items.sortedBy { it.name }
    }
}
```

### UI 变化

```
┌──────────────────────────────┐
│ 选择表情 · Eagle 图库        │
│                              │
│ [🔍 搜索表情...        ]     │ ← 启用搜索
│                              │
│ [日常] [猫] [meme]           │ ← 文件夹筛选（已有）
│ [猫] [狗] [搞笑]             │ ← 标签筛选（已有）
│                              │
│ 🕐最新  🔥最常用  📄名称    │ ← 新增排序
│                              │
│ ┌──┐ ┌──┐ ┌──┐ ┌──┐        │
│ │图│ │图│ │图│ │图│        │
│ └──┘ └──┘ └──┘ └──┘        │
└──────────────────────────────┘
```

## Acceptance criteria

1. ✅ 搜索框启用，输入关键词实时筛选（按名称/标签/备注）
2. ✅ 排序切换（最新/最常用/名称），默认「最新」
3. ✅ 发送表情后计数+1，下次打开时显示
4. ✅ 最常用排序按发送次数降序
5. ✅ 发送计数在 App 重启后保留
6. ✅ 文件夹/标签筛选与排序可同时生效
7. ✅ `./gradlew assembleDebug` 编译通过

## Out of scope

- ❌ 发送次数跨设备同步（未来再考虑）
- ❌ 发送次数按来源维度统计（stat plugin）
- ❌ 最近使用列表（未来再考虑）
- ❌ 动画 GIF 预览
