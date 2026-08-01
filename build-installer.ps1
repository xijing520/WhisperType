# build-installer.ps1 - 构建 WhisperType 安装程序
# 自动下载 Inno Setup（如未安装），编译 .iss 生成安装包
#
# 输出: installer-output\WhisperType-Setup-2.0.0.exe

$ErrorActionPreference = "Stop"

$ProjectRoot = $PSScriptRoot
$InnoSetupUrl = "https://github.com/jrsoftware/issrc/releases/download/is-6_7_3/innosetup-6.7.3.exe"
$InnoSetupDir = Join-Path $env:LOCALAPPDATA "InnoSetup6"
$IsccPath = Join-Path $InnoSetupDir "ISCC.exe"

Write-Host "===== WhisperType 安装程序构建脚本 =====" -ForegroundColor Cyan
Write-Host ""

# 1. 检查 Inno Setup 是否已安装
$installedIscc = Get-Command ISCC.exe -ErrorAction SilentlyContinue
if ($installedIscc) {
    $IsccPath = $installedIscc.Source
    Write-Host "已安装 Inno Setup: $IsccPath"
} elseif (Test-Path $IsccPath) {
    Write-Host "已下载 Inno Setup: $IsccPath"
} else {
    Write-Host "未找到 Inno Setup，正在下载..." -ForegroundColor Yellow
    
    # 下载 Inno Setup 安装程序
    $innoInstaller = Join-Path $env:TEMP "innosetup-installer.exe"
    Write-Host "  下载地址: $InnoSetupUrl"

    & curl.exe -L -o $innoInstaller $InnoSetupUrl --retry 3 --silent --show-error 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $innoInstaller)) {
        Write-Error "下载 Inno Setup 失败"
        exit 1
    }
    
    # 静默安装到 LOCALAPPDATA（不需要管理员权限）
    Write-Host "  安装 Inno Setup 到 $InnoSetupDir ..."
    $innoInstallArgs = "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /DIR=`"$InnoSetupDir`""
    Start-Process -FilePath $innoInstaller -ArgumentList $innoInstallArgs -Wait -NoNewWindow
    Start-Sleep -Seconds 2
    
    if (-not (Test-Path $IsccPath)) {
        # 尝试在 Program Files 中查找
        $pfIscc = Get-ChildItem "C:\Program Files*\Inno Setup*\ISCC.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($pfIscc) {
            $IsccPath = $pfIscc.FullName
            Write-Host "  Inno Setup 已安装: $IsccPath"
        } else {
            Write-Error "Inno Setup 安装失败，请手动安装: https://jrsoftware.org/isdl.php"
            exit 1
        }
    } else {
        Write-Host "  Inno Setup 安装完成"
    }
}

Write-Host ""

# 2. 先运行 build-exe.ps1 构建应用镜像
$buildExeScript = Join-Path $ProjectRoot "build-exe.ps1"
if (Test-Path $buildExeScript) {
    Write-Host "步骤 1: 构建应用镜像 (build-exe.ps1)..." -ForegroundColor Cyan
    & $buildExeScript
    if ($LASTEXITCODE -ne 0) {
        Write-Error "应用镜像构建失败"
        exit $LASTEXITCODE
    }
} else {
    Write-Error "未找到 build-exe.ps1"
    exit 1
}

Write-Host ""

# 3. 确保图标文件存在
$iconPath = Join-Path $ProjectRoot "app-icon.ico"
if (-not (Test-Path $iconPath)) {
    Write-Host "生成应用图标..."
    $genIconScript = Join-Path $ProjectRoot "generate-icon.ps1"
    if (Test-Path $genIconScript) {
        & $genIconScript
    }
}

# 4. 编译 Inno Setup 脚本
Write-Host "步骤 2: 编译安装程序..." -ForegroundColor Cyan
$issFile = Join-Path $ProjectRoot "installer.iss"

# 确保输出目录存在
$outputDir = Join-Path $ProjectRoot "installer-output"
if (Test-Path $outputDir) {
    Remove-Item $outputDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

& $IsccPath "/Qp" $issFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "安装程序编译失败"
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "===== 安装程序构建完成 =====" -ForegroundColor Green
$installerPath = Get-ChildItem $outputDir -Filter "*.exe" | Select-Object -First 1
if ($installerPath) {
    $sizeMB = [math]::Round($installerPath.Length / 1MB, 1)
    Write-Host "安装程序: $($installerPath.FullName)" -ForegroundColor Green
    Write-Host "大小:     $sizeMB MB" -ForegroundColor Green
    Write-Host ""
    Write-Host "用户只需运行此安装程序即可完成安装，安装过程中会自动下载 Whisper 模型。"
} else {
    Write-Warning "未找到生成的安装程序文件"
}
