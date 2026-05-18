# 后端发现与决策

## 需求
- 后端建立显式 Tool Registry。
- Planner 统一负责自然语言到结构化 plan 的转换、校验、追问、确认和执行调度。
- Ollama 只输出结构化 plan，不直接执行操作。
- Ollama 不可用、超时、非法 JSON、低置信时必须走确定性兜底或追问。
- 所有写操作必须先返回确认卡片，确认后执行冻结参数快照。
- 普通用户不能执行管理员工具。

## 研究发现
- 存在两条后端 AI 入口：`/api/v1/ai/assistant` 和 `/api/v1/ai/chat`。
- `AiAssistantController` 已有 session、message、confirm、cancel 接口。
- `AiChatController` 仍提供聊天和 SSE stream，`AiChatServiceImpl` 内有字符串意图分流和 LLM 生成答案。
- `AiAssistantActionRegistry` 当前是 actionType 到 handler 的映射，并带字符串 `detectActionType`，不是规格要求的工具元数据注册表。
- `AiAssistantServiceImpl` 已有 session draft、pending execution、确认/取消执行雏形。
- 当前返回结构仍是 `stage/assistantText/result/pendingAction/missingFields`，不是新版 `cards/state` turn 协议。
- 已初步确认现有 handler 覆盖概览、今日安排、日历、会议室查询、会议室详情、管理员待审核/通过/驳回。
- 聊天链路中存在“AI 服务暂时不可用，请稍后重试”类文案风险。
- `AiAssistantSemanticService` 当前先执行规则解析，规则结果置信度足够时直接返回，否则调用 Ollama 解析；Ollama 返回空、超时或非法 JSON 时会回退到规则结果。
- `SpringAiAssistantIntentLlmClient` 已有 `assistant.ai.enabled` 和 `assistant.ai.timeout-ms` 配置，超时或异常返回 null。
- `AiAssistantTurnVO` 当前只有 `stage/assistantText/missingFields/pendingAction/result`，需要补充或迁移到 `role/message/cards/state/turnId`。
- 现有测试已覆盖创建预约确认、取消确认、明天会议查询、非法 LLM JSON fallback、普通用户访问管理员动作、管理员通过/驳回确认。
- 尚缺规格要求的显式 Tool Registry 完整性测试、确认执行参数冻结测试、新版 card 协议测试和关闭 Ollama 后查询兜底的主服务层回归。

## 技术决策
| 决策 | 理由 |
|------|------|
| 优先统一后端主入口到 `/api/v1/ai/assistant` | 避免旧 chat 链路继续把模型失败暴露给助手页面 |
| 复用现有 pending execution 机制并补强 | 已满足用户、会话、参数快照、TTL 的大部分基础 |
| 后端采用兼容式 DTO 演进 | 前端和后端可同一阶段切换到 cards，同时避免旧字段导致已有测试大面积失效 |
| Tool Registry 显式声明权限、读写类型、确认要求和结果卡片类型 | 让 Planner 可以在执行前统一做权限、确认和卡片协议调度 |
| Ollama 结果只作为 plan 输入，低置信或异常继续走 deterministic fallback/clarification | 满足关闭 Ollama 后查询能力仍可用、不向前端暴露消息服务不可用的验收要求 |

## 实现结果
- 新增 `AiAssistantToolRegistry` 和 `AiAssistantToolDefinition`，第一阶段 14 个工具均在注册表中声明。
- 新增 `AiAssistantCardVO`，后端 turn 返回 `role/message/state/cards/turnId`，同时保留旧字段兼容。
- `AiAssistantServiceImpl` 统一处理 Planner 调度、工具定位、权限校验、字段追问、写操作确认卡片、确认后冻结参数执行和结果卡片。
- `AiAssistantIntentParser` 使用 Tool Registry 的 fallback 识别，覆盖“今天有哪些会”“明天有哪些会”“我明天有会吗”“明天有什么安排”“明天有哪些预约”等表达。
- 普通用户请求管理员工具会在执行前返回错误卡片；管理员通过/驳回先返回确认卡片，确认后才调用业务 handler。
- 旧 `AiAssistantActionRegistry` 已删除，现有业务 handler 作为工具 executor 复用，未新增后端依赖。

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| WindowsApps 下 `rg.exe` 无法运行 | 暂用 PowerShell `Get-ChildItem` 和 `Select-String` |

