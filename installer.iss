; WhisperType Installer Script for Inno Setup
; Build with: ISCC.exe installer.iss
; Prerequisites: Run build-exe.ps1 first to generate dist\WhisperType\

#define MyAppName "WhisperType"
#define MyAppVersion "2.0.0"
#define MyAppPublisher "Xiaohan"
#define MyAppExeName "WhisperType.exe"
#define MyAppDescription "Whisper 语音输入助手 - 离线语音转文字桌面工具"

[Setup]
AppId={{B7F3E8A2-4C5D-4F6E-9A8B-1C2D3E4F5A6B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppCopyright=Copyright (c) 2026 Xiaohan
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=installer-output
OutputBaseFilename=WhisperType-Setup-{#MyAppVersion}
SetupIconFile=app-icon.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=admin
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "在桌面创建快捷方式"; GroupDescription: "附加选项:"; Flags: checkedonce

[Files]
; 应用程序文件（整个 dist\WhisperType 目录）
Source: "dist\WhisperType\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
; 安装后下载 Whisper 模型到系统目录（仅当模型不存在时执行）
Filename: "powershell.exe"; Parameters: "-ExecutionPolicy Bypass -File ""{app}\download-model.ps1"""; Flags: postinstall skipifsilent; Description: "下载 Whisper 语音识别模型 (约 465MB)"; StatusMsg: "正在下载 Whisper 模型，请稍候..."; Check: ShouldDownloadModel

[UninstallDelete]
; 卸载时清理模型文件
Type: filesandordirs; Name: "{commonappdata}\WhisperType"

[Code]
function ShouldDownloadModel(): Boolean;
var
  ModelPath: String;
begin
  ModelPath := ExpandConstant('{commonappdata}\WhisperType\model\ggml-small.bin');
  Result := not FileExists(ModelPath);
end;
