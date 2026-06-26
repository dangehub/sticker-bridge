# Workflow Rules — 工作流执行细则

## 总则

所有功能开发遵循 OpenSpec → Superpowers → gstack 三阶段流程。

---

## 1. OpenSpec — 提案阶段

### 触发条件

以下改动**必须**写 proposal：
- 新增功能
- 重构
- 修改 API / 接口签名
- 引入新依赖
- 修改构建脚本
- 跨模块改动

以下改动**不需要** proposal：
- 修改文案
- 修改 CSS 样式
- 修复 typos
- 简单 bug 修复（≤5 行，不涉及逻辑修改）

### Proposal 内容要求

```markdown
## Goal
一句话说明为什么要改

## Files affected
绝对路径列所有涉及的文件（/app/src/main/java/.../Foo.kt）

## Out of scope
特意不动的范围，避免 scope creep

## Acceptance criteria
可验证的验收标准。每个标准必须是能确定「过」或「不过」的

## Risks
- 这个改动的副作用？
- 会影响哪些现有功能？
- 回滚难度？
```

### 流程

1. 在 `.agent/changes/` 下创建 `YYYY-MM-DD-short-description.md`
2. 等待用户确认
3. 确认后进入执行阶段（Superpowers）

---

## 2. Superpowers — 执行阶段

### 纪律

1. **不改接口签名**
   - 不改函数的参数名称、参数类型、参数顺序
   - 不改函数的返回类型
   - 如需扩展，使用默认参数或重载，不修改现有签名

2. **不改范围外文件**
   - proposal 里没列的文件不动
   - 即使看到明显 bug，如果不在 scope 内，不改。单独开 proposal

3. **写测试**
   - Repository、Helper、Utility 等纯逻辑层必须写单元测试
   - UI 层暂不做自动测试（手动验证）

4. **最简实现原则**
   ```kotlin
   // ❌ 不要引入设计模式装优雅
   interface StickerStrategy
   class DefaultStickerStrategy : StickerStrategy
   
   // ✅ 一个简单的 if 就够了
   if (isQQ) {
       qqShare(path)
   } else {
       wechatShare(path)
   }
   ```

---

## 3. gstack — 提交阶段

### 步骤

1. **用户确认后才 commit** — 不要自动 push
2. **按逻辑拆分 commit**
   - 一个功能 = 一个 commit（不要 10 个文件改了一堆东西写一个 "update"）
   - 重构和功能分开 commit
3. **编译验证**
   ```bash
   ./gradlew assembleDebug
   ```
   编译不通过不能 commit。编译通过 ≠ 逻辑正确，但编译不通过是中止条件。

### Commit 格式

```
type(scope): subject

Body (English paragraph, explaining what and why)

中文说明
```

| type | 用途 |
|------|------|
| feat | 新功能 |
| fix | Bug 修复 |
| refactor | 重构 |
| test | 测试 |
| chore | 构建/依赖/配置 |
| docs | 文档 |

### CI 验证

- 所有涉及 CI/GitHub Actions 的修改，push 后必须等 Actions 跑完且结果为 success 才通知用户
- CI 失败必须先修好，不能放任
