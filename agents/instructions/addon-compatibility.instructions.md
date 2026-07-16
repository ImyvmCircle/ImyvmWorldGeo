---
applyTo: "src/main/kotlin/com/imyvm/iwg/inter/api/**,src/main/kotlin/com/imyvm/iwg/**/*.kt,docs/addon-api-compatibility.md"
---

# Addon API 兼容规则

1. `com.imyvm.iwg.inter.api` 是受支持的 addon surface。
2. 修改 public JVM signature 前，检查 compiled descriptor 和已知调用方。
3. 优先提供 typed replacement，并保留 deprecated delegating wrapper；不要直接移除既有签名。
4. 旧 addon 必须继续加载，并能调用保留的 compatibility method；不安全 mutation 行为不需要继续有效。
5. deprecation 不代表计划删除。API 至少经过两个 released version 且得到 maintainer 明确批准后，才进入可删除状态。
6. 不自动把 deprecation 提升到 `DeprecationLevel.ERROR` 或 `HIDDEN`。
7. addon-facing 兼容变化记录到 `docs/addon-api-compatibility.md`，并提供具体 replacement 或 migration example。
8. 兼容文档中的 unreleased marker 在 release preparation 时替换为实际版本。
9. addon 兼容变化交付前，用 `javap` 或等价 ABI 检查确认 JVM descriptor。