## 2026-05-14 预约选择重复追问问题
- 现象：助手在“取消/修改预约”场景中返回多条预约选择卡片，用户已选择“目标预约”后，仍再次要求选择。
- 根因 1：`ReservationAssistantActionHandler.selectReservation` 在收到 `reservationId` 后，如果 `reservationService.myReservationDetail` 抛出 `BizException`，会移除 `reservationId` 并继续按时间窗口重新匹配列表，导致再次返回同一类“目标预约”补参卡片。
- 根因 2：`ReservationMapper.selectMyReservationDetail` 只允许组织者查看详情，而 `myReservations(scope=all)` 会返回组织者和参与者预约；列表可见但详情不可见，导致参与者预约被选择后进入根因 1。
- 相关风险：取消/修改工具可能把参与者预约当作可操作目标进入确认阶段，最终执行时才失败；评价工具也可能因详情 SQL 拒绝参与者而被误导到重复追问。
- 修复决策：选中的预约不可访问时直接返回可恢复错误，不再丢弃参数并重复追问；取消/修改/评价在进入确认前按 `canCancel/canEdit/reviewed` 做能力过滤；详情 SQL 与列表 SQL 对齐，允许参与者查看详情但 `canEdit/canCancel` 仅组织者为 true。

## 资源
- `E:\project\meeting-room\backend\meeting-room-server\src\main\java\com\llf\controller\AiAssistantController.java`
- `E:\project\meeting-room\backend\meeting-room-server\src\main\java\com\llf\controller\AiChatController.java`
- `E:\project\meeting-room\backend\meeting-room-server\src\main\java\com\llf\service\impl\AiAssistantServiceImpl.java`
- `E:\project\meeting-room\backend\meeting-room-server\src\main\java\com\llf\service\impl\AiChatServiceImpl.java`

## 2026-05-14 管理端会议室设备筛选
- 用户端会议室列表 `RoomServiceImpl.pageRooms` 已支持 `deviceIds` 并复用 `parseDeviceIds`。
- 管理端 `AdminRoomController.page` 当前没有 `deviceIds` 参数，`RoomService.adminPage` 也没有该参数，`RoomServiceImpl.adminPage` 固定向 `buildRoomPage` 传 `null`。
- 管理端设备筛选需要最小后端改动：控制器接收 `deviceIds`，服务接口和实现增加参数，复用现有解析后传给 `buildRoomPage`。
- 已实现：`AdminRoomController.page` 接收 `deviceIds`，`RoomService.adminPage` 签名增加参数，`RoomServiceImpl.adminPage` 复用 `parseDeviceIds` 后透传 mapper。

## 2026-05-14 AI 助手查询意图和预约列表过滤
- 业务无关语句进入 `unknown + confidence < 0.5` 后，`AiAssistantServiceImpl` 会统一返回 `clarificationTurn`，导致“讲个笑话”等非业务输入被继续追问。
- “我今天的会议有哪些 / 我明天的会议有哪些”不包含既有规则中的“有会 / 哪些会 / 预约”等连续片段，规则解析为 `unknown`；线上有 Ollama 时还可能被解析到日历，造成同义问法结果不一致。
- “查看我本周的预约”不包含连续的“我的预约”，也不是“查询预约”，因此落入 `contains 预约` 的创建预约兜底，进入创建补参卡片。
- `ReservationAssistantActionHandler.handleReservationList` 只过滤了 `CANCELLED`，所以 `EXCEPTION`、`REJECTED`、`PENDING` 会出现在“今天有哪些会”这类用户需要参加的会议列表里。
- 修复方式：增加规则侧 `isReservationListQuery`，覆盖“会议有哪些”“本周的预约”等查询表达；保留“今天有啥安排”走今日安排、“下周一上午的安排/明天中午有安排吗”走日历；低置信 unknown 只有业务含义不完整时追问，否则返回能力范围提示；预约列表只展示 `ACTIVE` 和 `ENDED`。

