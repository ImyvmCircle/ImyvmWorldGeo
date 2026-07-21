---
applyTo: "src/main/kotlin/**/CommandRegister.kt,src/main/kotlin/**/command/**/*.kt"
---

# 命令规则

1. 命令在 `CommandRegister.register()` 中注册。
2. 参数在同一文件提取，再调用 application 对应实现。
3. 没有合适调用时，在对应模块实现。
4. 避免只有 ID 可用的命令参数；可读名称存在时提供 Provider 或建议项。
5. 新增或修改机制时，提供游戏内部命令测试落点，覆盖查询、创建、修改、删除或触发对应运行时行为。
6. 游戏内部测试命令和直接操作命令默认要求服务器管理玩家权限；用户明确指定更低权限时，按指定身份设置权限。
7. Region 或 GeoScope 名称的 SuggestionProvider 遇到非 ASCII 字符或空格时，用双引号包裹后 suggest。
