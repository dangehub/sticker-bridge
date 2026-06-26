package com.example.stickerhelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stickerhelper.share.ShareTargetLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 主界面：权限引导 + 输入源/输出目标管理。
 *
 * 权限（全部可选项）：
 * 1. 无障碍服务（自动检测前台 App 并显示悬浮球）
 * 2. 悬浮窗权限（显示悬浮球）
 * 3. 通知权限（Android 13+，保持前台服务运行）
 *
 * 管理：
 * - 输入源：Eagle 图库 + 插件 APK
 * - 输出目标：从 JSON 加载，可启用/禁用、导入/导出
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 权限状态
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }

    // 图库路径
    var libraryPath by remember { mutableStateOf<String?>(null) }

    // 输出目标
    var shareTargets by remember { mutableStateOf<List<com.example.stickerhelper.share.ShareTarget>>(emptyList()) }
    var enabledTargets by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 边缘收缩开关
    var edgeShrinkEnabled by remember { mutableStateOf(true) }

    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = Settings.canDrawOverlays(context)
            notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            accessibilityEnabled = isAccessibilityServiceEnabled(context)

            // 加载输出目标
            shareTargets = withContext(Dispatchers.IO) {
                ShareTargetLoader.load(context)
            }
            enabledTargets = loadEnabledTargets(context)
            edgeShrinkEnabled = loadEdgeShrinkPref(context)

            delay(1500)
        }
    }

    // 悬浮窗权限申请器
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    // 图库目录选择器
    val libraryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val resolved = LibraryConfig.saveLibraryUri(context, it)
            libraryPath = resolved
            if (resolved != null) {
                val label = try {
                    DocumentsContract.getTreeDocumentId(it).substringAfter(":")
                } catch (_: Exception) { "" }
                Toast.makeText(context, "已选择图库${if (label.isNotEmpty()) "：$label" else ""}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "无法解析图库路径", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 导入 JSON 文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.readText() ?: ""
                inputStream?.close()
                // 写入用户覆盖路径
                context.openFileOutput("imported_targets.json", Context.MODE_PRIVATE).use { out ->
                    out.write(content.toByteArray())
                }
                Toast.makeText(context, "已导入，请重启应用生效", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 加载已保存的图库路径
    LaunchedEffect(Unit) {
        libraryPath = LibraryConfig.getLibraryPath(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 标题
        Text(
            text = "🐱 表情助手",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "跨平台表情包快捷发送工具",
            fontSize = 14.sp,
            color = Color.Gray,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ═══ 权限区 ═══
        Text(
            text = "🔑 权限设置",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            icon = "♿",
            title = "无障碍服务",
            description = "自动检测表情 App 输入框，智能显示悬浮球（可选）",
            isGranted = accessibilityEnabled,
            buttonText = "打开无障碍设置",
            isOptional = true,
            onClick = { openAccessibilitySettings(context) },
        )

        Spacer(modifier = Modifier.height(8.dp))

        PermissionCard(
            icon = "🔘",
            title = "悬浮窗权限",
            description = "显示可拖动的表情发送按钮",
            isGranted = overlayGranted,
            buttonText = "授予悬浮窗权限",
            isOptional = false,
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayLauncher.launch(intent)
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = "🔔",
                title = "通知权限",
                description = "保持后台服务运行",
                isGranted = notificationGranted,
                buttonText = "授予通知权限",
                isOptional = false,
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ═══ 图库设置 ═══
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "📁 输入源",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        LibraryCard(
            libraryPath = libraryPath,
            onClick = { libraryPickerLauncher.launch(null) },
        )

        // ═══ 输出目标 ═══
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📤 输出目标",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    try {
                        // 导出当前配置
                        val json = context.assets.open("output-targets-default.json")
                            .bufferedReader().readText()
                        val exportIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, "output-targets.json")
                        }
                        context.startActivity(exportIntent)
                        Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("导出 JSON", fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch("application/json")
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("导入 JSON", fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 输出目标列表
        shareTargets.forEach { target ->
            val isEnabled = target.id in enabledTargets
            TargetCard(
                icon = target.icon,
                name = target.displayName,
                enabled = isEnabled,
                onToggle = { enabled ->
                    toggleTarget(context, target.id, enabled)
                }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // ═══ 选项 ═══
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "⚙️ 选项",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        OptionCard(
            icon = "📐",
            title = "边缘自动收缩",
            description = "悬浮球拖到屏幕边缘时自动缩小",
            checked = edgeShrinkEnabled,
            onToggle = {
                saveEdgeShrinkPref(context, it)
                edgeShrinkEnabled = it
            }
        )

        // ═══ 使用说明 ═══
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FF)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📖 使用说明",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        append("1. 选择 Eagle 图库目录\n")
                        append("2. 授予悬浮窗权限（必需）\n")
                        append("3. 开启无障碍 → 自动检测输入框显示悬浮球\n")
                        append("   不开也无妨 → 悬浮球始终显示，手动点击使用\n")
                        append("4. 在输出目标中启用/禁用需要发送到的 App\n")
                        append("5. 点击悬浮球 → 选择表情 → 自动发送\n")
                    },
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF333333),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TargetCard(
    icon: String,
    name: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 24.sp, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = name,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF4CAF50),
                ),
            )
        }
    }
}

@Composable
private fun OptionCard(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 24.sp, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(text = description, fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    icon: String,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    isOptional: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = icon, fontSize = 28.sp, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGranted) "✅" else if (isOptional) "⭐" else "❌",
                        fontSize = 14.sp,
                    )
                }
                Text(
                    text = description + if (isOptional && !isGranted) "\n（不开仍可手动使用）" else "",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (!isGranted) {
                OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
                    Text(text = buttonText, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(libraryPath: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "📦", fontSize = 28.sp, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Eagle 图库", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    text = if (libraryPath != null) "✅ ${libraryPath.substringAfterLast("/")}"
                    else "❌ 未选择",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(8.dp)) {
                Text(text = if (libraryPath != null) "更换" else "选择", fontSize = 13.sp)
            }
        }
    }
}

/** 检查无障碍服务是否开启 */
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_GENERIC
    )
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

/** 打开无障碍设置页面 */
private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Toast.makeText(context, "请找到「表情助手」并开启", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开无障碍设置", Toast.LENGTH_SHORT).show()
    }
}

private fun loadEnabledTargets(context: Context): Set<String> {
    val prefs = context.getSharedPreferences("target_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getStringSet("enabled_targets", null)
    return saved ?: setOf("qq_friend", "wechat_friend", "clipboard", "generic_share")
}

private fun toggleTarget(context: Context, targetId: String, enabled: Boolean) {
    val prefs = context.getSharedPreferences("target_prefs", Context.MODE_PRIVATE)
    val current = prefs.getStringSet("enabled_targets", null)?.toMutableSet()
        ?: mutableSetOf("qq_friend", "wechat_friend", "clipboard", "generic_share")
    if (enabled) current.add(targetId) else current.remove(targetId)
    prefs.edit().putStringSet("enabled_targets", current).apply()

    // 通知 AppDetectService 刷新追踪的包名列表
    try {
        val intent = Intent(context, AppDetectService::class.java).apply {
            action = "reload"
        }
        context.startService(intent)
    } catch (_: Exception) {
        // 服务未运行，忽略
    }
}

private fun loadEdgeShrinkPref(context: Context): Boolean {
    val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("edge_shrink", true)
}

private fun saveEdgeShrinkPref(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("edge_shrink", enabled).apply()
}