## 2026-05-14 Planner v2 + RAG 升级
- LLM Planner 已切换为语义主路径：`AiAssistantSemanticService` 先调用 `AiAssistantPlannerService`，只有 Ollama 不可用、空响应、非法 JSON、schema 校验失败、未知工具或低置信时才进入规则 fallback。
- 所有 LLM plan 先经过 `AiAssistantPlanValidator` 校验：限制 JSON 字段、校验 `intentType`、`toolName` 是否注册、置信度范围、低于 `0.70` 不执行、写操作歧义不允许直接执行。
- 时间解析补齐固定日期、上周、上周末、下周和“明天9点到11点”区间；修复 `2026-05-15` 被小时正则误解析为 `26:00` 的问题。
- `RoomAssistantActionHandler` 对带时间窗的会议室查询调用推荐接口，未指定人数时使用 `attendees=1`，避免“明天9点到11点有哪些会议室可以用”退化为普通房间列表。
- Session 增加最近工具和实体上下文；`这个会` 可以引用最近明确预约，但 `取消这个会议室` 固定返回追问，不能隐式取消预约或会议室。
- RAG 知识库只覆盖系统规则、帮助说明和越界能力提示，不产生 `pendingAction`，也不直接执行业务；业务操作仍必须通过 Tool Registry。
- 前端只因澄清卡片恢复做最小改动；查询结果、执行结果、错误和 RAG 文本仍只通过聊天气泡展示，只有补参和确认等需要用户操作的内容显示卡片。
- 真实 Ollama 烟测发现 LLM 会输出自然语言字段值，例如 `dateFrom=下周开始日期`、`dateFrom=明天`，这类值会绕过 JSON schema 但在业务 service 中触发时间解析异常。修复方式是在时间解析前清理非法日期/时间字段，并根据原始用户文本重新归一化。
- 真实 Ollama 烟测还发现明显工具错配：会议室可用性问题可能被 plan 成 `calendar.query`，固定日期“有哪些会议”可能被 plan 成全局日历查询。修复方式是在 LLM plan 通过 schema 后再做轻量工具一致性校验，明显错配时进入规则 fallback。

## 2026-05-14 数字会议主题取消预约
- 现象：用户先查询到“111（A201 董事会议室 09:00-10:00）”，再输入“把111的会议取消”时，助手返回“选中的预约已不可用，或你没有权限访问这条预约”。
- 根因：LLM Planner 可能把用户文本中的数字会议主题“111”填入 `reservationId=111`。`ReservationAssistantActionHandler.selectReservation` 优先按 `reservationId` 查详情，详情查不到后直接返回不可用错误，没有回到本人预约列表按标题“111”匹配真实预约。
- 修复决策：如果 `reservationId` 来自自然语言消息且查详情失败，只允许在当前消息能明确匹配本人预约标题或会议室时继续进入选择流程；如果没有明确文本匹配，仍返回不可用错误，避免错误取消其他单条预约。
- 保留边界：用户在补参卡片中手动选择的 `reservationId` 失效时，`message` 为空，仍直接返回不可用错误，不重新模糊匹配。

