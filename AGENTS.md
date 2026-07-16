# Agents 规则

本文件是本仓库的 Agents 通用入口，面向所有自动化编码代理和协作代理。用户指令优先于本文件；本文件优先于 `agents/` 下的专项规则。不要为了缩小 diff 弱化正确性、持久化安全或 addon 兼容性。

## 基础准则

1. 不确定就问，不要猜测。
2. 没有要求的不写，只写要求了的部分。
3. 以验收标准进行测试通过才算完成，别只给步骤。
4. 不适用的专项规则不强行套用；不确定是否适用时询问。

## 项目边界

1. IMYVMWorldGeo 是 Fabric Minecraft mod，主要使用 Kotlin 编写，并包含 Java mixin。
2. `com.imyvm.iwg.domain` 存放领域状态。
3. `com.imyvm.iwg.application` 存放用例和运行时行为。
4. `com.imyvm.iwg.infra` 存放持久化和外部集成。
5. `com.imyvm.iwg.inter.api` 是受支持的 addon API。
6. `inter.api` 之外的 public 声明不自动视为受支持 API，但既有 JVM 签名仍可能需要兼容。

## 规则路由

1. 产出或改写正式文字时读取 `agents/WRITING_STYLE.md`，并完整执行其第四节写后链路；缺标题审定、机械扫描、人工判退、CV/TTR 或 AIGC-X 段落结果，任务未完成。
2. 执行开发任务时读取 `agents/instructions/development.instructions.md`，并先对照现有结构与同类功能文件，再开始写代码。
3. 修改 Kotlin、Java mixin、领域模型、热路径或线程相关代码时读取 `agents/instructions/kotlin-domain-style.instructions.md`。
4. 修改 addon API、公开 JVM 签名或兼容文档时读取 `agents/instructions/addon-compatibility.instructions.md`。
5. `agents/instructions/development.instructions.md` 只作为索引，具体规则以相关专项 instruction 为准。
6. `agents/archive/` 只作为历史任务参考，不自动作为当前规则。

## Git 与同步

1. 执行修改任务前检查远端主分支状态；可以 fast-forward 时先同步，存在冲突或需要人工判断时先询问。
2. 没有明确要求时，不主动 commit、push、rebase、创建 pull request 或执行其它会改变仓库历史和远端状态的 Git 操作。
3. 用户要求 git、提交、推送、拉取或发布时，先同步远端，再按请求操作。
4. 用户要求 commit 时，检查精确文件列表，只 stage 本次相关文件，创建 commit 后必须 push 到当前分支。
5. commit 或 push 失败时，报告失败命令、失败原因和仓库当前状态，不把未推送 commit 说成已经完成。
6. 保留 dirty worktree 中无关的用户改动；除非明确要求，不使用破坏性 Git 命令。

## 执行流程

1. 实现前先给出完整、可审查的工作路线，覆盖范围、阶段、预期文件、兼容或回滚风险、验证方式和明确排除项。
2. 用户未明确要求执行路线前，只做只读调查；为支持方案审查，可只修改 `docs/plan/` 下的本地笔记。
3. 编辑前读完整调用链：构造或 API 边界、application 逻辑、resolver、持久化和测试。
4. 修改共享函数前搜索所有调用方，在最窄的共享边界修根因。
5. 保持纵向完整；领域不变量变化必须同步 resolver、mutation path、codec、公开 API、兼容层和测试。
6. 优先做最小正确修改；先复用现有代码和标准库，再考虑抽象或依赖。
7. 不添加 speculative interface、factory、repository、DSL、cache 或配置。
8. 原则上不新建 class，不添加 comments，除非 prompt 要求；新增功能先沿既有模块平行扩展，不补丁式旁挂。

## 验证与交付

1. 每个非平凡 bug fix 或领域分支都需要最小回归测试，且该测试在缺少修改时应失败。
2. 优先运行 touched capability 的聚焦测试，再在交付前运行完整构建。
3. Unix-like 系统使用 `./gradlew`；Windows 使用 `gradlew.bat`。
4. 代码修改的最低验证是 `./gradlew build` 和 `git diff --check`。
5. 运行时机制、命令、mixin、持久化或集成行为变化还要包含 `./gradlew runServer`。
6. 持久化变化要包含 round-trip 和 malformed-input 覆盖。
7. 权限变化要覆盖 Region/Scope、global/player、exact/parent、default 和 extension-key 行为。
8. addon 兼容变化要用 `javap` 或等价 ABI 检查确认 JVM descriptor。
9. 报告跳过的检查和原因；必需验证失败时不要声称完成。

## 审查优先级

1. 优先审查数据丢失、权限绕过、无效归属和 false success reporting。
2. 其次审查编译、二进制兼容、持久化兼容和行为回归。
3. 再审查热路径分配、无界计算、溢出和线程问题。
4. 继续审查 nullable-state protocol、过宽类型、重复和可维护性问题。
5. 最后审查命名和局部风格。
6. 不因偏好级格式问题或 speculative future flexibility 阻塞正确修改。

## 仓库卫生

1. 所有临时需求、执行计划、发现、进度日志和 agent 工作笔记放在 `docs/plan/`。
2. `docs/plan/` 是本地工作状态，不得 staged 或 committed。
3. `logs/` 是运行输出，不得 staged 或 committed。
4. 正式用户或 addon 文档放在 `docs/` 其它位置，可以提交。
5. `README.md` 只聚焦已发布行为和公开用法。
6. staging 前排除 `docs/plan/`、`logs/`、构建产物、IDE 文件和无关用户改动。
