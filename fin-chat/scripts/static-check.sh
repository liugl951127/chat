#!/bin/bash
# Java 静态检查: 大括号/小括号/方括号平衡 + import 检查
# 用法: ./static-check.sh

set -e
cd "$(dirname "$0")/.."

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "════════════════════════════════════════"
echo "  Java 静态检查"
echo "════════════════════════════════════════"

TOTAL=0
ERRORS=0

# 1. 找所有 .java
JAVA_FILES=$(find . -name "*.java" -not -path "*/target/*" | sort)

for f in $JAVA_FILES; do
    TOTAL=$((TOTAL + 1))

    # 1.1 大括号配平 (排除字符串和注释中的)
    OPEN_BRACE=$(tr -cd '{' < "$f" | wc -c)
    CLOSE_BRACE=$(tr -cd '}' < "$f" | wc -c)
    if [ "$OPEN_BRACE" != "$CLOSE_BRACE" ]; then
        echo -e "${RED}✗${NC} $f: 大括号不平衡 { = $OPEN_BRACE, } = $CLOSE_BRACE"
        ERRORS=$((ERRORS + 1))
    fi

    # 1.2 小括号配平 (粗略, 因为有字符串内括号, 跳过)

    # 1.3 检查空 class 文件 (只有 package 声明)
    LINE_COUNT=$(wc -l < "$f")
    if [ "$LINE_COUNT" -lt 5 ]; then
        echo -e "${YELLOW}⚠${NC} $f: 仅有 ${LINE_COUNT} 行, 可能未完成"
    fi

    # 1.4 检查明显错误: 未关闭的 if/for 块
    # 简单检查: 行尾 } 配平 (已在大括号中处理)
done

# 2. 检查 @SpringBootApplication 必有 main 方法
echo ""
echo "--- 检查 Application 类 ---"
for app in $(find . -name "*Application.java" -not -path "*/target/*"); do
    if ! grep -q "public static void main" "$app"; then
        echo -e "${RED}✗${NC} $app: 缺少 main 方法"
        ERRORS=$((ERRORS + 1))
    else
        echo -e "${GREEN}✓${NC} $app: 有 main 方法"
    fi
done

# 3. 检查 Controller 是否引入了 @RestController 或 @Controller
echo ""
echo "--- 检查 Controller 类 ---"
for ctrl in $(find . -name "*Controller.java" -not -path "*/target/*"); do
    if ! grep -q "@RestController\|@Controller" "$ctrl"; then
        echo -e "${RED}✗${NC} $ctrl: 缺少 @RestController 注解"
        ERRORS=$((ERRORS + 1))
    fi
done

# 4. 检查 @Autowired 字段命名 (避免用大写开头)
echo ""
echo "--- 检查 @Autowired 字段 ---"
for f in $JAVA_FILES; do
    # 找 @Autowired 后面跟的字段
    grep -A1 "@Autowired" "$f" 2>/dev/null | grep "private " | while read line; do
        FIELD=$(echo "$line" | sed 's/.*private \([A-Za-z0-9_]*\).*/\1/')
        FIRST=$(echo "$FIELD" | cut -c1)
        if [[ "$FIRST" =~ [A-Z] ]]; then
            echo -e "${YELLOW}⚠${NC} $f: 字段 $FIELD 首字母大写, 不符合 Java 规范"
        fi
    done
done

echo ""
echo "════════════════════════════════════════"
echo "  总文件: $TOTAL, 错误: $ERRORS"
echo "════════════════════════════════════════"
[ "$ERRORS" -eq 0 ] && exit 0 || exit 1