## 2026-05-14 我的预约列表只显示 3 条
- 现象：助手回复“本周共有 5 条预约”，但后面只列出 3 条。
- 根因：`ReservationAssistantActionHandler.handleReservationList` 文案使用真实数量 `visibleReservations.size()`，但拼接明细时硬编码 `Math.min(3, visibleReservations.size())`，造成总数与明细不一致。
- 修复决策：我的预约列表查询展示本次 service 返回的全部有效预约；其他 handler 的 3 条摘要上限暂不改动，避免扩大范围。

## 2026-05-14 AI 助手创建补参字段
- 现象：创建预约补参卡片仍展示“参会人数”，用户需要手动填写；卡片缺少“所需设备”字段。
- 根因：`ReservationAssistantActionHandler.buildCreateMissingFields` 将 `attendees` 作为必填字段返回，Tool Registry 也把 `attendees` 列为创建预约必填；设备需求 DTO 已存在，但助手补参、推荐和执行链路没有暴露或透传 `deviceRequirements`。
- 修复方式：创建/修改预约补参移除手填 `attendees`，由 `participantUserIds.size() + 1` 推导，未选择参会人时默认 1；新增 `device-requirements` 字段，推荐和创建/修改 DTO 均透传 `deviceRequirements`。
- 补充：`RoomService` 暴露 `deviceOptions()`，助手复用会议室设备选项作为设备需求可选项；`RoomController` 也改为通过 service 返回 locations 和 device options。

## 2026-05-14 AI 助手真实系统清单验证
- 使用真实 8081 后端和 5172 前端按 `完整功能测试清单.md` 第 8 节验证 AI 助手；接口级脚本覆盖 53 个用例，浏览器脚本补测欢迎语、澄清卡片和旧错误文案。
- 8.4 通过临时 18082 后端加 `--assistant.ai.enabled=false` 验证，关闭 LLM 主路径后“明天有哪些会”仍返回预约查询结果，没有“当前消息服务暂时不可用”。
- 发现 9 个不符合清单预期的点：
  - `帮我看看明天的` 直接查询明天日历，而不是澄清追问。
  - `今天概览怎么样` 被 RAG 帮助路由接走，未调用系统概览工具。
  - `A101 会议室详情` 不能用房间编码直接命中，仍要求选择 `roomId`。
  - `取消我明天下午的预约` 在没有用户提供原因时直接进入确认，和清单“先补原因”的预期不一致。
  - 管理员 AI 工具 8.41、8.43、8.44、8.45、8.46 全部返回“该操作仅管理员可用”，但同一 admin token 可正常访问 `/api/v1/admin/reservations`。
- 管理员 AI 工具失败的直接原因指向角色判断不一致：`AuthInterceptor` 和 `AdminReservationAssistantActionHandler.isAdmin` 接受 `2/admin/ADMIN`，但 `AiAssistantToolRegistry.hasPermission` 只接受 `ADMIN`。
- 测试期间创建了 `AI实测...` 开头的夹具预约：`1011` 已创建后修改并取消，`1012`、`1013`、`1014`、`1015` 仍为待审核，用于后续复现管理员 AI 权限问题。

## 2026-05-14 AI 助手测试清单补充审查
- `完整功能测试清单.md` 第 8 节原本覆盖 14 个工具和 Planner v2 五条失败样本，但缺少若干容易回归的边界：RAG 管理规则/越界、混合规则与操作请求、Ollama 禁用后的复杂 fallback、更多自然时间表达、预约状态过滤、发起/参与范围、数字标题误当 ID、确认参数冻结、取消写操作确认、创建冲突/容量/设备保护、AI 参会人候选保护、管理员按预约号审核、审核冲突重校验、新会话上下文清空和前端信息型卡片隐藏。
- 已将这些风险补充为 8.54-8.74，并把 8.23 的“参会人数”描述修正为当前设计：用户不手填参会人数，系统从参会人列表推导。

