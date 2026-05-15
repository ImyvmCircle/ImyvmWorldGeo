---
applyTo: "src/main/kotlin/**/CommandRegister.kt,src/main/kotlin/**/command/**/*.kt"
---

# 命令规则

1. 命令在 `CommandRegister.register()` 中注册。
2. 参数在同一文件提取，再调用 application 对应实现。
3. 没有合适调用时，在对应模块实现。
4. 避免只有 ID 可用的命令参数；可读名称存在时提供 Provider 或建议项。
5. Region 或 GeoScope 名称的 SuggestionProvider 遇到非 ASCII 字符或空格时，用双引号包裹后 suggest。
