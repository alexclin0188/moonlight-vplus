@echo off
REM ────────────────────────────────────────────────────────────
REM switch-to-my-deepseek.bat
REM 快速切换 oh-my-openagent 所有 subagent 到 deepseek/deepseek-v4-flash
REM 用自己的 DeepSeek API Key（需要在 OpenCode 中配置好 provider）
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
    $newContent = $content -replace '\"model\": \"[^\"]*\"', '\"model\": \"deepseek/deepseek-v4-flash\"'; ^
    [System.IO.File]::WriteAllText($path, $newContent); ^
    Write-Host "[OK] 已切换所有 subagent 到 deepseek/deepseek-v4-flash（自己的 API Key）"

echo.
pause