## 2026-05-14 AI 助手真实系统 9 项失败修复
- `帮我看看明天的` 的根因是 LLM Planner 高置信输出日历查询后缺少“模糊时间但无业务对象”的二次校验；已在语义一致性校验中拒绝该类 plan，让规则 fallback 返回澄清卡片。
- `今天概览怎么样` 的根因有两层：路由层会被“怎么”类帮助词吸走，LLM 也可能把“概览”误 plan 成今日安排；已让明确概览词优先进入业务操作，并要求概览请求只能接受 `overview.summary.query`。
- `A101 会议室详情` 的根因是会议室详情只按 `roomId/name` 粗匹配，没有处理 `roomCode=R-A101` 和用户短写 `A101`；已增加房间编码归一化匹配，兼容 `A101`、`R-A101`、房间名称和带“查看/详情/会议室”的表达。
- `取消我明天下午的预约` 的根因是取消原因解析把任何“取消”文本自动补成默认原因；已改为只接受显式“原因/理由/因为/由于”或“冲突”关键词，否则返回 `cancelReason` 补参卡片。
- 管理员 8.41/8.43/8.44/8.45/8.46 的根因是 `AiAssistantToolRegistry.hasPermission` 只识别 `ADMIN`，与系统登录角色 `2/admin/ADMIN` 不一致；已统一管理员角色判断。
- 真实脚本重复运行后 8.21 暴露预约详情会把 `CANCELLED` 的历史测试预约也算作“明天下午”候选；已在未指定状态的预约选择入口排除 `CANCELLED/REJECTED/EXCEPTION`，保留 `PENDING/ACTIVE/ENDED`。
- 验证结果：后端助手目标测试 80 个通过，meeting-room-server 模块测试 174 个通过；真实系统脚本最新结果为 54 通过、0 失败、1 跳过，跳过项是脚本自身未关闭 Ollama 的 8.4。

## 2026-05-14 AI 助手最新清单真实系统全量验证
- 使用真实 8081 后端按 `完整功能测试清单.md` 第 8 节 8.1-8.74 重跑接口级验证；脚本为 `codex-work/assistant-real-system-full-test.cjs`，结果写入 `codex-work/assistant-real-system-full-results.json`。
- 8081 正常 AI 路径结果：73 通过、3 失败、2 跳过。跳过项中 8.4/8.57 已通过临时 18082 禁用 AI 后端补测，8.72 因真实公开 API 创建阶段会先阻止冲突待审单，无法不改数据库构造审核确认时冲突夹具。
- 18082 禁用 AI 路径结果：`codex-work/assistant-noai-fallback-full-test.cjs` 验证 8.4 和 8.57 均通过，禁用 `assistant.ai.enabled=false` 后仍能回答“明天有哪些会”“2026-05-15有哪些会议”“明天9点到11点有哪些会议室可以用”。
- 本轮确认 3 个真实功能缺口：
  - 8.62 发起/参与范围查询：`我发起了哪些会议` 和 `我参与了哪些会议` 返回同一份 `scope=all` 结果，后端 `ReservationAssistantActionHandler.handleReservationList` 未消费发起/参与 scope。
  - 8.65 确认参数冻结：进入创建确认后，同会话发送“查看我本周的预约”，再点击原确认卡片，返回“这次待执行动作已经失效”，说明 pending execution 会被后续消息失效化，不能满足“原确认卡片冻结参数仍可执行”。
  - 8.71 管理员按预约号审核：输入真实预约号 `RSV1778752532878793` 时，助手把其中数字解析为预约 ID `1778752532878793`，没有映射到真实数据库 ID `1064`。

