# Sticker Bridge 开发计划

> 计划生成日期：2026-06-26
> 状态：原型阶段 v0.1

---

## 一、项目概述

### 目标

全平台（Android、Windows、macOS、iOS）表情包管理工具，从多种图片来源读取表情，在各聊天软件中快速发送。

### 核心痛点

不同软件（微信、QQ、iMessage 等）和不同平台的表情包系统各自独立，互不互通。用户需要在多个地方重复管理同一套表情。

### 哲学

- 图片来源多样（Eagle 为第一站，后续可扩展）
- 同步由 Syncthing 等外部工具处理，本工具不涉足
- 核心交互：**点一下就能发**

---

## 二、原型阶段 (v0.1)

### 范围

> 先跑通最小闭环，验证可行性。

**已实现：**

- [x] 无障碍服务检测 QQ 聊天界面
- [x] 悬浮球浮动按钮（可拖动、吸附边缘）
- [x] 点击悬浮球 → 弹出表情选择器（Compose Dialog）
- [x] 选择表情 → 精确跳转 QQ「发送给好友」Activity
- [x] Fallback：精确跳转失败时走包名分享
- [x] 占位表情自动生成（8 张 emoji 带色背景）
- [x] 权限引导页（悬浮窗 + 无障碍 + 通知）

### 已知问题 & 解决

| 问题 | 方案 |
|------|------|
| 点击悬浮球无反应 | OnTouchListener 吞了事件 → 改为在 ACTION_UP 判断点击/拖动 |
| 提示"未安装 QQ" | Android 11+ 包可见性限制 → 添加 `<queries>` 声明 |
| QQ 内部分享有 4 个选项 | PackageManager 查找「发送给好友」Activity 精确跳转 |

### 代码结构

```
sticker-bridge/
├── app/src/main/java/com/example/stickerhelper/
│   ├── MainActivity.kt                  # 权限引导
│   ├── BubbleService.kt                 # 悬浮球服务
│   ├── QQDetectAccessibilityService.kt  # QQ 聊天检测
│   ├── StickerPickerActivity.kt         # 表情选择器
│   ├── StickerRepository.kt            # 数据源抽象
│   └── QQShareHelper.kt                # QQ 分享
├── .agent/changes/                      # OpenSpec proposals
├── .hermes/plans/
├── CONTRIBUTING.md
└── README.md
```

---

## 三、后续阶段

### v0.2 — 真正数据源 + 筛选搜索
- [ ] 对接 Eagle `metadata.db`（SQLite）
- [ ] 按分类浏览
- [ ] 按标签筛选
- [ ] 搜索（名称 + 标签 + 备注）
- [ ] 图片来源抽象层（Eagle 只是第一个 provider）

### v0.3 — 更多平台
- [ ] Android 微信发送
- [ ] Windows 剪贴板发送
- [ ] 无障碍自动点发送按钮
- [ ] 最近使用 / 常用

### v0.4 — 体验完善
- [ ] 悬浮球自动贴边隐藏
- [ ] 批量选择
- [ ] 自定义收藏

### v1.0 — 全平台
- [ ] macOS 适配
- [ ] iOS 适配
- [ ] 平台间同步策略完善

---

## 四、Commit 规范

遵循 Conventional Commits，详见 `CONTRIBUTING.md`。

## 五、开发流程

所有编程任务走 **OpenSpec → Superpowers → gstack**：

1. **OpenSpec** — 在 `.agent/changes/` 下写 proposal，记录目标、涉及文件、边界条件、验收标准、风险
2. **Superpowers** — 按纪律执行：不改接口签名、写逻辑层测试、不改范围外文件
3. **gstack** — 编译 → 用户确认 → 自动 commit（按逻辑拆分）

---

*此文件随项目进展持续更新。*
