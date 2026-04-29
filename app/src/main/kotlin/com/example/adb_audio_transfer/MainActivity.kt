package com.example.adb_audio_transfer

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.adb_audio_transfer.ui.theme.ADB_Audio_TransferTheme

class MainActivity : ComponentActivity() {

    private val REQUEST_CODE_MEDIA_PROJECTION = 1001

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, AudioCaptureService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            startForegroundService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ADB_Audio_TransferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onRequestCapturePermission = { requestMediaProjection() }
                    )
                }
            }
        }
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onRequestCapturePermission: () -> Unit) {
    var selectedMode by remember { mutableStateOf(AppMode.SENDER) }
    var isRunning by remember { mutableStateOf(false) }
    var serverPort by remember { mutableStateOf("9876") }
    var serverIp by remember { mutableStateOf("127.0.0.1") }
    var status by remember { mutableStateOf("空闲") }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "ADB Audio Transfer",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 模式选择
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedMode == AppMode.SENDER,
                onClick = { selectedMode = AppMode.SENDER },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("发送端")
            }
            SegmentedButton(
                selected = selectedMode == AppMode.RECEIVER,
                onClick = { selectedMode = AppMode.RECEIVER },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("接收端")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 配置区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (selectedMode == AppMode.SENDER) {
                    Text(
                        text = "发送端配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it.filter { c -> c.isDigit() } },
                        label = { Text("监听端口") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ADB 命令: adb forward tcp:$serverPort tcp:$serverPort",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "接收端配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverIp,
                        onValueChange = { serverIp = it },
                        label = { Text("服务器 IP") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverPort,
                        onValueChange = { serverPort = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 状态显示
        Text(
            text = "状态: $status",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 控制按钮
        Button(
            onClick = {
                if (!isRunning) {
                    if (selectedMode == AppMode.SENDER) {
                        onRequestCapturePermission()
                        status = "等待权限授权..."
                    } else {
                        // 启动接收端
                        val intent = Intent(context, AudioCaptureService::class.java).apply {
                            putExtra("mode", "receiver")
                            putExtra("serverIp", serverIp)
                            putExtra("serverPort", serverPort.toIntOrNull() ?: 9876)
                        }
                        context.startForegroundService(intent)
                        status = "正在连接..."
                    }
                } else {
                    // 停止服务
                    context.stopService(Intent(context, AudioCaptureService::class.java))
                    status = "空闲"
                }
                isRunning = !isRunning
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isRunning) "停止" else "开始",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 使用说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "使用步骤:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. 发送端手机开启开发者模式\n" +
                           "2. 用 USB 连接电脑\n" +
                           "3. 电脑执行: adb forward tcp:9876 tcp:9876\n" +
                           "4. 发送端点击开始，授权屏幕录制\n" +
                           "5. 接收端输入 127.0.0.1:9876 开始接收",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

enum class AppMode {
    SENDER,
    RECEIVER
}
