## Goal

将表情发送方式从硬编码改为配置驱动，支持通过 JSON 配置文件扩展新的发送目标。

## Background

当前 `StickerPickerActivity.shareToQQ()` 是硬编码的：
- QQ 精确跳转（已知 Activity）
- QQ 泛型 fallback
- 内置在 `QQShareHelper` 中

```
发送一个表情
  → shareToQQ(sticker)  ← 硬编码，只能发到 QQ
```

如果要支持微信、Telegram 等其他 App，需要：
- 改 `StickerPickerActivity`
- 加新 Helper 类
- 重新打包

## Design

### 抽象层：ShareTarget

```kotlin
interface ShareTarget {
    val id: String              // 唯一标识
    val displayName: String     // 展示名称
    val icon: String            // emoji 图标
    suspend fun share(context: Context, sticker: StickerItem): ShareResult
}

sealed class ShareResult {
    data object Success : ShareResult()
    data class Failed(val message: String, val canFallback: Boolean = false) : ShareResult()
}
```

### 配置格式（JSON）

```json
{
  "version": 1,
  "targets": [
    {
      "id": "qq_friend",
      "name": "QQ 好友",
      "icon": "💬",
      "type": "intent",
      "config": {
        "packageName": "com.tencent.mobileqq",
        "componentName": "com.tencent.mobileqq.activity.JumpActivity",
        "mimeType": "image/*",
        "action": "android.intent.action.SEND",
        "grantReadUriPermission": true
      }
    },
    {
      "id": "qq_general",
      "name": "QQ 分享",
      "icon": "💬",
      "type": "intent",
      "config": {
        "packageName": "com.tencent.mobileqq",
        "mimeType": "image/*",
        "action": "android.intent.action.SEND"
      }
    },
    {
      "id": "wechat",
      "name": "微信",
      "icon": "💚",
      "type": "intent",
      "config": {
        "packageName": "com.tencent.mm",
        "mimeType": "image/*",
        "action": "android.intent.action.SEND"
      }
    },
    {
      "id": "clipboard",
      "name": "复制到剪贴板",
      "icon": "📋",
      "type": "clipboard"
    }
  ]
}
```

### 加载器

```
output-targets.json  →  ShareTargetLoader
        ↑                    ↓
  用户可配置的          List<ShareTarget>
  JSON 文件               ↙    ↘    ↘
                     IntentClipboardAccessibility
                     Share    Share    Share
```

配置路径优先级：
1. 手机存储 `/sdcard/StickerBridge/output-targets.json`（用户自定义）
2. 内置 Assets `output-targets.json`（默认配置，自带 QQ + 微信 + 剪贴板）

### 与插件的关系

```
输入侧（数据来源）          输出侧（发送目标）
    插件 APK                    JSON 配置
  Eagle 插件                  QQ 好友
  NativeSource (未来)         微信
  云市场 (未来)               剪贴板
                              无障碍 (未来)
```

### UI 交互

分两种情况：

**有无障碍权限时：自动感知当前 App**
- 系统检测到 QQ 聊天界面 → 默认选中 QQ 发送，无需选择
- 检测到微信聊天 → 默认选中微信发送
- 用户可手动切换目标（顶部行）
- 无需长按和弹窗

**无无障碍权限时：手动选择**
- 顶部目标切换行（B）：点表情直接发到当前选中的目标
- 长按表情 → 弹出目标选择器（A）：列出所有可用目标供选择
- 切换行支持左右滑动查看更多目标

```
┌──────────────────────────────┐
│ 选择表情 · Eagle 图库        │
│                              │
│ [🔍 搜索表情...       ]      │
│                              │
│ [💬QQ] [💚微信] [📋剪贴板]  │ ← 目标切换行
│                              │     点击切换默认目标
│ [日常] [猫] [meme]           │     长按弹出选择器
│ [猫] [狗] [搞笑]             │
│ 🕐最新  🔥最常用  📄名称    │
│                              │
│ ┌──┐ ┌──┐ ┌──┐ ┌──┐        │
│ │图│ │图│ │图│ │图│        │
│ └──┘ └──┘ └──┘ └──┘        │
└──────────────────────────────┘
```

点击手势逻辑：

```
短按表情
  ├─ 无障碍开启 → 自动检测前台 App → 发送到对应目标
  └─ 无障碍关闭 → 发送到当前选中的默认目标

长按表情
  → 弹出目标选择器（所有可用目标）
  → 选中后发送
```

## Files affected

### 新增
- `app/src/main/java/com/example/stickerhelper/share/ShareTarget.kt`
  — 接口 + 结果 sealed class
- `app/src/main/java/com/example/stickerhelper/share/IntentShareTarget.kt`
  — Intent 类型的发送实现
- `app/src/main/java/com/example/stickerhelper/share/ClipboardShareTarget.kt`
  — 剪贴板类型的发送实现
- `app/src/main/java/com/example/stickerhelper/share/ShareTargetLoader.kt`
  — 从 JSON 加载配置，实例化 ShareTarget 列表
- `app/src/main/assets/output-targets-default.json`
  — 内置默认配置（QQ + 微信 + 剪贴板）

### 修改
- `app/src/main/java/com/example/stickerhelper/StickerPickerActivity.kt`
  — 点表情后显示目标选择器，或顶部目标切换行
- `app/build.gradle.kts`
  — 零新依赖

### 可能删除
- `QQShareHelper.kt` — 逻辑移到 IntentShareTarget 后可以删除（或者保留作为 fallback）

## Acceptance criteria

1. ✅ `output-targets-default.json` 内置配置能正确加载
2. ✅ QQ 发送通过配置驱动工作（回归测试）
3. ✅ 微信发送通过配置驱动工作
4. ✅ 复制到剪贴板功能可用
5. ✅ 用户可在手机存储上放自定义 JSON 覆盖内置配置
6. ✅ 不引新依赖
7. ✅ `./gradlew assembleDebug` 编译通过

## Out of scope

- ❌ 无障碍自动点击（未来再做）
- ❌ 发送目标管理 UI（用文件编辑器改 JSON）
- ❌ 发送历史记录
- ❌ 多目标同时发送
