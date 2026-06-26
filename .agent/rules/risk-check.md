# Risk Check — 高风险改动检查清单

> 当 proposal 的 `## Risks` 中有一个或多个风险标记为「高」时，
> 执行前必须逐项检查此清单。

---

## 高风险操作定义

以下操作默认视为高风险：
- 修改 `AndroidManifest.xml`
- 新增权限
- 修改 Service 声明/行为
- 修改 Intent/Activity 跳转逻辑
- 新增 ContentProvider / FileProvider
- 修改构建脚本（`build.gradle.kts`、`settings.gradle.kts`）
- 升级 AGP / Gradle / Kotlin 版本
- 修改数据库 schema
- 引入新 dependency（库/plugin）
- 修改无障碍服务逻辑

---

## 检查清单

### 1. 编译检查
- [ ] `./gradlew assembleDebug` 编译通过
- [ ] 无新 warning（如果必须引入，说明原因）

### 2. 兼容性检查
- [ ] 目标 API level 不变（或明确写明变更原因）
- [ ] 新代码兼容 minSdk（当前值是多少？确认后再改）
- [ ] 涉及权限：是否添加了运行时权限请求？
- [ ] 涉及 Intent：`PackageManager.queryIntentActivities` 返回值空时有没有 fallback？

### 3. 回滚检查
- [ ] 修改前的文件备份：git diff 可逆吗？
- [ ] 如果改坏了 APK 闪退，回滚需要几步？（1 步：git revert → rebuild → reinstall）
- [ ] 数据库 schema 改动：能否降级到旧版？不能降级时有没有 migration 方案？

### 4. 副作用检查
- [ ] 这个改动会影响现有的原型功能吗？（悬浮球、QQ 分享、无障碍检测）
- [ ] 如果影响，回退方案是什么？
- [ ] 涉及 Service/AccessibilityService 的改动，是否需要重新安装/重启才能生效？

### 5. 用户确认
- [ ] 用户已知风险
- [ ] 用户同意先测试验证再推送

---

## 低风险（跳过清单）

以下情况跳过检查清单，但依然走 OpenSpec → Superpowers → gstack：
- 修改纯 UI（Compose 组件布局、颜色、文字）
- 修改注释/文档
- 修改纯计算逻辑（不涉及 IO、权限、系统服务）
- 修改测试代码