## 2026-05-14 AI 助手真实系统 3 项失败修复
- 8.62 根因：语义层能识别 `reservations.list`，但 draft 中的 `targetScope` 固定为 `mine`，handler 查询时又固定传 `scope=all`；已增加发起/创建/组织 -> `organizer`，参与/参加 -> `participant` 的确定性 scope 解析，并在 LLM 错路由时强制回退到预约列表工具。
- 8.65 根因：新消息进入业务 action 时会清除 session 当前 pending execution，导致旧确认卡片的 executionId 不再能确认；已改为 pending execution 按 executionId 在 TTL 内保留，只有确认、取消或过期后失效，后续对话只重置当前会话进度，不删除冻结参数快照。
- 8.71 根因：管理员审核文本中的 `RSV177...` 先被数字正则抽成数据库 ID；已优先解析 `reservationNo`，并通过管理员待审核列表按预约号精确定位真实数据库 ID，再生成确认卡片。
- 验证结果：后端助手目标测试 84 个通过，meeting-room-server 模块测试 178 个通过；真实系统全量脚本中 8.62、8.65、8.71 均通过。
- 非本轮遗留：真实系统全量脚本仍有 8.38-8.40 评价用例失败，当前表现为固定历史预约已不可评价；这不属于本次 3 项修复范围，未改业务数据或扩大修复范围。

## 2026-05-14 AI 助手当前通过性复测
- 后端助手目标测试通过：84 个测试、0 失败。
- 后端 `meeting-room-server` 模块测试通过：178 个测试、0 失败。
- 真实 8081 接口全量脚本仍未完全通过：73 通过、3 失败、2 跳过。
- 失败项为 8.38、8.39、8.40 评价链路；实际返回“这条预约当前不能评价。只有已结束且尚未评价的预约可以评价。”
- 数据复核：张三当前可访问的 3 条已结束预约 `RSV-TEST-1002`、`RSV-TEST-1001`、`RSV-TEST-1007` 都已经存在 `reservation_review` 记录，因此“最近结束的会/已通过部门例会”不会进入评价确认。
- 结论：8.62、8.65、8.71 已通过；当前未全绿的直接原因是评价测试依赖固定历史数据而没有准备“已结束且未评价”的新夹具。`完整功能测试清单.md` 已将 8.38-8.40 标为 `[ × ]`。

## 2026-05-14 AI 助手创建预约设备后置补参
- 用户反馈设备不应出现在首张创建预约补参卡，而应作为后续补参卡出现，然后再筛选会议室。
- 根因：`ReservationAssistantActionHandler.buildCreateMissingFields` 在任何创建预约补参阶段都追加 `deviceRequirements`，导致前端只能把设备需求放在基础字段同一张卡里；基础字段补齐后 handler 又直接进入 `roomId` 推荐卡，没有一个“已问过设备需求”的状态边界。
- 修复方式：创建预约基础缺字段阶段只返回主题、日期、开始时间、结束时间和参会人；当这些字段齐全且 draft 中还没有结构化 `deviceRequirements` 列表时，返回仅包含设备需求的补参卡。
- 前端提交空设备需求时会传 `deviceRequirements: []`；后端据此判断用户已跳过设备补参，然后调用推荐接口筛选会议室。非空设备需求同样会进入 `reservationService.recommend()` 的 `ReservationRecommendationDTO.deviceRequirements`。
- 已同步调整后端测试：确认阶段用例需要带 `deviceRequirements: List.of()` 才能表示设备补参已完成。

