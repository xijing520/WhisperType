; WhisperType 安装程序脚本
; 使用 Inno Setup 编译生成标准 Windows 安装程序

#define MyAppName "WhisperType"
#define MyAppVersion "2.0.0"
#define MyAppPublisher "小寒"
#define MyAppExeName "WhisperType.exe"
#define MyAppDescription "WhisperType 语音输入助手"

[Setup]
; 应用信息
AppId={{B7F2A3C1-4D5E-4F6A-9B8C-1A2B3C4D5E6F}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL=https://github.com/whispertype
AppSupportURL=https://github.com/whispertype
AppUpdatesURL=https://github.com/whispertype

; 默认安装目录：系统盘下的 WhisperType 文件夹
DefaultDirName={sd}\WhisperType
DefaultGroupName={#MyAppName}

; 安装程序输出
OutputDir=installer\Output
OutputBaseFilename=WhisperTypeSetup

; 压缩
Compression=lzma2
SolidCompression=yes
WizardStyle=modern

; 源文件目录（相对于 .iss 文件的上级目录，即项目根目录）
SourceDir=..

; 卸载
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppDescription}

; 禁用安装到 Program Files 的默认提示
DisableProgramGroupPage=yes
DisableDirPage=no
AllowNoIcons=yes

; 语言
ShowLanguageDialog=yes

[Languages]
Name: "chinesesimp"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce
Name: "startmenuicon"; Description: "创建开始菜单快捷方式"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; 主程序
Source: "dist\WhisperType.exe"; DestDir: "{app}"; Flags: ignoreversion

; 应用配置和 JAR
Source: "dist\app\*"; DestDir: "{app}\app"; Flags: ignoreversion recursesubdirs createallsubdirs

; JRE 运行时
Source: "dist\runtime\*"; DestDir: "{app}\runtime"; Flags: ignoreversion recursesubdirs createallsubdirs

; Whisper 引擎
Source: "dist\whisper\*"; DestDir: "{app}\whisper"; Flags: ignoreversion recursesubdirs createallsubdirs

; AI 模型
Source: "dist\model\*"; DestDir: "{app}\model"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
; 桌面快捷方式
Name: "{commondesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Comment: "{#MyAppDescription}"; Tasks: desktopicon

; 开始菜单快捷方式
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Comment: "{#MyAppDescription}"; Tasks: startmenuicon

; 开始菜单卸载快捷方式
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"; Comment: "卸载 {#MyAppDescription}"

[Run]
; 安装完成后启动程序
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; 卸载时删除应用数据目录（可选）
Type: dirifempty; Name: "{app}"

[Code]
function InitializeSetup(): Boolean;
begin
  Result := True;
end;
