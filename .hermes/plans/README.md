# Sticker Bridge 开发计划

> 计划更新：2026-06-26
> 当前版本：v0.2 — Eagle 数据源 + 插件原型

---

## 一、项目概述

### 目标

全平台（Android、Windows、macOS、iOS）表情包管理工具，从多种图片来源读取表情，在各聊天软件中快速发送。

### 核心痛点

不同软件（微信、QQ、iMessage 等）和不同平台的表情包系统各自独立，互不互通。用户需要在多个地方重复管理同一套表情。

### 哲学

- 图片来源多样：通过 **StickerSource** 接口抽象 + **插件系统** 扩展
- 同步由 Syncthing 等外部工具处理，本工具不涉足
- 核心交互：**点一下就能发**

### 架构决策

| 决策 | 结论 | 原因 |
|------|------|------|
| Eagle CRUD | ❌ **不做** | Eagle 桌面端独占库写入权，Android 端写入可能破坏格式或产生冲突 |
| 插件系统 | ✅ **多 APK + Binder IPC** | 新来源 = 装个新 APK，主 App 不更新。支持第三方开发 |
| Eagle 只读 | ✅ 只从 Eagle 读图 | 自定义收藏/新建表情写到 NativeSource（未来）|
| SAF 文件访问 | ✅ **DocumentFile API** | Android 11+ Scoped Storage 要求，免 MANAGE_EXTERNAL_STORAGE 权限 |

---

## 二、已实现 (v0.1 ~ v0.2)

### v0.1 — 原型闭环

- [x] 无障碍服务检测 QQ 聊天界面
- [x] 悬浮球浮动按钮（可拖动、吸附边缘）
- [x] 点击悬浮球 → 弹出表情选择器（Compose Dialog）
- [x] 选择表情 → 精确跳转 QQ「发送给好友」Activity
- [x] Fallback：精确跳转失败时走包名分享
- [x] 权限引导页（悬浮窗 + 无障碍 + 通知）
- [x] 占位表情自动生成（8 张 emoji 带色背景）

### v0.2 — Eagle 数据源 + 插件原型

- [x] **StickerSource** 数据源抽象接口
- [x] **StickerItem / Folder** 统一数据模型
- [x] **EagleSource** 读取 Eagle 4.0 JSON 库（SAF DocumentFile 兼容）
- [x] **StickerRepository** 注入 StickerSource，删掉占位图
- [x] **SAF 目录选择器** 选择图库路径 + 持久化（LibraryConfig）
- [x] **分类/标签筛选** UI（FilterChip）
- [x] **QQShareHelper** 改用 content:// URI（替代 FileProvider）
- [x] **插件系统原型**：
  - `PluginProtocol` Binder IPC 协议（JSON 字符串传输）
  - `PluginManager` 包扫描发现插件
  - `PluginStickerSource` 远程 Service → StickerSource 包装
  - `sticker-plugin-eagle` 独立 APK：Eagle 库通过 IPC 提供数据
  - 插件不可用时自动回退到内置 EagleSource
- [x] **Agent 约束规范**：AGENTS.md + .agent/rules/
- [x] **OpenSpec 流程**：提案 → 确认 → 执行 → commit

### 已知问题 & 解决

