# ADB Audio Transfer

通过 ADB 在两台 Android 手机之间传输系统音频。

## 功能

- **发送端模式**：捕获手机系统音频（音乐、视频、游戏等），通过 TCP 发送
- **接收端模式**：接收音频数据并实时播放
- 支持 ADB forward 实现 USB 有线传输

## 系统要求

- Android 10+（API 29+）
- 需要屏幕录制权限（用于捕获系统音频）

## 使用方法

### 1. 安装 APK

在两台手机上分别安装 APK。

### 2. 连接手机

用 USB 线连接发送端手机到电脑，确保 ADB 已启用：

```bash
adb devices
```

### 3. 设置 ADB Forward

在电脑上执行：

```bash
adb forward tcp:9876 tcp:9876
```

这会把电脑的 9876 端口转发到手机的 9876 端口。

### 4. 启动发送端

- 在发送端手机上打开 APP
- 选择"发送端"模式
- 点击"开始"按钮
- 授权屏幕录制权限
- 等待接收端连接

### 5. 启动接收端

- 在接收端手机上打开 APP
- 选择"接收端"模式
- 输入服务器 IP：`127.0.0.1`
- 端口保持默认：`9876`
- 点击"开始"按钮

### 6. 测试

在发送端手机上播放音乐或视频，接收端应该能听到声音。

## 无线 ADB（可选）

如果不想用 USB 线，可以使用无线 ADB：

```bash
# 1. 先用 USB 连接手机
adb tcpip 5555

# 2. 断开 USB，连接 WiFi
adb connect <手机IP>:5555

# 3. 设置端口转发
adb forward tcp:9876 tcp:9876
```

## 项目结构

```
ADB_Audio_Transfer/
├── app/
│   ├── src/main/kotlin/com/example/adb_audio_transfer/
│   │   ├── MainActivity.kt          # 主界面
│   │   ├── AudioCaptureService.kt   # 音频捕获/播放服务
│   │   └── ui/theme/                # Compose 主题
│   └── src/main/res/                # 资源文件
├── build.gradle.kts
└── settings.gradle.kts
```

## 技术细节

- 使用 `MediaProjection` + `AudioPlaybackCaptureConfiguration` 捕获系统音频
- 使用 `AudioRecord` 录制音频
- 使用 `AudioTrack` 播放音频
- TCP Socket 传输 PCM 原始音频数据
- 采样率：44100 Hz
- 声道：立体声
- 格式：16-bit PCM

## 已知限制

1. 需要 Android 10+（API 29+）
2. 部分应用可能禁止音频捕获（如 DRM 保护内容）
3. 音频有轻微延迟（网络传输导致）
4. 需要保持 APP 在前台或后台服务运行

## 编译

```bash
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`
