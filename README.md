# WhisperType

基于 whisper.cpp 的**离线语音输入助手**。长按全局热键说话，松开后自动将语音转录为文字，并模拟键盘输入到当前焦点窗口。全程本地离线运行，不上传任何音频，隐私安全。适用于任何可接收键盘输入的 Windows 应用（聊天、文档、浏览器、代码编辑器等）。

## 核心特性

- **离线语音识别** — 基于 whisper.cpp + ggml-small 模型，纯本地推理，不联网、不上传音频
- **长按热键输入** — 长按全局热键（默认 CapsLock）开始录音，松开即转写并输入；亦支持点击切换模式
- **任意窗口输入** — 模拟键盘 / 粘贴方式将文字输入到当前焦点窗口，兼容几乎所有应用
- **macOS Liquid Glass 风格 UI** — Swing + FlatLaf 实现的五层液态玻璃质感设置界面，支持浅色 / 深色主题
- **全局热键** — 基于 jnativehook，可自定义按键与修饰键（Ctrl/Shift/Alt/Win 组合）
- **系统托盘** — 后台常驻，五色状态指示，右键菜单完整操作
- **依赖自检与自动下载** — 首次启动自动检查 whisper 运行时与模型，缺失时自动下载
- **精细可配置** — 录音参数、识别超时、输入模式、文本后缀、空格裁剪、声音反馈、悬浮窗、主题等

## 工作流程

1. 启动后常驻系统托盘，注册全局热键
2. **长按模式**：按下热键 → 开始录音（托盘变红、显示悬浮窗）→ 松开热键 → 停止录音并调用 whisper-cli 转写（托盘变蓝）→ 识别完成后将文字输入到当前焦点窗口
3. **点击切换模式**：单击热键开始录音，再次单击停止并转写
4. 录音时长低于 `minRecordingMs`（默认 300ms）视为误触，自动忽略

## 系统托盘

| 状态 | 颜色 | 含义 |
|------|------|------|
| IDLE | 绿色 | 待机，就绪 |
| RECORDING | 红色 | 录音中 |
| RECOGNIZING | 蓝色 | 识别中 |
| ERROR | 黄色 | 出错 |
| DISABLED | 灰色 | 已禁用 |

- **左键单击**：切换启用 / 禁用
- **右键菜单**：启用 / 禁用、打开设置、切换模型、查看识别历史（最近 50 条）、退出

## 运行环境

