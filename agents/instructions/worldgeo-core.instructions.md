---
applyTo: "src/main/kotlin/com/imyvm/iwg/**"
---

# WorldGeo Core 规则

1. 本项目是基于区域的 mod Core，维护 `Region`、`GeoScope`、区域效果和 `entrypoints/api`。
2. 新增核心功能或数据时，同步考虑 API 和指令能否完成修改。
3. 几何逻辑使用 `util/geo`。
4. 优先调用已有工具；没有可用工具时，在最接近的模块平行新建。
5. 与 CommunityAddon 保持协作，API 或区域机制变化同步考虑 addon 影响。
