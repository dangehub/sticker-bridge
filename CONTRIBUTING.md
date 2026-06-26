# 贡献指南

## Commit 规范

采用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
type(scope): subject

Body (English description)

中文说明
```

### Type

| 类型 | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不修改功能） |
| `test` | 添加或修改测试 |
| `chore` | 构建 / 依赖 / 配置改动 |
| `docs` | 文档 |

### Scope

标注改动的模块，例如：
- `a11y` — 无障碍服务
- `bubble` — 悬浮球
- `picker` — 表情选择器
- `share` — 分享逻辑
- `eagle` — Eagle 数据源
- `build` — 构建配置
- `docs` — 文档

### 格式要求

1. **subject** 英文祈使句、小写开头、无句号
2. **body** 英文段落，空一行后接中文说明
3. **按逻辑拆分**，不一个 commit 塞所有改动

### 示例

```
feat(share): locate QQ 'send to friend' activity via PackageManager

Replace generic package-scoped share intent with explicit component
targeting. Uses queryIntentActivities to find the correct activity
labeled "发送给好友", bypassing QQ's internal menu picker.

通过 PackageManager 动态查找 QQ「发送给好友」Activity，跳过入口面板选择。
```

## 分支策略

```
main         ← 稳定可用版本
  ├── dev    ← 开发主分支
  └── feat/* ← 功能分支（从 dev 切出，PR 合并回 dev）
```

1. 功能从 `dev` 切出 `feat/<描述>` 分支
2. 开发完成后提交 PR → `dev`
3. 测试通过后合入 `dev`
4. 定期从 `dev` 合入 `main` 发布

## 代码风格

- **语言：** Kotlin，遵循官方风格
- **UI：** Jetpack Compose（新界面），传统 View（仅系统级 Overlay）
- **命名：**
  - 类名：`PascalCase`
  - 函数 / 变量：`camelCase`
  - 常量：`UPPER_SNAKE_CASE`
  - Compose 组件：`PascalCase`，注释 `@Composable`
- **资源：**
  - 布局文件：`snake_case`
  - drawable：`ic_` 前缀（图标）、`bg_` 前缀（背景）
  - string：`snake_case`

## 项目结构

```
StickerHelper/
├── app/
│   └── src/main/java/com/example/stickerhelper/
│       ├── MainActivity.kt                  # 引导 / 权限设置
│       ├── BubbleService.kt                 # 悬浮球服务
│       ├── QQDetectAccessibilityService.kt   # QQ 聊天检测
│       ├── StickerPickerActivity.kt          # 表情选择器
│       ├── StickerRepository.kt             # 表情数据源
│       └── QQShareHelper.kt                # QQ 分享
└── .hermes/plans/                           # 开发计划
```

## 测试

- 纯逻辑层（Repository、Helper）编写单元测试
- UI 层做手动验证
- 每项功能需有通过标准

## 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（需签名配置）
./gradlew assembleRelease
```
