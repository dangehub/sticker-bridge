# 表情桥 (Sticker Bridge)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

跨平台表情包管理器。点一下，发到任何地方 — Android / Windows / macOS。

## 当前状态

**v0.5** — Android 端已可日常使用：

- ✅ Eagle 图库作为数据源
- ✅ 悬浮球快速发送表情
- ✅ 微信：自动粘贴到输入框（GIF 走 Intent 跳转）
- ✅ QQ：直接打开发送给好友
- ✅ 复制到剪贴板（通用）
- ✅ 分类/标签筛选
- ✅ 搜索（名称/标签/备注）
- ✅ 输出目标开关管理
- ✅ 发送次数追踪 + 排序

## 技术栈

- **平台:** Android（Kotlin）
- **UI:** Jetpack Compose + Material 3
- **图片加载:** Coil（含 GIF 动画）
- **无障碍:** AccessibilityService
- **构建:** Gradle + AGP 8.2.2
- **数据源:** Eagle 本地图库

## 快速开始

```bash
git clone https://github.com/dangehub/sticker-bridge.git
cd sticker-bridge
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 上架状态

| 平台 | 状态 |
|------|------|
| [F-Droid](https://f-droid.org/) | 准备中 |
| [酷安](https://www.coolapk.com/) | 准备中 |

## 许可证

[Apache License 2.0](LICENSE)
