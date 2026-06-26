# Code Quality Rules — 代码质量约束

> 适用于所有提交到 sticker-bridge 的代码。
> 违反这些规则的 PR 会被要求修改。

---

## 1. 语言与风格

### Kotlin 风格
- 遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 空白：4 空格缩进，不用 tab
- 花括号：K&R 风格（左花括号不换行）
- 最大行宽：120 字符

### 命名规范

| 元素 | 格式 | 示例 |
|------|------|------|
| 类/接口 | `PascalCase` | `StickerRepository` |
| 函数/属性 | `camelCase` | `loadStickers()` |
| 常量（`const val`） | `UPPER_SNAKE_CASE` | `MAX_BUBBLE_SIZE` |
| Compose 组件 | `PascalCase` | `fun StickerGrid()` |
| 布局文件 | `snake_case` | `activity_main.xml` |
| drawable | `ic_` / `bg_` 前缀 | `ic_favorite.png` |
| String key | `snake_case` | `error_no_network` |

### 文件组织
- 一个文件一个公开类，文件名为类名 + `.kt`
- 例外：紧密相关的辅助类/扩展函数可以合并到同一文件
- 扩展函数放在 `.kt` 文件顶层，不要嵌套在类里

---

## 2. Android 专用规则

### Kotlin 代码
```kotlin
// ✅ val > var（不可变优先）
private val stickerList = mutableStateListOf<Sticker>()

// ❌ var 必须有明确的不可变原因
// var stickerList = mutableListOf<Sticker>() — 不需要可变就用 val

// ✅ 非空优先于可空（避免 !!）
fun loadSticker(id: Long): Sticker?  // 明确可能为 null
// 不要写 fun loadSticker(id: Long): Sticker 然后抛出异常
```

### Compose
- 组件函数用 `@Composable` 注解，且首字母大写
- State 提升到调用方，Composable 保持无状态（或最少状态）
- 不要用 `remember` 做昂贵计算——用 `remember(keys)` 控制缓存生命周期

### Activity / Service
- Activity 只做生命周期管理，业务逻辑放到 ViewModel 或 Repository
- Service 的 `onStartCommand` 要简短，耗时代码走协程

### 资源
- string 写在 `strings.xml`，不要硬编码中文到代码里
- 颜色写在 `colors.xml`（或 Compose Color 常量）
- dimens 写在 `dimens.xml`

---

## 3. 架构约束

### 分层
```
UI Layer (Activity / Composable)
    ↓
Service Layer (BubbleService, AccessibilityService)
    ↓
Logic Layer (Helper, Manager)
    ↓
Data Layer (Repository → Storage)
```

### 单向依赖
- UI 层 → Logic 层 → Data 层
- Data 层不能反向依赖 Logic 层
- Logic 层通过接口依赖 Data 层（方便测试替换）

### 每个 Repository 必须：
1. 有接口（interface）
2. 有默认实现
3. 构造参数注入依赖（不用手动 new）

### 协程规则
- Main 线程安全：ViewModel 和 Compose 层通过 `viewModelScope.launch` 启动
- IO 操作（数据库、文件读写）用 `Dispatchers.IO`
- 所有 `suspend` 函数必须文档注释说明需要在哪个 Dispatcher 上调用
- 禁止 `GlobalScope`、`runBlocking`（除测试外）

---

## 4. 测试纪律

### 测试范围
- Repository 必须写单元测试（mock 数据源）
- Helper/Utility 类必须写单元测试
- Service / Activity 不做单元测试（手动验证）

### 测试命名
```
fun `方法名_条件_期望`()
```
```kotlin
@Test
fun `loadStickers_byFolder_returnsFilteredList`() {
    // ...
}
```

### 测试要求
- 每个测试函数测一件事
- 测试不依赖顺序（可以任意排列执行）
- 不用 Thread.sleep——用 `delay` 或 `TestCoroutineDispatcher`
