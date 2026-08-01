# download-model.ps1 - 下载 Whisper small 模型到系统目录
# 由安装程序在安装完成后调用

$ErrorActionPreference = "Stop"

$ModelDir = Join-Path $env:PROGRAMDATA "WhisperType\model"
$ModelFile = Join-Path $ModelDir "ggml-small.bin"
$ModelUrl = "https://hf-mirror.com/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"

# 如果模型已存在且大小合理（>400MB），跳过下载
if (Test-Path $ModelFile) {
    $size = (Get-Item $ModelFile).Length
    if ($size -gt 400MB) {
        Write-Host "模型已存在，跳过下载: $ModelFile"
        exit 0
    }
}

# 创建目录
if (-not (Test-Path $ModelDir)) {
    New-Item -ItemType Directory -Path $ModelDir -Force | Out-Null
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  正在下载 Whisper small 模型" -ForegroundColor Cyan
Write-Host "  大小: 约 465MB" -ForegroundColor Cyan
Write-Host "  目标: $ModelFile" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 使用 curl.exe 下载（Windows 10+ 自带，支持重定向和进度条）
$curlPath = "curl.exe"
try {
    # 先尝试用 curl 下载（更好的重定向支持）
    Write-Host "使用 curl 下载..."
    & $curlPath -L -o "$ModelFile.tmp" -# --retry 3 --retry-delay 5 $ModelUrl
    if ($LASTEXITCODE -eq 0 -and (Test-Path "$ModelFile.tmp")) {
        $tmpSize = (Get-Item "$ModelFile.tmp").Length
        if ($tmpSize -gt 400MB) {
            Move-Item "$ModelFile.tmp" $ModelFile -Force
            Write-Host ""
            Write-Host "模型下载完成!" -ForegroundColor Green
            Write-Host "  路径: $ModelFile" -ForegroundColor Green
            Write-Host "  大小: $([math]::Round($tmpSize / 1MB, 1)) MB" -ForegroundColor Green
            exit 0
        } else {
            Write-Warning "下载文件大小异常: $([math]::Round($tmpSize / 1KB, 1)) KB"
            Remove-Item "$ModelFile.tmp" -Force -ErrorAction SilentlyContinue
        }
    }
} catch {
    Write-Warning "curl 下载失败: $($_.Exception.Message)"
}

# 回退到 .NET WebClient 下载
Write-Host "尝试使用 .NET WebClient 下载..."
try {
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.WebClient
    $client.Headers.Add("User-Agent", "WhisperType/2.0")
    
    # 注册进度事件
    $watcher = New-Object System.Diagnostics.Stopwatch
    $watcher.Start()
    
    $downloadTask = $client.DownloadFileTaskAsync($ModelUrl, "$ModelFile.tmp")
    
    while (-not $downloadTask.IsCompleted) {
        if (Test-Path "$ModelFile.tmp") {
            $currentSize = (Get-Item "$ModelFile.tmp").Length
            $elapsed = $watcher.Elapsed.TotalSeconds
            if ($elapsed -gt 0 -and $currentSize -gt 0) {
                $speed = [math]::Round($currentSize / 1MB / $elapsed, 1)
                Write-Host "`r  已下载: $([math]::Round($currentSize / 1MB, 1)) MB  速度: $speed MB/s" -NoNewline
            }
        }
        Start-Sleep -Milliseconds 500
    }
    
    $watcher.Stop()
    $client.Dispose()
    
    if (Test-Path "$ModelFile.tmp") {
        $tmpSize = (Get-Item "$ModelFile.tmp").Length
        if ($tmpSize -gt 400MB) {
            Move-Item "$ModelFile.tmp" $ModelFile -Force
            Write-Host ""
            Write-Host "模型下载完成!" -ForegroundColor Green
            Write-Host "  路径: $ModelFile" -ForegroundColor Green
            Write-Host "  大小: $([math]::Round($tmpSize / 1MB, 1)) MB" -ForegroundColor Green
            exit 0
        }
    }
    Write-Host ""
    Write-Error "模型下载失败，文件大小不足"
    exit 1
} catch {
    Write-Host ""
    Write-Error "下载失败: $($_.Exception.Message)"
    exit 1
}
