# AGENTS.md — Sticker Bridge 机器人约束

> 任何 agent（Hermes、Claude Code、Cursor 等）在为本项目工作前**必须**读取此文件。
> 所有规则按优先级排列，不得跳过。

---

## 0. 失效规则

**如用户明确说「先跳过规范」「直接改」「不用走流程」，则此文件的内容暂不适用。**
但用户未明确豁免时，以下规则是硬约束。

---

## 1. 黄金法则

一条压倒一切的原则：

> **只改被要求的。不改未要求改的。不引入不必要的抽象。**
>
> 如果某个改动是为了「未来可能需要」而做的，不要做。等真的需要时再做。

---

## 2. 工作流约束

| 阶段 | 行为 |
|------|------|
| **OpenSpec** | 所有非 trivial 改动（修 CSS、文案除外）必须先写 proposal 到 `.agent/changes/`，等待用户确认后才动手 |
| **Superpowers** | 不修改接口签名（不改参数类型/顺序/返回类型）。不改范围外文件。所有纯逻辑层写单元测试 |
| **gstack** | 改完后必须 `./gradlew assembleDebug` 编译通过。用户确认后才 commit。commit 按逻辑拆分 |

proposal 文件命名：`YYYY-MM-DD-short-description.md`

---

## 3. 代码红线和绿线

### 红线（绝对禁止）

- ❌ **不编造不存在的方法/类/API** — 如果调用的方法不确定是否存在，先确认
- ❌ **不是说编译通过就等于逻辑正确** — 编译通过和功能正确是两回事
- ❌ **重构前必须枚举不变量** — 不知道什么不能变就重构，100% 会破坏功能
- ❌ **不制造紧迫感** — 不要写「这很重要得赶紧修」之类的语句，正常告知即可
- ❌ **不改未要求改的文件** — 哪怕看到一个小 bug，如果用户没提，不改
- ❌ **不引入新依赖除非 proposal 里写了** — 每个新库/新 Gradle plugin 都要有明确理由

### 绿线（必须遵守）

- ✅ **Compile-first** — 任何代码改动后，先编译验证通过，再继续下一步
- ✅ **不变量声明** — 重构前，先写下这个模块的不变量（不能破坏的行为）
- ✅ **问而不是猜** — 不确定的地方问用户，不要自作聪明
- ✅ **最简方案优先** — 能用 if 解决的不要用设计模式，能用函数的不要用类

---

## 4. 项目档案

### 项目身份
- **名称：** Sticker Bridge（代码目录名: `sticker-bridge`）
- **描述：** Cross-platform sticker manager. Tap to send anywhere on Android, Windows, macOS & iOS.
- **GitHub：** `dangehub/sticker-bridge`
- **当前阶段：** v0.1 原型（仅 Android）
- **开发流程：** OpenSpec → Superpowers → gstack
- **Commit 风格：** [Conventional Commits](https://www.conventionalcommits.org/)

### 快速命令
```bash
# 编译
./gradlew assembleDebug

# 安装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 关键人员
- **曲淡歌（曲奇）** — 用户 / Owner
- **GitHub org：** dangehub

---

## 5. 文件体系

```
sticker-bridge/
├── AGENTS.md                         ← 此文件（入口约束）
├── CONTRIBUTING.md                   ← 贡献指南（commit 规范、分支策略）
├── .agent/
│   ├── changes/                      ← OpenSpec proposals
│   └── rules/
│       ├── workflow.md               ← 工作流执行细则
│       ├── code-quality.md           ← 代码质量约束
│       └── risk-check.md             ← 高风险改动检查清单
└── .hermes/plans/                    ← 开发计划
```

---

## 6. 引用

本文件的设计参考了以下社区实践：
- [PatrickJS/awesome-cursorrules](https://github.com/PatrickJS/awesome-cursorrules) — 规则文件格式与 anti-sycophancy 原则
- [cursor.directory AGENTS.md](https://cursor.directory) — 项目级 AGENTS.md 约定
