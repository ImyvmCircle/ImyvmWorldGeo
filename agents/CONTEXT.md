# Agent 上下文

本文件是本项目唯一总上下文入口。prompt 中引用时写：`先读 agents/CONTEXT.md`。

## 读取顺序

1. 本文件。
2. 产出或改写正式文字时读取 `agents/WRITING_STYLE.md`。
3. 执行开发任务时读取 `agents/instructions/development.instructions.md`。
4. 按开发规则索引读取相关专项 instruction。

## 执行纪律

1. 说什么执行什么。
2. 没说的不执行。
3. 不确定就问，别猜；发现缺失文件、依赖 API、服务器设定、项目意图或设计框架时先询问。
4. 给验收标准，别给步骤。
5. 不主动使用 git；prompt 要求 git、提交、推送、拉取或发布时，先同步远端再操作。
6. 完成后给一条符合项目既有格式的 commit 描述建议。
7. 不自行更新 tag、版本号或发布版本。
8. 原则上不新建 class，不添加 comments，除非 prompt 要求。

## 规则选择

1. `agents/instructions/development.instructions.md` 只作为索引。
2. 具体规则以相关专项 instruction 为准。
3. `agents/prompts/archive/` 存在时只作为历史任务参考，不自动作为当前规则。
4. 不适用的规则不强行套用；不确定是否适用时询问。
