# 表情助手 (StickerHelper)

跨平台表情包管理工具 — 读取 Eagle 图库，在 Android / Windows 上快速发送表情到微信、QQ 等聊天软件。

## 项目状态

**原型阶段 v0.1** — 仅实现 Android 端：
- [x] 无障碍服务检测 QQ 聊天界面
- [x] 悬浮球浮动按钮
- [x] 点击悬浮球 → 选择表情 → 分享到 QQ
- [x] 精确跳转 QQ「发送给好友」Activity
- [ ] 读取 Eagle 图库（`metadata.db` + `images/`）
- [ ] 分类 / 标签筛选
- [ ] 搜索
- [ ] 微信适配
- [ ] Windows 端

## 技术栈

- **平台：** Android
- **语言：** Kotlin
- **UI：** Jetpack Compose + Material 3
- **图片加载：** Coil
- **无障碍：** AccessibilityService
- **构建：** Gradle + AGP 8.2.2
- **数据源：** Eagle SQLite + 图片文件（规划中）

## 快速开始

```bash
git clone <repo-url>
cd StickerHelper
./gradlew assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk
```