- **操作系统**：Windows 10/11（64 位）
- **Java**：17 或更高（安装版已内嵌运行时）
- **whisper.cpp 运行时**：仓库 `whisper/` 目录已附带（whisper-cli.exe + 依赖 DLL）
- **模型**：ggml-small.bin（约 465 MB），由安装程序自动下载到 `%PROGRAMDATA%\WhisperType\model\`

## 目录结构

```
WhisperType/
├── src/main/java/com/whispertype/   # Java 源码
│   ├── Main.java                    # 程序入口与主流程编排
│   ├── audio/AudioRecorder.java     # 音频录制（javax.sound.sampled）
│   ├── config/AppConfig.java        # 配置定义与 JSON 持久化
│   ├── dependency/DependencyChecker.java  # 运行时依赖检查与自动下载
│   ├── hotkey/GlobalHotkeyListener.java   # 全局热键监听（jnativehook）
│   ├── hotkey/KeyCodeMapper.java    # 按键码映射
│   ├── input/TextInputEngine.java   # 文本输入模拟（键盘 / 粘贴）
│   ├── recognizer/WhisperRecognizer.java  # whisper-cli 进程调用与结果解析
│   ├── tray/TrayManager.java        # 系统托盘与状态指示
│   └── ui/                          # 液态玻璃 UI 组件
│       ├── GlassUI.java             # 玻璃材质渲染
│       ├── GlassPanel.java          # 玻璃面板
│       ├── GlassButton.java         # 玻璃按钮
│       ├── ToggleSwitch.java        # 开关控件
│       ├── MacTitleBar.java         # macOS 风格标题栏
│       └── SettingsWindow.java      # 设置窗口（侧边栏 + 多分区）
├── whisper/                         # whisper.cpp 运行时（DLL + exe）
├── ui-design/                       # UI 设计预览（HTML + CSS）
├── pom.xml                          # Maven 构建配置
├── build-exe.ps1                    # jpackage 打包为 exe
├── build-installer.ps1              # Inno Setup 生成安装程序
├── download-model.ps1               # 下载 ggml-small 模型脚本
├── installer/setup.iss              # Inno Setup 安装脚本
├── installer.iss                    # Inno Setup 脚本（build-installer.ps1 引用）
├── app-icon.ico                     # 应用图标
├── mvnw / mvnw.cmd                  # Maven Wrapper
└── LICENSE                          # MIT 许可证
```

## 构建

### 前置要求

- JDK 17
- Maven 3.6+（或直接使用项目自带的 Maven Wrapper）
- 打包 exe 需 JDK 17+（jpackage）
- 生成安装程序需 Inno Setup 6.7+（`build-installer.ps1` 会自动下载安装）

### 编译打包

```powershell
# 使用 Maven Wrapper 编译，产出可执行 jar
.\mvnw clean package
# 产物：target\WhisperType.jar（含全部依赖的 fat jar）
```

### 打包为 Windows 可执行程序

```powershell
.\build-exe.ps1         # jpackage 生成 dist\WhisperType\（含 runtime + app + whisper）
.\build-installer.ps1   # Inno Setup 生成标准 Windows 安装程序
```

## 配置

配置文件位于 `~/.whispertype/config.json`，首次运行自动生成，可在设置界面修改。

### 完整配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `hotkeyCode` | int | 58 | 全局热键键码（58 = CapsLock） |
| `hotkeyModifiers` | int | 0 | 修饰键（Ctrl/Shift/Alt/Win 组合） |
| `longPressMode` | bool | true | true=长按模式，false=点击切换模式 |
| `minRecordingMs` | int | 300 | 最短录音时长(ms)，低于此值视为误触忽略 |
| `sampleRate` | int | 16000 | 采样率(Hz)，whisper 要求 16000 |
| `sampleSizeBits` | int | 16 | 采样位深 |
| `channels` | int | 1 | 声道数（单声道） |
| `audioInputDevice` | string | "" | 音频输入设备（空=系统默认） |
| `modelPath` | string | `%PROGRAMDATA%\WhisperType\model\ggml-small.bin` | 模型文件路径 |
| `whisperCliPath` | string | `whisper\whisper-cli.exe` | whisper-cli 可执行文件路径 |
| `recognizeTimeoutMs` | int | 5000 | 识别超时(ms) |
| `pasteMode` | bool | true | true=粘贴方式输入，false=逐字符模拟键盘 |
| `textSuffix` | string | "" | 输入文字后自动追加的后缀（如句号、空格） |
| `trimWhitespace` | bool | true | 是否裁剪首尾空白 |
| `autoSendMode` | bool | false | 是否自动发送（如配合回车） |
| `soundFeedback` | bool | true | 录音开始 / 结束声音反馈 |
| `showFloatingWindow` | bool | true | 录音时显示悬浮状态窗 |
| `darkTheme` | bool | false | 深色主题 |
| `enabled` | bool | true | 是否启用语音输入 |

### 日志

运行日志写入 `~/.whispertype/whispertype.log`（安装版在程序根目录）。

## 技术栈

- **Java 17 + Swing** — 桌面 GUI
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) — 语音识别引擎（通过 whisper-cli 进程调用）
- [jnativehook](https://github.com/kwhat/jnativehook) 2.2.2 — 全局键盘鼠标监听
- [FlatLaf](https://www.formdev.com/flatlaf/) 3.5.4 — 现代扁平外观基座
- [Jackson](https://github.com/FasterXML/jackson) 2.16.1 — JSON 配置序列化
- **构建**：Maven + Maven Shade + jpackage + Inno Setup

## 模型下载说明

模型从 `hf-mirror.com`（Hugging Face 国内镜像）下载，国内网络可直连。如需更换模型（如 tiny/base/medium/large），修改 `modelPath` 指向对应 `.bin` 文件即可。

## 常见问题

- **热键无效**：确认 CapsLock 未被其他软件占用；在设置中改用其他按键或组合键
- **识别慢 / 不准**：ggml-small 在 CPU 上约 1× 实时；可换 ggml-medium 提升精度（更慢），或确认录音设备与采样率(16000Hz)
- **首次启动慢**：DependencyChecker 在检查并按需下载运行时与模型
- **托盘中文乱码**：已使用 Swing JPopupMenu（FlatLaf 渲染）替代 AWT PopupMenu 解决

## 许可证

[MIT License](./LICENSE) — 完全公开，允许任意使用、修改、分发、再发行与商用，无任何附加限制。