## 2026-05-15 紧急会议抢占调配第一版
- 规格限制不允许新增数据库字段或抢占关系表；后端需要复用预约 `ACTIVE/CANCELLED` 状态、`cancel_reason`、`remark` 和通知表达调配结果。
- 现有普通创建 `ReservationServiceImpl.create` 会在冲突时拒绝，并创建 `PENDING` 预约；紧急会议需要管理员确认后直接成为 `ACTIVE`，可复用插入后立即置为 `ACTIVE` 的 mapper 能力，但不能走普通待审核用户流程。
- 现有推荐逻辑 `reservationService.recommend()` 会自动排除当前时间段冲突会议室并校验容量/设备；抢占调配寻找替代会议室时可以复用相同候选规则，并排除紧急会议目标会议室。
- 现有通知服务只提供预约创建/更新/取消/审核等固定文案；紧急调配需要新增面向指定用户的系统通知方法，但仍写入现有通知表。
- AI 助手已有管理员权限判断和写操作确认冻结机制；紧急抢占工具应注册为管理员写工具，普通用户在 Tool Registry 权限处被拒绝。
- 已实现管理员 `/api/v1/admin/emergency-reservations/preview` 与 `/confirm`：预览只计算方案，确认重新计算并用预览指纹校验占用未变化，事务内完成可调配预约换房、不可调配预约取消、紧急会议创建并置为 `ACTIVE`、通知发送。
- 第一版替代会议室只做“同时间换会议室”，候选排除紧急会议目标会议室和本次方案已使用会议室，并校验容量、状态、时间冲突和原预约设备需求。
- 没有可替代会议室时，冲突预约改为 `CANCELLED`，`cancelReason` 写入紧急调配原因；可替代时追加系统调配说明到原预约 `remark`。
- AI 新增 `admin.emergency_reservations.preview` 和 `admin.emergency_reservations.confirm` 工具；普通用户请求抢占会被 Tool Registry 权限拒绝，管理员请求缺字段时先补参，字段齐全后返回确认卡片，确认后才调用后端确认服务。
- 预览指纹为内存短 TTL 方案，符合“不新增表字段”的约束；服务重启、预览过期或占用变化后确认会要求重新预览。

## 2026-05-17 后端整理边界检查
- 检查后端公开接口和测试引用后，旧 `/api/v1/ai/chat` 链路仍有 `AiChatControllerTest`、`AiChatServiceImplTest` 和 AI 知识服务测试覆盖，不能仅因前端主助手不再调用就删除。
- 紧急会议、通知发布、AI Planner/RAG 等后端新增能力均被控制器、服务或测试引用；本轮不做后端代码删除，避免改变已有公开 API。

## 2026-05-18 演示数据库重置
- 当前后端连接的真实库为 `${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:meeting_system}`，本次直接重置该库业务数据。
- 重置前已用 `mysqldump --single-transaction` 备份到 `codex-work/db/database_backup.sql`。
- 表结构确认：预约状态在库内为 `1=PENDING`、`2=ACTIVE`、`3=ENDED`、`4=CANCELLED`、`5=REJECTED`、`6=EXCEPTION`；会议室状态为 `1=AVAILABLE`、`2=MAINTENANCE`；设备状态为 `1=ENABLED`、`0=DISABLED`。
- 新种子脚本为 `codex-work/db/seed_meeting_system.sql`，导入前先创建临时库跑同一份表结构和种子脚本，验证通过后再导入正式库。
- 新数据覆盖：8 个用户、8 间会议室、7 类设备、20 条会议室设备绑定、31 条预约、59 条参会人关系、41 条预约设备关系、4 条评价、16 条通知。
- 预约数据时间集中在 2026-05-18 到 2026-05-27，并保留 2026-05-13 到 2026-05-17 的已结束会议，便于验证“上周我参加了哪些会议”和会后评价。
- 张三视角重点覆盖：2026-05-20 可见 9 条预约；上周相关 4 条；下周相关 4 条；已结束未评价 2 条；2026-05-19 09:00-11:00 可用会议室为 A102、C301、C302、E401。

## 2026-05-18 GitHub 根 README 文档
- 根 README 需要避免直接暴露远程数据库配置细节，文档中只说明应配置自己的 MySQL 连接，并提示不要提交生产凭据。
- 后端 `application.yml` 默认端口为 8080，但本项目开发启动脚本和用户要求使用 8081；README 中明确说明默认配置和开发命令的差异。
- AI 助手 README 描述应强调受控业务执行器：Tool Registry、LLM Planner、RAG、确定性 fallback、写操作确认和冻结参数，而不是自由聊天。
- 仓库根目录没有 `LICENSE` 文件，README 中只提示发布前补充根许可证，不把整个项目声明为 MIT。
