## 项目线程规则

1. 在本项目下开启每一个新线程时，必须先阅读项目根目录的 `AGENTS.md` 文件。
2. 在本项目下开启每一个新线程时，必须使用 [$planning-with-files-zh](C:\Users\Acer\.agents\skills\planning-with-files-zh\SKILL.md) 阅读上下文文档。前端线程阅读 `frontend/` 目录下的上下文文档，后端线程阅读 `backend/` 目录下的上下文文档。
3. 如果项目根目录缺少 [$planning-with-files-zh](C:\Users\Acer\.agents\skills\planning-with-files-zh\SKILL.md) 需要的 `task_plan.md`、`findings.md`、`progress.md` 任一文件，必须使用该 skill 在前端或者后端目录下创建缺失文件后再继续复杂任务。
4. 每一个需求完成之后，由 agent 根据任务复杂度、后续维护价值和上下文延续需要，自行决定是否使用 [$planning-with-files-zh](C:\Users\Acer\.agents\skills\planning-with-files-zh\SKILL.md) 记录上下文。

## 项目经理 agent 专用规则

1. 当用户向项目经理 agent 提出需求时，项目经理 agent 必须持续追问必要问题，直到 100% 理解用户的想法、目标、边界、验收标准和执行约束后，再给出对应的 prompt。
2. 本规则只在与项目经理 agent 对话时生效，不适用于前端、后端或其他执行型 agent。

## 依赖使用规则

1. 在实现需求时，可以随意添加依赖，只要该依赖能够让需求的实现更加方便。
2. 添加依赖时应说明该依赖用于解决什么问题，并避免引入与当前需求无关的依赖。

## 前端截图检查规则

1. 进行前端截图检查、真实浏览器视觉验证或 UI 流程调试时，默认使用 [$playwright-local](C:\Users\Acer\.codex\skills\playwright-local\SKILL.md)。
2. 在 `E:\project\meeting-room` 中使用 Playwright 时，临时脚本、截图和 traces 放到 `E:\project\meeting-room\codex-work`。
3. Playwright 命令使用 `D:\PowerShell-7.6.1\pwsh.exe`、UTF-8 输出和 Microsoft Edge，避免使用系统 PowerShell 或临时安装的 `@playwright/cli`。

## 前端 UI 参考资源

1. 设计页面或组件时，可以参考以下 UI/动效资源的组件代码、交互方式和视觉表达：
   - Animate UI：https://animate-ui.com/
   - Kokonut UI Card Flip：https://kokonutui.com/docs/cards/card-flip
   - React Bits：https://reactbits.dev/
   - Design Spells：https://www.designspells.com/
2. 参考这些资源时，应优先适配本项目已有技术栈、视觉风格和交互规则，不直接堆叠无关效果。
3. 如果借鉴组件代码，需要先确认依赖、许可证和实现成本；React 组件示例应转换为符合本项目 Vue 3 + Vite 的实现。
