---
applyTo: "src/main/kotlin/com/imyvm/iwg/**"
---

# WorldGeo Core 规则

## 项目边界

1. 本项目是基于区域的 mod Core，维护 `Region`、`GeoScope`、区域效果和 `entrypoints/api`。
2. 新增核心功能或数据时，同步考虑 API 和指令能否完成修改。
3. 几何逻辑使用 `util/geo`。
4. 优先调用已有工具；没有可用工具时，在最接近的模块平行新建，先对照同类功能文件确认落点。
5. 与 CommunityAddon 保持协作，API 或区域机制变化同步考虑 addon 影响。
6. `RegionIdHandler` 的 `idMark` 字段保留以下编号供 addon 区分用途：`1` 归 WorldGeo-AdventureAddon 野区，`2` 归 WorldGeo-CommunityAddon 社区，`0` 表示未分类，其余编号留待后续分配。本项目代码不强制 enforce，由调用方在 `createRegion` 时传入对应 mark；Adventure 与 Community 创建的 region 集合写死互斥。

## 领域不变量

1. 携带 `Region` 传入的 `GeoScope` 必须属于该 `Region`，在公开边界和 mutation 边界验证归属。
2. 不要把 `Region` 与无归属约束的子对象或子集合作为独立参数传递；能用 typed owner 或 typed target 表达时优先使用类型防止错配。
3. 全局设置和玩家专属设置是不同主体，不要在生产逻辑里用含义不明的 nullable UUID 表达这种差异。
4. 内置权限继承只适用于 `PermissionKey`；extension permission key 是精确匹配，除非未来经过审查的 API 明确加入继承。
5. extension permission key 和 rule key 必须先注册，受支持 API 查询或 mutation 才能使用。
6. rule、entry setting 和 exit setting 不支持 personal subject。
7. duplicate setting 由 setting type/key 和 subject 识别，不恢复依赖 list order 的 duplicate 行为。
8. `Region.settings` 和 `GeoScope.settings` 是 legacy ABI snapshot，返回列表的 mutation 不得作为写入路径。
9. 所有 setting mutation 都必须经过受控 store 或 application 边界。
10. 持久化失败不得报告成功；mutation 无法保存时要恢复内存状态。
11. 除非同时提供 migration 和兼容测试，否则保留既有 database tag、enum encoding 和 legacy record layout。
12. `GeoShape` 内部 geometry 必须在构造边界完整验证；不要让未验证的 index-based parameter slice 进入生产逻辑，在 input codec 边界立即转成有描述性的 geometry record。

## 持久化与错误

1. untrusted value 和 persisted value 进入领域模型前必须验证。
2. 在边界拒绝 unknown setting subclass、invalid enum ordinal、invalid count、malformed ID 和 invalid geometry parameter。
3. 既有 atomic write helper 可用时，写入保持 atomic。
4. 不把 arbitrary exception message 当作 i18n key 翻译；domain/application failure 应暴露已知 message key 或 typed error。
5. user-visible success message 只在操作和必要保存都成功后发送。
