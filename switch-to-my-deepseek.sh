#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────
# switch-to-my-deepseek.sh
# 快速切换 oh-my-openagent 所有 subagent 到 deepseek/deepseek-v4-flash
# 用自己的 DeepSeek API Key（需要在 OpenCode 中配置好 provider）
# ────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/.opencode/oh-my-openagent.json"

if [ ! -f "$CONFIG_FILE" ]; then
  echo "❌ 未找到 $CONFIG_FILE，请确保该文件存在。"
  exit 1
fi

# 替换所有 agent 的 model 字段
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' 's/"model": "[^"]*"/"model": "deepseek\/deepseek-v4-flash"/g' "$CONFIG_FILE"
else
  sed -i 's/"model": "[^"]*"/"model": "deepseek\/deepseek-v4-flash"/g' "$CONFIG_FILE"
fi

echo "✅ 已切换所有 subagent 到 deepseek/deepseek-v4-flash（自己的 API Key）"