| 问题 | 方案 |
|------|------|
| 点击悬浮球无反应 | OnTouchListener 吞了事件 → 改为在 ACTION_UP 判断点击/拖动 |
| 提示"未安装 QQ" | Android 11+ 包可见性限制 → 添加 `<queries>` 声明 |
| QQ 内部分享有 4 个选项 | PackageManager 查找「发送给好友」Activity 精确跳转 |
| Eagle 4.0 改用 JSON 非 SQLite | 读取 metadata.json + 遍历 images/*.info/ 目录 |
| Android Scoped Storage 权限拒绝 | 改用 DocumentFile API 替代 java.io.File |

---

## 三、后续阶段

### v0.3 — 体验完善

- [ ] **搜索** — 按名称/标签/备注搜索（已有 placeholder）
- [ ] **插件管理界面** — 主 App 显示已安装插件、状态、开关
- [ ] **NativeSource** — 本地图片文件夹读取（脱离 Eagle 的轻量方案）
- [ ] **最近使用 / 常用** — 本地 SQLite 记录发送历史
- [ ] **悬浮球自动贴边隐藏**

### v0.4 — 更多发送方式

- [ ] **Android 微信发送** — 通过文件路径调用微信发送
- [ ] **QQ 无障碍自动点击发送** — 减少手动操作步骤
- [ ] **批量选择** — 一次选多个表情发送
- [ ] **自定义收藏** — 从任何来源收藏表情到本地 FavoriteSource

### v0.5 — 插件生态

- [ ] **插件 SDK 文档** — 公开协议，支持第三方开发
- [ ] **表情云市场插件** — 从云端浏览/下载表情包
- [ ] **多数据源合并展示** — 多个插件同时提供数据，统一显示

### v1.0 — 跨平台

- [ ] **Windows 剪贴板发送** — 选中表情 → 复制到剪贴板
- [ ] **macOS 适配** — 菜单栏图标 + 表情面板
- [ ] **iOS 适配** — 键盘扩展输入法
- [ ] **平台间同步策略完善**

---

## 四、代码结构

```
sticker-bridge/
├── app/                          ← 主 App
│   └── src/main/java/.../
│       ├── data/                 ← 数据层
│       │   ├── StickerSource.kt  ← 抽象接口
│       │   ├── StickerItem.kt    ← 统一数据模型
│       │   ├── Folder.kt         ← 文件夹模型
│       │   ├── EagleSource.kt    ← Eagle 4.0 内置实现（插件 fallback）
│       │   └── NativeSource.kt   ← [未来] 本地图片源
│       ├── plugin/               ← 插件系统
│       │   ├── PluginProtocol.kt ← IPC 协议常量
│       │   ├── PluginManager.kt  ← 插件发现/绑定
│       │   └── PluginStickerSource.kt ← 插件 → StickerSource
│       ├── MainActivity.kt       ← 权限引导 + 图库选择
│       ├── BubbleService.kt      ← 悬浮球服务
│       ├── QQDetectAccessibilityService.kt ← QQ 聊天检测
│       ├── StickerPickerActivity.kt ← 表情选择器
│       ├── StickerRepository.kt  ← 数据源注入层
│       ├── QQShareHelper.kt      ← QQ 分享
│       └── LibraryConfig.kt      ← SAF 路径管理
├── sticker-plugin-eagle/         ← [原型] Eagle 插件 APK
│   └── src/main/java/.../
│       ├── EaglePluginService.kt ← Binder IPC Service
│       └── data/                 ← 插件内部数据层
├── .agent/
│   ├── changes/                  ← OpenSpec proposals
│   └── rules/                    ← Agent 约束
├── AGENTS.md
├── CONTRIBUTING.md
└── README.md
```

---

## 五、Commit 规范

遵循 Conventional Commits，详见 `CONTRIBUTING.md`。

## 六、开发流程

所有编程任务走 **OpenSpec → Superpowers → gstack**：

| 阶段 | 做什么 | 约束文件 |
|------|--------|----------|
| **OpenSpec** | 在 `.agent/changes/` 下写 proposal，记录目标、涉及文件、边界条件、验收标准、风险 | `.agent/rules/workflow.md` |
| **Superpowers** | 按纪律执行：不改接口签名、写逻辑层测试、不改范围外文件 | `.agent/rules/code-quality.md` |
| **gstack** | 编译 → 用户确认 → 按逻辑拆分 commit | `.agent/rules/workflow.md` |

### 约束文件索引

| 文件 | 内容 |
|------|------|
| `AGENTS.md` | 入口约束，优先级最高 |
| `rules/workflow.md` | OpenSpec / Superpowers / gstack 各阶段执行细则 |
| `rules/code-quality.md` | Kotlin/Android 代码质量规范、架构约束、测试纪律 |
| `rules/risk-check.md` | 高风险改动检查清单 |

---

*此文件随项目进展持续更新。*
