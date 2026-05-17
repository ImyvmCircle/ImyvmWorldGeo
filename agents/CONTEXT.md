# Agent 上下文

本文件是本项目唯一总上下文入口。prompt 中引用时写：`先读 agents/CONTEXT.md`。执行任何任务，必须根据读取索引进行读取，也必须阅读本项目专有规则。

## 读取索引

1. 本文件。
2. 产出或改写正式文字时读取 `agents/WRITING_STYLE.md`，并完整执行其第四节写后链路；缺标题审定、机械扫描、人工判退、CV/TTR 或 AIGC-X 段落结果，任务未完成。
3. 执行开发任务时读取 `agents/instructions/development.instructions.md`。
4. 按开发规则索引读取相关专项 instruction。

## 本项目专有规则

1. 执行修改任务前先检查远端仓库主分支状态，并在不发生冲突时尽可能同步；冲突交由用户裁决。
2. 不主动使用 git；prompt 要求 git、提交、推送、拉取或发布时，先同步远端再操作。
3. 原则上不新建 class，不添加 comments，除非 prompt 要求。

## 规则选择

1. `agents/instructions/development.instructions.md` 只作为索引。
2. 具体规则以相关专项 instruction 为准。
3. `agents/prompts/archive/` 存在时只作为历史任务参考，不自动作为当前规则。
4. 不适用的规则不强行套用；不确定是否适用时询问。
