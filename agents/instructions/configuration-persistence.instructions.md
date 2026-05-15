---
applyTo: "src/main/kotlin/com/imyvm/iwg/infra/**,src/main/kotlin/com/imyvm/iwg/domain/**"
---

# 配置与持久化规则

1. 具体数值放入 `infra/config/` 下合适配置类，不在业务代码中硬编码具体数值。
2. 配置分类包括 `CoreConfig`、`PermissionConfig`、`RuleConfig`、`EffectConfig`、`SelectionConfig`、`GeoConfig`、`TeleportConfig`、`EntryExitConfig`。
3. `Region` 对象数据变化必须同步检查 `RegionDatabase` 保存逻辑。
4. 新增持久化字段必须提供旧数据可用的默认值。
5. 新字段只能追加在当前数据块末尾，不插入已有字段之间。
6. 周期性任务使用 `lazyTicker`。
