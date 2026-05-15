---
applyTo: "README.md,agents/**/*.md"
---

# 文档与验证规则

1. 正式文字先读取 `agents/WRITING_STYLE.md`。
2. 用户侧机制变化同步 README 相关模块和 changelog。
3. 不自行新建版本，不自行更新版本号。
4. changelog 描述保持简洁。
5. 调用外部或并列项目 API 时，以 Gradle 解析的已发布制品为准。
6. 不主动使用 git；prompt 要求 git、提交、推送、拉取或发布时，先同步远端，再遵循既有 commit 格式，不添加 co-author trailer。
7. 完成后给一条符合项目既有格式的 commit 描述建议。
8. 代码修改使用 `./gradlew build`；运行时机制、命令、mixin、持久化或集成行为变化包含 `./gradlew runServer`。
