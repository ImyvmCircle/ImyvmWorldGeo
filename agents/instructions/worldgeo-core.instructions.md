---
applyTo: "src/main/kotlin/com/imyvm/iwg/**"
---

# WorldGeo Core 规则

1. 本项目是基于区域的 mod Core，维护 `Region`、`GeoScope`、区域效果和 `entrypoints/api`。
2. 新增核心功能或数据时，同步考虑 API 和指令能否完成修改。
3. 几何逻辑使用 `util/geo`。
4. 优先调用已有工具；没有可用工具时，在最接近的模块平行新建，先对照同类功能文件确认落点。
5. 与 CommunityAddon 保持协作，API 或区域机制变化同步考虑 addon 影响。
6. `RegionIdHandler` 的 `idMark` 字段保留以下编号供 addon 区分用途：`1` 归 WorldGeo-AdventureAddon 野区，`2` 归 WorldGeo-CommunityAddon 社区，`0` 表示未分类，其余编号留待后续分配。本项目代码不强制 enforce，由调用方在 `createRegion` 时传入对应 mark；Adventure 与 Community 创建的 region 集合写死互斥。
