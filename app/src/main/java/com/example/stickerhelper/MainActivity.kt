package com.example.stickerhelper

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import kotlinx.coroutines.delay

/**
 * 主界面：引导用户完成权限设置。
 *
 * 需要授予的权限：
 * 1. 无障碍服务（检测 QQ 聊天）
 * 2. 悬浮窗权限（显示悬浮球）
 * 3. 通知权限（Android 13+，保持前台服务运行）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionGuide()
                }
            }
        }
    }
}

@Composable
private fun PermissionGuide() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 权限状态
    var overlayGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var accessibilityEnabled by remember { mutableStateOf(false) }

    // 定期检查权限状态
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = Settings.canDrawOverlays(context)
            notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
            accessibilityEnabled = isAccessibilityServiceEnabled(context)

            // 全部就绪后停止轮询
            if (overlayGranted && notificationGranted && accessibilityEnabled) break

            // 每 1.5 秒检查一次
            delay(1500)
        }
    }

    // 悬浮窗权限申请器
    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // 返回后由 LaunchedEffect 更新状态
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 标题
        Text(
            text = "🐱 表情助手",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "在 QQ 聊天中快速发送表情包",
            fontSize = 14.sp,
            color = Color.Gray,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 权限卡片列
        PermissionCard(
            icon = "♿",
            title = "无障碍服务",
            description = "检测何时进入 QQ 聊天界面，自动显示悬浮球",
            isGranted = accessibilityEnabled,
            buttonText = "打开无障碍设置",
            onClick = {
                openAccessibilitySettings(context)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            icon = "🔘",
            title = "悬浮窗权限",
            description = "在 QQ 聊天界面显示表情选择按钮",
            isGranted = overlayGranted,
            buttonText = "授予悬浮窗权限",
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                overlayLauncher.launch(intent)
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                icon = "🔔",
                title = "通知权限",
                description = "保持后台服务运行，确保悬浮球始终可用",
                isGranted = notificationGranted,
                buttonText = "授予通知权限",
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS,
                    ).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                },
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0F4FF)
            ),
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
                    text = "1. 开启以上所有权限\n" +
                            "2. 打开 QQ，进入任意聊天对话框\n" +
                            "3. ✅ 悬浮球会自动出现在屏幕边缘\n" +
                            "4. 点击悬浮球 → 选择表情 → 自动分享到 QQ",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF333333),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 状态汇总
        val allGranted = overlayGranted && notificationGranted && accessibilityEnabled
        Text(
            text = if (allGranted) "✅ 所有权限已就绪，可以开始使用！"
            else "⚠️ 请完成以上所有权限设置",
            fontSize = 14.sp,
            color = if (allGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(
    icon: String,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
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
            // 图标
            Text(
                text = icon,
                fontSize = 28.sp,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 文字
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGranted) "✅" else "❌",
                        fontSize = 14.sp,
                    )
                }
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 按钮
            if (!isGranted) {
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "设置", fontSize = 13.sp)
                }
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

/** 检查自 Android 13 起的运行时通知权限 */
private fun checkSelfPermission(context: Context, permission: String): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
}
