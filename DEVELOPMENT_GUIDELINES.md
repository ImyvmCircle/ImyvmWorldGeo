# 开发规范（IMYVMWorldGeo 项目上下文）

> 配套文档：[写作规范](prompts/WRITING_STYLE.md) · [迭代规则](prompts/ITERATION_RULES.md) · [AI 规则](prompts/AI_RULES.md)

## AI 执行纪律

执行任何任务时，以下纪律优先于一切：

1. **不确定就问，别猜** — 机制不明、需求模糊时，向操作者提问；不要为确认需求而终止对话。
2. **没要求的不写** — 仅实现 prompt 明确要求的内容。
3. **只改被要求的部分** — 不修改 prompt 未涉及的代码或文档。
4. **给验收标准，别给步骤** — 完成后说明结果是否满足预期，而非描述执行过程。

## 通用开发规范

每次完成任务前后，必须逐条检查任务方案和执行是否符合以下规范：

1. **i18n**：通过 `Translator.tr()` 实现，中英文语言文件同步维护。原则上不使用 `Text.literal()`。对发送给玩家的文本，使用 MOTD 格式的颜色和样式，不引入 Unicode 特殊符号。不要用单引号包裹参数占位符。凡语言文件条目值中含有单引号（如 `it's`、`don't`），且以带参数方式调用，必须将 `'` 转义为 `''`；`java.text.MessageFormat` 将 `'` 视为转义字符，未转义会导致占位符或后续内容被错误解析。无参数调用不受此影响，但建议统一转义以防将来添加参数时遗漏。

2. **配置**：所有具体数值写入对应配置类，不在业务代码中硬编码。

3. **持久化**：凡涉及修改数据成员变量的操作，必须检查是否需要同步更新数据库存储。

4. **命令注册**：所有 Command 在 `CommandRegister.register()` 中注册，参数提取在同一文件完成，调用 application 层实现；无合适调用时，自行实现对应模块。

5. **SuggestionProvider**：涉及名称参数的所有 SuggestionProvider，不满足「全部字符为 ASCII 字母或数字」的名称必须用双引号包裹后 suggest：
   ```kotlin
   if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)
   ```

6. **代码规范**：原则上不新建 class，不添加 Comments。

7. **README.md**：修改机制后必须更新 `README.md`，以玩家侧的游戏机制介绍为主，不使用 emoji 等特殊 Unicode 符号；修改完后须向操作者提供清晰的变更总结。每次任务完成前须确认 changelog 已同步记录本次更改；没有明确指示不新建版本、不更新版本号；版本更改描述简洁。

8. **版本控制**：不使用 git，除非 prompt 明确要求。提交时遵循 git log 中已有的 commit 格式，不添加 Co-authored-by 等 trailer。

9. **测试**：测试必须包含 `./gradlew runServer`。

## 项目特有规范

1. **项目定位**：本项目是 Core，为基于区域的 mod 提供基本依赖，核心功能是维护 `Region` 及其附带效果并提供 `entrypoints/api` 接口。指令以紧急底层介入为主，应较完备。新增功能或数据时，须确认接口和指令能有效覆盖对其的修改。

2. **工具优先**：对以下提及的工具，无论新增何种功能，均应优先调用；如无，则在尽可能接近的模块平行新建。

3. **几何工具**：所有几何工具在 `util/geo` 中。

4. **基础设施**（`infra/`）：
   - `lazyTicker`：注册每隔一段时间执行一次的事件
   - `RegionDatabase`：`Region` 对象数据有修改时必须持久化
   - `Config`（`infra/config/`）：`CoreConfig`（语言、延迟 ticker 间隔）、`PermissionConfig`（权限默认值及飞行参数）、`RuleConfig`（规则默认值）、`EffectConfig`（效果持续时间）、`SelectionConfig`（选点数量限制）、`GeoConfig`（几何尺寸约束）、`TeleportConfig`（传送点搜索半径）、`EntryExitConfig`（进出通知延迟）

5. **i18n 补充**：消息文本中须写清楚操作的对象（操作者、Region、Scope、属性、目标玩家、操作内容等）。语言系统支持 MOTD 丰富显示，请使用醒目的颜色和提示等效果。

6. **SuggestionProvider 范围**：Region 名称和 GeoScope 名称均须应用通用规范中的双引号包裹规则，原因是包含中文等非 ASCII 字符或空格的名称在 Brigadier 命令解析中若不加引号将无法被正确识别。

7. **协作**：本项目与 WorldGeo-CommunityAddon 高度协作，互相参考。
