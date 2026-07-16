---
applyTo: "src/main/kotlin/**/*.kt,src/main/java/**/*.java"
---

# Kotlin 与领域建模规则

## 类型与空值

1. 每个函数只承担一个清晰职责。
2. 正常 Minecraft runtime state 使用明确的非空参数。
3. 不通过 nullable 参数、boolean 或互相依赖的值组合编码模式；拆分操作，或用 sealed type、enum、validated value object 表达合法状态。
4. 避免没有语义的 marker interface；interface 必须定义调用方实际使用的 capability、invariant 或 API constraint。只给类型贴标签时，优先使用 sealed union、enum、annotation 或不新增抽象。
5. 领域模型不要使用 `Any`。`Any`、unchecked cast、宽泛 base key 和 raw collection 只允许出现在序列化、反射、命令解析或 legacy compatibility 边界，并且必须立即验证并转换为具体领域类型。
6. 不要让 compatibility facade 的 `Any` 或 nullable protocol 回流到生产 resolver、store 或 mutation。
7. permission code 接收 permission key；rule code 接收 rule key；effect code 接收 effect key。
8. closed domain type 使用 exhaustive `when` expression。
9. 避免 `!!`。必需值在边界验证一次；非必需值直接处理 nullable 结果。
10. 避免 `!!?.`、`!! ?:` 或 assert non-null 后再检查 null 之类自相矛盾的 null handling。
11. API 边界优先返回 immutable result 和 snapshot；mutable collection 保持在所属 aggregate 内部私有。
12. equality 具有领域含义时使用 data/value type。

## Mixin、性能与线程

1. Java mixin 保持薄层：收集 Minecraft callback context，调用 application 逻辑，再应用结果。
2. 匹配 touched 文件的既有格式和命名，不做无关格式化重写。
3. Minecraft 交互和移动 handler 是热路径；已有 indexed resolver 时，不要在每次事件中扫描 setting 或使用 `filterIsInstance`。
4. 热路径中避免临时 list、composite lookup object、reflection 和重复 parsing。
5. 分配前先限制工作量；不要在 enforce limit 前枚举整个 region、shape、chunk set 或 search cube。
6. 坐标、面积、平方距离、tick 和 ID 使用 overflow-safe arithmetic。
7. 除非 API 明确另有说明，默认核心 gameplay mutation 运行在 server thread。
8. 没有明确跨线程调用方时，不新增 concurrent collection 或 lock；addon-facing API 使线程归属不确定时，保留既有同步。
