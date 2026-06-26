# 表情助手 (StickerHelper) 开发计划

> 计划生成日期：2026-06-26
> 状态：原型阶段 v0.1

---

## 一、项目概述

### 目标

跨平台（Android + Windows）表情包管理工具，读取 **Eagle** 图库作为数据源，在各聊天软件中快速发送表情。

### 核心痛点

不同软件（微信、QQ）和不同平台（Android、Windows）的表情包系统各自独立，互不互通。用户在 Eagle 中管理表情包，但发送时需要手动翻找。

### 哲学

- Eagle 是「管理端」— 增删改、打标签
- 本工具是「消费端」— 只读读取、快速发送
- 同步由 Syncthing 处理，本工具不涉足

---

## 二、架构

### 数据流

```
Eagle 图库 (Windows)
    │
    ▼ Syncthing 自动同步
    │
Eagle 图库副本 (Android)
    │
    ▼ 读取 metadata.db + images/
    │
表情助手 App (Android)
    │
    ├── 微信 → 文件路径直接发送
    ├── QQ → 精确 Activity 跳转（发送给好友）
    └── 其他 → 系统分享 + 无障碍加速
```

### 技术栈

| 层面 | 方案 |
|------|------|
| 平台 | Android (最低 API 26) |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 图片 | Coil |
| 无障碍 | AccessibilityService |
| 数据 | Android SQLite (读 Eagle) |
| 构建 | Gradle + AGP 8.2.2 |

---

## 三、原型阶段 (v0.1)

### 范围

> 先跑通最小闭环，验证可行性，不追求完美。

**已实现：**

- [x] 无障碍服务检测 QQ 聊天界面
- [x] 悬浮球浮动按钮（可拖动、吸附边缘）
- [x] 点击悬浮球 → 弹出表情选择器（Compose Dialog）
- [x] 选择表情 → 精确跳转 QQ「发送给好友」Activity
- [x] Fallback：精确跳转失败时走包名分享 + QQ 内部面板
- [x] 占位表情自动生成（8 张 emoji 带色背景）
- [x] 权限引导页（悬浮窗 + 无障碍 + 通知）

### 遇到的问题 & 解决

| 问题 | 方案 |
|------|------|
| 点击悬浮球无反应 | OnTouchListener 消耗了事件，改为 Action_UP 判断点击/拖动 |
| 提示"未安装 QQ" | Android 11+ 包可见性限制，添加 `<queries>` 声明 |
| QQ 分享出 4 个选项面板 | 用 PackageManager 查找「发送给好友」Activity 精确跳转 |

### 代码结构

```
StickerHelper/
├── app/src/main/java/com/example/stickerhelper/
│   ├── MainActivity.kt                  # 权限引导页
│   ├── BubbleService.kt                 # 悬浮球前台服务
│   ├── QQDetectAccessibilityService.kt  # QQ 聊天检测 + 自动发送
│   ├── StickerPickerActivity.kt         # 表情选择器 (Compose)
│   ├── StickerRepository.kt            # 表情数据源（占位图 → Eagle）
│   └── QQShareHelper.kt                # QQ 分享 Intent 工具
├── .hermes/plans/                       # 开发计划
├── CONTRIBUTING.md                      # 贡献指南（含Commit规范）
└── README.md
```

---

## 四、后续阶段

### v0.2 — Eagle 数据源 + 搜索

- [ ] 解析 Eagle `metadata.db`（SQLite）
- [ ] 按分类（folder）浏览表情
- [ ] 按标签（tag）筛选
- [ ] 搜索（文件名 + 标签 + 备注）
- [ ] 替换占位图

### v0.3 — 微信适配

- [ ] Android 微信文件路径发送
- [ ] 多图发送
- [ ] 常用/最近使用

### v0.4 — 体验优化

- [ ] 无障碍自动发送（点击「发送」按钮）
- [ ] 悬浮球自动贴边隐藏
- [ ] 批量管理

### v1.0 — Windows 端

- [ ] 托盘程序
- [ ] 剪贴板方式发送
- [ ] Eagle 原生集成

---

## 五、Commit 规范

本仓库采用 Conventional Commits：

```
feat(share): locate QQ send-to-friend activity via PackageManager

English description here.

中文说明。
```

详见 `CONTRIBUTING.md`。

---

## 六、验证标准

1. APK 安装后可正常启动引导页
2. 三个权限开启后悬浮球出现
3. 进入 QQ 聊天 → 悬浮球自动显示
4. 点击悬浮球 → 选表情 → 直接打开 QQ 好友选择器
5. 选择好友 → 发送成功

---

*此文件随项目进展持续更新。*
