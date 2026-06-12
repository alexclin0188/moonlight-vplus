#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────
# switch-to-free.sh
# 快速切换 oh-my-openagent 所有 subagent 到 opencode/deepseek-v4-flash-free
# 免费模型，由 OpenCode 提供 API，无需自己配 key
# ────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/.opencode/oh-my-openagent.json"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "❌ 未找到 $CONFIG_FILE，请确保该文件存在。"
  exit 1
fi

# 替换所有 agent 的 model 字段
if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS 的 sed 需要空备份后缀
  sed -i '' 's/"model": "[^"]*"/"model": "opencode\/deepseek-v4-flash-free"/g' "$CONFIG_FILE"
else
  sed -i 's/"model": "[^"]*"/"model": "opencode\/deepseek-v4-flash-free"/g' "$CONFIG_FILE"
fi

echo "✅ 已切换所有 subagent 到 opencode/deepseek-v4-flash-free（免费）"
