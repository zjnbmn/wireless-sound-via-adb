package com.example.adb_audio_transfer

import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.adb_audio_transfer.ui.theme.ADB_Audio_TransferTheme

class MainActivity : ComponentActivity() {

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
    var serverIp by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("空闲") }
    var showQrCode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val context = LocalContext.current

    // 生成二维码
    LaunchedEffect(showQrCode, serverPort) {
        if (showQrCode && selectedMode == AppMode.SENDER) {
            val ip = NetworkUtils.getLocalIpAddress(context) ?: "192.168.43.1"
            val content = "$ip:$serverPort"
            qrBitmap = QrCodeGenerator.generateQRCode(content, 512)
        }
    }

    if (showQrScanner) {
        QrCodeScanner(
            onQrCodeScanned = { result ->
                // 解析 IP:Port
                val parts = result.split(":")
                if (parts.size == 2) {
                    serverIp = parts[0]
                    serverPort = parts[1]
                    showQrScanner = false
                    status = "已扫描: $result"
                }
            },
            onCancel = { showQrScanner = false }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WiFi Audio Transfer",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 模式选择
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedMode == AppMode.SENDER,
                    onClick = { 
                        selectedMode = AppMode.SENDER
                        showQrCode = false
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("发送端")
                }
                SegmentedButton(
                    selected = selectedMode == AppMode.RECEIVER,
                    onClick = { 
                        selectedMode = AppMode.RECEIVER
                        showQrCode = false
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("接收端")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                        
                        // 显示当前 IP
                        val currentIp = NetworkUtils.getLocalIpAddress(context) ?: "获取中..."
                        Text(
                            text = "本机 IP: $currentIp",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it.filter { c -> c.isDigit() } },
                            label = { Text("监听端口") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 显示/隐藏二维码按钮
                        Button(
                            onClick = { showQrCode = !showQrCode },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showQrCode) "隐藏二维码" else "显示二维码")
                        }
                        
                        // 显示二维码
                        if (showQrCode && qrBitmap != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier
                                    .size(250.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                            Text(
                                text = "让接收端扫描此二维码",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    } else {
                        Text(
                            text = "接收端配置",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = serverIp,
                            onValueChange = { serverIp = it },
                            label = { Text("发送端 IP") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverPort,
                            onValueChange = { serverPort = it.filter { c -> c.isDigit() } },
                            label = { Text("端口") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // 扫描二维码按钮
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("扫描二维码")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态显示
            Text(
                text = "状态: $status",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 控制按钮
            Button(
                onClick = {
                    if (!isRunning) {
                        if (selectedMode == AppMode.SENDER) {
                            onRequestCapturePermission()
                            status = "等待权限授权..."
                        } else {
                            if (serverIp.isBlank()) {
                                status = "请先输入发送端 IP"
                                return@Button
                            }
                            val intent = Intent(context, AudioCaptureService::class.java).apply {
                                putExtra("mode", "receiver")
                                putExtra("serverIp", serverIp)
                                putExtra("serverPort", serverPort.toIntOrNull() ?: 9876)
                            }
                            context.startForegroundService(intent)
                            status = "正在连接..."
                        }
                    } else {
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
                    if (selectedMode == AppMode.SENDER) {
                        Text(
                            text = "1. 打开发送端手机热点\n" +
                                   "2. 点击开始，授权屏幕录制\n" +
                                   "3. 显示二维码给接收端扫描",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "1. 连接发送端手机热点\n" +
                                   "2. 扫描二维码获取 IP\n" +
                                   "3. 点击开始接收音频",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

enum class AppMode {
    SENDER,
    RECEIVER
}
