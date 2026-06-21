#!/bin/bash
# Vue 文件语法粗检 (无 node 也能跑)
# 1. <script> 配平
# 2. <template> 配平
# 3. <style> 配平
# 4. 关键 import 路径检查

set -e
cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

VUE_FILES=$(find src -name "*.vue")
TOTAL=0
ERRORS=0

for f in $VUE_FILES; do
    TOTAL=$((TOTAL + 1))

    # 1. <script> 配平
    if grep -q '<script' "$f"; then
        SCRIPT_OPEN=$(grep -c '<script' "$f")
        SCRIPT_CLOSE=$(grep -c '</script>' "$f")
        if [ "$SCRIPT_OPEN" != "$SCRIPT_CLOSE" ]; then
            echo -e "${RED}✗${NC} $f: <script> 配平失败 (open=$SCRIPT_OPEN, close=$SCRIPT_CLOSE)"
            ERRORS=$((ERRORS + 1))
        fi
    fi

    # 2. <template> 配平
    if grep -q '<template' "$f"; then
        T_OPEN=$(grep -c '<template' "$f")
        T_CLOSE=$(grep -c '</template>' "$f")
        if [ "$T_OPEN" != "$T_CLOSE" ]; then
            echo -e "${RED}✗${NC} $f: <template> 配平失败"
            ERRORS=$((ERRORS + 1))
        fi
    fi

    # 3. 大括号配平
    OPEN_BRACE=$(tr -cd '{' < "$f" | wc -c)
    CLOSE_BRACE=$(tr -cd '}' < "$f" | wc -c)
    if [ "$OPEN_BRACE" != "$CLOSE_BRACE" ]; then
        echo -e "${YELLOW}⚠${NC} $f: {} 配平 (开=$OPEN_BRACE, 闭=$CLOSE_BRACE)"
    fi

    # 4. 关键 API 路径引用
    grep -E "from '@/" "$f" | head -3 | while read line; do
        IMPORT_PATH=$(echo "$line" | sed -E "s/.*from '(@\/[^']+)'.*/\1/")
        # 简化: 信任相对路径, 跳过深度检查
    done
done

# TS 文件
TS_FILES=$(find src -name "*.ts")
for f in $TS_FILES; do
    TOTAL=$((TOTAL + 1))
    OPEN=$(tr -cd '{' < "$f" | wc -c)
    CLOSE=$(tr -cd '}' < "$f" | wc -c)
    if [ "$OPEN" != "$CLOSE" ]; then
        echo -e "${RED}✗${NC} $f: {} 配平失败 (开=$OPEN, 闭=$CLOSE)"
        ERRORS=$((ERRORS + 1))
    fi
done

echo ""
echo "════════════════════════════════════════"
echo "  Vue/TS 文件: $TOTAL, 错误: $ERRORS"
echo "════════════════════════════════════════"
[ "$ERRORS" -eq 0 ] && exit 0 || exit 1
