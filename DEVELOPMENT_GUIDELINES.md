# Development Guidelines(Project Context)

1. 本项目很大程度上是一个Core，即为一系列基于区域的mod提供基本依赖。故而，本mod最大的功能是维护`Region`及其附带效果，并提供`entrypoints/api`中的接口。指令也要配套制作，指令的主要功能是紧急情况下的手工底层介入，应该要制作的比较全面为好。新增加功能或者数据的时候都要考虑用接口和指令对其修改的是否可以有效达成。
2. 对以下提及的工具，不管新增什么功能，均应该优先调用，如无，则在尽可能接近的模块平行新建。
3. 本mod有i18n系统，通过`Translator.tr()`函数实现，所有语言都要同步实现。原则上不要使用`Text.literal()`。需实现`resource`里面对应的英文项目。对于发送给玩家的文本，需用写清楚操作的对象（特别是操作者，Region, Scope, 属性，目标玩家，操作内容等）但不要引入Unicode特殊符号。本mod的语言系统是支持MOTD下的丰富显示的，请使得显示更加丰富，带有各种醒目的颜色和提示等效果。所有语言文件中的参数都要确认被正确传入，且在语言文件中不能被单引号包裹，不然将不能正确显示参数。**此外，凡是语言文件条目值中含有单引号 `'`（如英文中的 `it's`、`scope's` 等），且该条目以带参数的方式调用（即传入 `{0}` 等占位符），必须将单引号转义为 `''`（两个单引号）。这是因为 `java.text.MessageFormat` 将 `'` 视为转义字符，未转义的单引号会导致占位符或后续内容被错误解析。无参数调用的条目不受此影响，但建议统一转义以防将来添加参数时遗漏。**
4. 项目的几何工具全部都在`util/geo`中。
5. 项目有三个基础设施，`lazyTicker`,`RegionDatabase`和`Config`. 第一个用来注册每隔一段时间进行一次的事件，第二个是每次`Region`对象有数据被修改的话都要进行的保存操作，第三个是`infra/config/`目录下按类型分类的配置文件集合，存储了所有具体数值，任何具体数值都应该写在其中，特别是接下来新添加的具体数值。`Config`目录包含：`CoreConfig`（语言、延迟ticker间隔）、`PermissionConfig`（权限默认值及飞行相关参数）、`RuleConfig`（规则默认值）、`EffectConfig`（效果持续时间）、`SelectionConfig`（选点数量限制）、`GeoConfig`（几何尺寸约束）、`TeleportConfig`（传送点搜索半径）、`EntryExitConfig`（进出通知延迟）。
6. 修改完之后，要进行清晰的总结并修改`README.md`的相关模块，且前述任务中，不要使用各种emoji以及特殊Unicode符号。
   - 没有我的明确指示，不要更新版本信息，在原有版本下面修改。
   - 对于每次的版本更改描述应当简洁。
   - 版本更改一般加在现有版本，不要自作主张新建版本。
7. 原则上不要添加任何Comments.
8. 不要使用git.
9. 测试要包含./gradlew runServer.
10. 未说明清楚的机制、语言文件用名和感到机制模糊的地方等等应该向操作者提问。不要为了确认需求终止对话。
11. 命令参数中涉及 Region 名称或 GeoScope 名称的所有 SuggestionProvider，必须对不满足"全部字符均为 ASCII 字母或数字"条件的名称用双引号包裹后再 suggest，即使用 `if (!name.all { it.isLetterOrDigit() && it.code < 128 }) builder.suggest("\"$name\"") else builder.suggest(name)` 的形式。这是因为包含中文等非 ASCII 字符或空格的名称，在 Brigadier 命令解析中若不加引号将无法被正确识别。
12. 本项目跟CommunityAddon要高度协作，互相参考。