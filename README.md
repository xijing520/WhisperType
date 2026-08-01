# WhisperType

基于 whisper.cpp 的离线语音输入助手。长按热键说话，松开后自动将语音转为文字并输入到当前焦点窗口。全程离线，无需联网，隐私安全。

## 特性

- **离线语音识别** — 基于 whisper.cpp + ggml-small 模型，不联网、不上传音频
- **长按热键输入** — 长按全局热键（默认 CapsLock）开始录音，松开即转写并模拟键盘输入到任意窗口
- **macOS Liquid Glass 风格 UI** — Swing + FlatLaf 实现的液态玻璃质感设置界面，支持浅色 / 深色主题
- **全局热键** — 基于 jnativehook，可自定义按键与修饰键
- **系统托盘** — 后台常驻，快速启停、打开设置、查看识别历史
- **精细可配置** — 录音参数、识别超时、粘贴模式、文本后缀、空格裁剪、声音反馈、悬浮窗等
- **依赖自检** — 首次启动自动检查 whisper 运行时与模型

## 运行环境

- Windows 10/11（64 位）
- Java 17 运行时（安装版已内嵌）
- whisper.cpp 运行时（仓库 `whisper/` 目录已附带）
- ggml-small 模型（约 465 MB，由安装程序自动下载到 `%PROGRAMDATA%\WhisperType\model\`）

## 目录结构

```
WhisperType/
├── src/main/java/com/whispertype/   # Java 源码
│   ├── Main.java                    # 程序入口
│   ├── audio/                       # 音频录制
│   ├── config/                      # 配置（JSON 持久化）
│   ├── dependency/                  # 运行时依赖检查
│   ├── hotkey/                      # 全局热键监听
│   ├── input/                       # 文本输入模拟
│   ├── recognizer/                  # whisper-cli 调用
│   ├── tray/                        # 系统托盘
│   └── ui/                          # 液态玻璃 UI 组件
├── whisper/                         # whisper.cpp 运行时（DLL + exe）
├── pom.xml                          # Maven 构建配置
├── build-exe.ps1                    # jpackage 打包为 exe
├── build-installer.ps1              # Inno Setup 生成安装程序
├── download-model.ps1               # 下载 ggml-small 模型
├── installer/setup.iss              # Inno Setup 安装脚本
└── app-icon.ico                     # 应用图标
```

## 构建

需要 JDK 17 与 Maven。

```powershell
# 编译打包为可执行 jar
.\mvnw clean package
# 产物：target\WhisperType.jar
```

打包为 Windows 可执行程序与安装程序：

```powershell
.\build-exe.ps1         # jpackage 生成 dist\WhisperType\
.\build-installer.ps1   # Inno Setup 生成安装程序（自动下载 ISCC）
```

## 配置

配置文件位于 `~/.whispertype/config.json`，可在设置界面修改。关键项：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| hotkeyCode / hotkeyModifiers | 全局热键 | 58 / 0（CapsLock 长按） |
| longPressMode | 长按模式 | true |
| modelPath | 模型路径 | `%PROGRAMDATA%\WhisperType\model\ggml-small.bin` |
| whisperCliPath | whisper-cli 路径 | `whisper\whisper-cli.exe` |
| pasteMode | 粘贴模式输入 | true |
| darkTheme | 深色主题 | false |
| soundFeedback | 声音反馈 | true |
| showFloatingWindow | 录音悬浮窗 | true |

## 技术栈

- Java 17 + Swing
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) 语音识别引擎
- [jnativehook](https://github.com/kwhat/jnativehook) 2.2.2 全局键鼠监听
- [FlatLaf](https://www.formdev.com/flatlaf/) 3.5.4 UI 基座
- [Jackson](https://github.com/FasterXML/jackson) 2.16.1 JSON 处理
- Maven + jpackage + Inno Setup 构建

## 许可证

[MIT License](./LICENSE) — 完全公开，允许任意使用、修改、分发与商用。
