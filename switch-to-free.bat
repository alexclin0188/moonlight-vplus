@echo off
REM ────────────────────────────────────────────────────────────
REM switch-to-free.bat
REM 快速切换 oh-my-openagent 所有 subagent 到 opencode/deepseek-v4-flash-free
REM 免费模型，由 OpenCode 提供 API，无需自己配 key
REM Windows CMD / 双击可直接运行（内部用 PowerShell 替换）
REM ────────────────────────────────────────────────────────────

set CONFIG_FILE=.opencode\oh-my-openagent.json

if not exist "%CONFIG_FILE%" (
    echo [ERROR] 未找到 %CONFIG_FILE%，请确保该文件存在。
    pause
    exit /b 1
)

powershell.exe -NoProfile -Command ^
    $path = Resolve-Path "%CONFIG_FILE%"; ^
    $content = Get-Content $path -Raw; ^
    $newContent = $content -replace '\"model\": \"[^\"]*\"', '\"model\": \"opencode/deepseek-v4-flash-free\"'; ^
    [System.IO.File]::WriteAllText($path, $newContent); ^
    Write-Host "[OK] 已切换所有 subagent 到 opencode/deepseek-v4-flash-free（免费）"

echo.
pause
