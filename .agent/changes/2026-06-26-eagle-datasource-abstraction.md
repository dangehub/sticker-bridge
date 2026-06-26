## Goal

实现 Eagle 数据库直接读取，同时建立数据源抽象层，为未来脱离 Eagle 改用本地方案做准备。

## Files affected

### 新增
- `app/src/main/java/com/example/stickerhelper/data/StickerItem.kt`
  — 统一数据模型（所有数据源的输出都转成此结构）
- `app/src/main/java/com/example/stickerhelper/data/StickerSource.kt`
  — 数据源抽象接口
- `app/src/main/java/com/example/stickerhelper/data/EagleSource.kt`
  — Eagle 实现的 StickerSource（读 SQLite + 文件系统）
- `app/src/main/java/com/example/stickerhelper/data/models/Folder.kt`
  — 文件夹模型

### 修改
- `app/src/main/java/com/example/stickerhelper/StickerRepository.kt`
  — 改为注入 StickerSource，不再硬编码占位图
- `app/src/main/java/com/example/stickerhelper/StickerPickerActivity.kt`
  — 改用真实数据，加分类/标签筛选 UI
- `app/build.gradle.kts`
  — 无新增依赖（用 Android 内置 `android.database.sqlite.SQLiteDatabase`）

## Out of scope

- ❌ 搜索功能（下个迭代做）
- ❌ 微信发送（不是数据源层的事）
- ❌ 悬浮球 / QQ 分享的改动
- ❌ 性能优化（先跑通，数据量大时再优化）
- ❌ Eagle thumbnail（直接用原图）

## 调研结果 — Eagle 4.0 数据模型

Eagle 4.0 已弃用 SQLite，改用纯 JSON + 文件系统：

```
Eagle.library/
├── metadata.json           ← 顶层：version、folders（树形）、smartFolders、quickAccess
├── tags.json               ← historyTags、starredTags
└── images/
    ├── {id}.info/
    │   ├── metadata.json   ← name, ext, tags[], folders[], annotation, width, height, size, isDeleted
    │   └── {name}.{ext}    ← 图片文件本体
    └── ...
```

样例库路径：`/Users/qudange/Pictures/表情桥测试用.library`（4 张图的空库）

### 关键映射

| Eagle field | StickerItem field |
|---|---|
| `id` | `id` |
| `name` + `ext` | fileName, fileExtension |
| `{library}/images/{id}.info/{name}.{ext}` | `filePath` |
| folders (ID array) → metadata.json → folder names | `folders` |
| `tags` (string array) | `tags` |
| `annotation` | `annotation` |
| `width`, `height` | `width`, `height` |
| `size` | `fileSize` |
| `modificationTime` | `modificationTime` |
| `isDeleted == true` | 过滤掉（不显示已删除） |

## Design

### 抽象层架构

```
StickerPickerActivity
       ↓
StickerRepository    ← 构造注入 StickerSource
       ↓
StickerSource (interface)
    ↙           ↘
EagleSource    NativeSource (未来)
```

### StickerItem — 统一数据模型

```kotlin
data class StickerItem(
    val id: String,               // 来源内唯一 ID
    val name: String,             // 显示名称
    val filePath: String,         // 文件绝对路径（供 Coil 加载）
    val fileExtension: String,    // png / jpg / gif / webp
    val folders: List<String>,    // 所属分类路径（如 ["日常/猫"]）
    val tags: List<String>,
    val annotation: String?,      // Eagle 备注
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val modificationTime: Long,
)
```

### StickerSource 接口

```kotlin
interface StickerSource {
    suspend fun getAll(): List<StickerItem>
    suspend fun getFolders(): List<Folder>   // 树形分类
    suspend fun getTags(): List<String>
    suspend fun search(query: String): List<StickerItem>
    suspend fun filter(folderId: String?, tags: List<String>?): List<StickerItem>
    val displayName: String                   // 「Eagle 库」或「本地收藏」
}
```

### EagleSource — 实现细节

- 读取 `{library}/metadata.json` 解析文件夹树
- 读取 `{library}/tags.json` 解析标签（historyTags + starredTags 去重）
- 遍历 `{library}/images/*.info/metadata.json` 读取每张图片元数据
- 图片文件路径：`{library}/images/{id}.info/{name}.{ext}`
- 过滤 `isDeleted == true` 的项
- 用 `kotlinx.serialization.json` 或 `org.json`（Android 内置）解析 JSON
- 路径配置：先硬编码可配置路径，后续版本做设置页

### 文件夹模型的两种形态

| 场景 | 形态 |
|------|------|
| Eagle 树形分类 | 用 `metadata.json → folders[].parent` 重建树 |
| 未来本地方案 | 用文件系统目录结构重建树 |

## Acceptance criteria

1. ✅ `EagleSource` 能读取 `metadata.json` 并列出所有非删除图片
2. ✅ 返回的 `StickerItem.filePath` 指向真实存在的图片文件
3. ✅ 文件夹树能正确重建（多层嵌套、关联到图片）
4. ✅ 标签能正确读取并去重
5. ✅ `StickerRepository` 改为注入 `StickerSource`，占位图逻辑移入 `EagleSource`
6. ✅ 无新增第三方依赖（用 Android 内置 org.json + Coroutine File I/O）
7. ✅ `./gradlew assembleDebug` 编译通过

## Risks

- **低**：Eagle 4.0 的 JSON schema 可能在后续小版本变化。方案：读时做字段缺失容错（? 默认值）
- **中**：大库（10000+ 图片）首次全量扫描 `.info/` 目录可能慢。方案：首次加载后台缓存，UI 展示先显示 loading
- **低**：Android 上读取外部文件路径可能存在权限问题。方案：用户需授予文件读权限（MANAGE_EXTERNAL_STORAGE 或 SAF）
- **低**：大 JSON 文件在主线程解析可能 ANR。方案：所有文件读取走 Dispatchers.IO + 协程
