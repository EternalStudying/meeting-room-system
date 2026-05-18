# 前端发现与决策

## 需求
- 前端重做为聊天主界面 + 工具卡片。
- 支持文本、查询结果、参数补充、确认、执行结果、追问、错误卡片。
- 接口异常或后端可恢复错误时，不展示“当前消息服务暂时不可用”。
- 用户确认写操作后调用确认执行接口。

## 研究发现
- 前端 README 显示当前技术栈为 Vue 3 + Vite + TypeScript + Element Plus + Pinia + Vitest。
- 已定位助手相关文件：
  - `frontend/src/common/apis/assistant/index.ts`
  - `frontend/src/common/apis/assistant/type.ts`
  - `frontend/src/pages/assistant/index.vue`
  - `frontend/src/pinia/stores/assistant.ts`
  - `frontend/tests/common/apis/AssistantApi.test.ts`
  - `frontend/tests/pages/Assistant.test.ts`
  - `frontend/tests/pages/AssistantStyles.test.ts`
- 尚需继续读取具体实现后才能确定精确修改范围。
- 当前前端已调用 `/api/v1/ai/assistant`，并已有 collect、confirm、result 面板。
- 当前前端类型仍是 `AssistantStage` 和 `AssistantTurnPayload.stage`，没有 `cards/state/turnId/role/message`。
- 当前页面 `sendMessage` 捕获异常时会展示“当前消息服务暂时不可用，你可以稍后重试。”，与规格验收冲突。
- 当前测试围绕旧 stage 协议和现有样式断言，需要同步改成 card 协议断言。

## 技术决策
| 决策 | 理由 |
|------|------|
| 优先让助手页面调用新版 `/api/v1/ai/assistant` turn/cards 协议 | 符合规格，避免旧聊天接口异常文案外泄 |
| 前端卡片类型用 TypeScript 显式建模 | 减少卡片渲染和表单提交的运行时歧义 |
| 复用现有页面的 collect/confirm/result 交互骨架 | 第一阶段不做无关视觉重构，集中改协议和卡片能力 |
| 接口异常时追加本地 error card | 满足可恢复提示要求，并避免显示“当前消息服务暂时不可用” |
| 不新增前端依赖 | 当前 Vue、Pinia、Element Plus、Vitest 和 Playwright 验证链路已够用 |

## 实现结果
- `assistant/type.ts` 切换为 `AssistantTurnState`、`AssistantCardType`、`AssistantCard` 和新版 `AssistantTurnPayload`。
- `assistant` Pinia store 改为保存 `state/cards`，并从 `field_form` 卡片初始化补参表单状态。
- `pages/assistant/index.vue` 改为根据 cards 渲染文本、查询结果、参数补充、确认、执行结果、追问和错误卡片。
- 写操作确认卡片仍调用确认/取消接口，执行结果卡片支持 deep link 跳转。
- `sendMessage/startNewSession/confirm/cancel` 的异常提示均落为本地 error card，不再展示旧的“当前消息服务暂时不可用”。
- 测试已改为断言新版卡片协议，覆盖查询结果卡、参数补充卡、确认卡、执行结果卡和异常错误卡。

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| WindowsApps 下 `rg.exe` 无法运行 | 暂用 PowerShell `Get-ChildItem` |

## 资源
- `E:\project\meeting-room\frontend\README.zh-CN.md`
- `E:\project\meeting-room\frontend\src\common\apis\assistant\index.ts`
- `E:\project\meeting-room\frontend\src\common\apis\assistant\type.ts`
- `E:\project\meeting-room\frontend\src\pages\assistant\index.vue`
- `E:\project\meeting-room\frontend\src\pinia\stores\assistant.ts`

## 2026-05-14 管理端会议室管理修复
- 管理端会议室页位于 `frontend/src/pages/admin/rooms/index.vue`，新增弹窗位于 `frontend/src/pages/admin/rooms/components/RoomUpsertDialog.vue`。
- 前端 `RoomQueryRequestData` 已有 `deviceIds?: string`，但管理端会议室页当前没有设备筛选 UI，也没有把设备筛选传给 `getAdminRoomListApi`。
- 新增会议室弹窗当前只维护房间基础信息，不包含设备选择；设备绑定只能创建后通过独立“设备绑定”弹窗完成。
- 重复会议室编码后端错误为 `roomCode already exists`；前端全局 axios 会先提示一次，页面 catch 又提示“会议室保存失败，请稍后重试”，导致双提示且原因不明确。
- 设备筛选若要真实生效，后端管理端 `/api/v1/admin/rooms` 也需要接收并透传 `deviceIds`，否则前端传参会被忽略。
- 实现后管理端筛选区新增设备多选，查询会向分页列表和全量筛选列表同时传 `deviceIds`。
- 新增/编辑会议室弹窗新增“会议室设备”数量选择，创建成功后使用返回的房间 id 调用设备绑定接口。
- 分页切换时立即清理径向菜单浮层，避免旧浮层拦截后续卡片点击。
- 创建/编辑会议室接口启用本地错误处理，页面只展示一次明确错误；`roomCode already exists` 映射为“会议室编码已存在，请更换编码”。

## 2026-05-14 管理端会议室分页详情二次修复
- 用户补充触发对象为自建“阶梯会议室”。数据库查询确认该房间 `id=106`、`room_code=A401`、`description=NULL`、未绑定设备，位于当前排序后的第 2 页。
- 第一次修复只验证了普通第 2 页卡片，未覆盖空描述自建房间。真实根因不是径向菜单残留，而是点击详情后 `selectedRoomWarnings` 调用 `room.description.trim()`，空描述触发运行时异常，导致抽屉不渲染并打断后续交互。
- 修复方式：管理端会议室列表数据进入页面状态前调用 `normalizeRoomItem`，将 `description`、`maintenanceRemark`、`devices`、`deviceCount`、`deviceBindingSummary` 和历史数字状态归一化为前端稳定字段。
- 新增回归测试覆盖：分页到第 2 页后打开 `description: null` 的“阶梯会议室”，详情可打开并显示“未绑定设备 / 缺少空间说明”。
- Playwright 冒烟脚本已更新为使用 `阶梯会议室 + description=null` 的数据，验证第 2 页点击详情链路。

## 2026-05-14 管理端会议室弹窗与删除流程修复
- 转维护动作原先直接调用状态接口，并把当前 `room.maintenanceRemark` 原样提交；可用房间通常为空备注，所以不会要求管理员填写维护原因。
- 新增/编辑会议室选择“维护中”时虽然展示维护备注字段，但保存前没有强制校验，仍可能保存空维护备注。
- 设备绑定弹窗底部按钮继承了通用 `.ghost-button { width: 100%; }`，导致取消按钮和保存按钮尺寸不一致；改为弹窗 footer 内等宽按钮并靠右排列。
- 删除会议室原先使用 `ElMessageBox.confirm`，在当前管理页样式/布局中出现位置异常；改为与页面其它弹窗一致的 `el-dialog align-center`。
- 后端删除有预约会议室返回 `room has related reservations and cannot be deleted`；删除 API 未启用 `silentError`，导致 axios 全局提示和页面 catch 提示重复出现。现在删除接口本地处理错误，并将该英文错误映射为“该会议室已有预约记录，暂不允许删除”。

## 2026-05-14 管理端设备管理错误提示与删除弹窗修复
- 后端设备重复编码错误为 `deviceCode already exists`。设备新增/编辑 API 未启用 `silentError`，axios 全局提示一次，页面 `submitDeviceForm` catch 又提示“设备保存失败，请稍后重试”，导致双提示且原因不明确。
- 修复方式：设备新增、编辑、删除接口启用本地错误处理；设备页新增 `getDeviceSaveErrorMessage`，将重复编码映射为“设备编码已存在，请更换编码”；axios 全局映射也补充该文案。
- 删除设备原先仍使用 `ElMessageBox.confirm`，和会议室删除弹窗问题同源。现在改为页面内 `device-delete-dialog`，使用 `align-center`，展示设备名称、删除说明和取消/确认删除按钮。

## 2026-05-14 登录验证码错误提示修复
- 后端验证码错误枚举位于 `backend/meeting-room-common/src/main/java/com/llf/result/ErrorCode.java`，错误验证码返回 `captcha invalid`，验证码过期返回 `captcha expired`。
- 前端登录接口 `loginApi` 复用全局 `request`，错误文案来自 `frontend/src/http/axios.ts` 的 `normalizeErrorMessage`。
- 当前 `normalizeErrorMessage` 没有验证码相关映射，英文 `captcha invalid` 不含中文，会落到默认“操作失败，请稍后重试”。
- 修复方式：在登录鉴权错误附近补充 `captcha invalid` -> “验证码错误”、`captcha expired` -> “验证码已过期，请重新获取”，并用 `tests/http/AxiosAuth.test.ts` 覆盖登录验证码错误不触发登出刷新。

## 2026-05-14 AI 助手欢迎语重复渲染修复
- 后端 session turn 同时返回 `message` 和一张同文案的 `text` card；助手页面先渲染主消息气泡，再渲染 cards，因此欢迎语会显示两次。
- 修复方式：前端渲染 cards 前过滤掉“`text` 卡片内容与当前 turn 主消息完全相同”的卡片，保留主消息气泡；其它查询结果、参数补充、确认和执行结果卡片不受影响。
- 回归测试调整为断言初始化欢迎语只出现在主消息气泡中，不再额外渲染同文案 `.assistant-card.is-text`。

## 2026-05-14 AI 助手非交互卡片隐藏
- 用户要求 AI 助手内只有需要用户添加或补充时才出现卡片，其它内容不需要额外卡片。
- 当前页面 `visibleTurnCards` 只过滤重复 `text` 卡片，仍会渲染 `query_result`、`error`、`execution_result` 等信息型卡片，导致聊天气泡和卡片重复表达同一结果。
- 修复方式：`visibleTurnCards` 改为交互卡片白名单，只返回带字段的 `field_form` 和带待执行动作的 `confirmation`。
- 写操作确认卡片仍保留，因为后端要求写操作先确认后执行；查询结果、异常提示和执行结果只通过聊天气泡展示。

## 2026-05-14 Planner v2 + RAG 助手卡片回归
- Planner v2 要求 `取消这个会议室` 必须追问，后端会返回 `clarification` 卡片；该卡片属于需要用户继续补充的信息，应在前端显示。
- RAG 系统知识回答是纯说明，不需要用户补参或确认；前端继续隐藏 `text` 卡片，只显示聊天气泡，避免重复展示。
- 本轮前端改动仅限 `visibleTurnCards` 白名单和澄清卡片模板，没有改动助手整体布局和 API 协议。
- 接口异常路径仍展示本地可恢复提示，不使用“当前消息服务暂时不可用”。

## 2026-05-14 AI 助手补参表单字段
- 现象：创建预约补参卡片展示“参会人数”，参会人选择后在后续回合看起来会被自动清空，且没有“所需设备”输入。
- 根因 1：通用 `field_form` 直接渲染后端返回的所有字段，`attendees` 被当成普通 number 字段。
- 根因 2：所有历史补参卡片都继续渲染并绑定同一个全局 `activeFieldValues`；提交后 store 清空/切换活动状态，旧卡片仍在页面上显示空值。
- 根因 3：前端助手类型和模板没有 `device-requirements` 字段类型，无法渲染设备需求数组。
- 修复方式：隐藏 `attendees` 字段并在提交时按 `participantUserIds.length + 1` 推导；新增设备需求数组类型和设备选择/数量控件；`field_form`、`confirmation` 和 `clarification` 只显示当前活动助手 turn。

## 2026-05-14 AI 助手补参卡片参会人与设备后置
- 单个参会人显示不稳定的根因是远程多选只依赖搜索结果缓存；当字段自带 `options/value` 或选项缓存被清理时，Element Plus 只能显示数字 ID。修复方式：把 `field.options` 也归一化为用户选项，并在更新选择值时保留已选项标签。
- 补参字段外层使用 `<label>` 包裹 Element Plus 远程多选和选择器，点击卡片其它区域会触发复杂控件的隐式 label 行为；已改为普通 `.collect-field` 容器，字段名仍用文本展示。
- “添加设备”无效的直接原因是首行设备需求只是展示用虚拟空行，点击添加时把虚拟空行落为真实空行，行数看起来没有变化。修复后保留设备需求草稿行，空状态点击添加会显示第二行。
- 设备需求空提交需要传 `deviceRequirements: []`，否则后端无法区分“用户已跳过设备补参”和“还没问过设备需求”。前端现在对设备需求字段提交空数组，摘要仍不展示空设备项。
- 创建预约首张补参卡只收基础字段和参会人；设备需求由后端后续 `field_form` 卡片返回，再进入会议室推荐。

## 2026-05-15 紧急会议抢占调配第一版
- 管理端预约管理页和日历页是本轮前端入口；普通用户预约页不应展示任何“抢占/紧急会议”入口。
- 前端新增字段“是否紧急会议 / 允许抢占已有预约 / 紧急原因”只用于请求预览/确认，不要求后端新增数据库字段。
- 有冲突且允许抢占时，前端应展示抢占调配确认弹窗；取消弹窗只关闭预览状态，不调用确认接口。
- 管理员确认后需要刷新当前列表/日历数据，避免页面仍展示被抢占前的会议室占用。
- 紧急会议复用 `ReservationCreateDialog`，通过 `emergency` prop 切换标题、按钮文案、紧急原因和抢占开关；普通预约创建路径保持原接口不变。
- 管理端预约审核页新增“创建紧急会议”，打开时加载可预约会议室；确认成功后重新拉取管理员预约列表。
- 预约日历页仅管理员角色展示“新建紧急会议”，普通用户角色不展示；确认成功后重新加载日历和我的预约列表。
- 抢占调配确认弹窗展示紧急会议摘要、被影响预约、调配动作和通知对象；取消弹窗不调用确认接口。
- Playwright 页面验证使用真实 5172 Vite 前端加统一 API mock，覆盖管理员入口、普通用户隐藏、确认弹窗、预览/确认请求参数和确认后刷新调用。

## 2026-05-15 预约日历页删除管理员紧急会议入口
- 用户要求管理员登录时，预约日历页面删除“添加/新建紧急会议”按钮。
- 预约日历页入口位于 `frontend/src/pages/calendar/index.vue` 的 `.emergency-calendar-button`，依赖 `isAdminUser` 计算属性，并额外挂载一个 `emergency` 模式的 `ReservationCreateDialog`。
- 修复方式：从预约日历页删除紧急会议按钮、管理员角色判断、日历页紧急会议弹窗状态和提交回调；普通日历选中时间段创建普通预约的流程不变。
- 管理端预约审核页 `frontend/src/pages/admin/reservations/index.vue` 的 `.emergency-admin-button` 保留，紧急会议创建入口仍在管理端预约审核页。

## 2026-05-15 通知发布迁移到顶部铃铛弹层
- 规格来源：`docs/superpowers/specs/2026-05-15-admin-notification-publish-dialog-design.md`。
- 现有通知发布表单集中在 `frontend/src/pages/admin/notifications/index.vue`，调用 `publishAdminNotificationApi`，字段为发布类型、接收范围、通知标题、通知内容。
- 顶部铃铛弹层位于 `frontend/src/common/components/Notify/index.vue`，原先只负责摘要、分类列表、标记已读和跳转，没有角色判断和管理员发布入口。
- 本轮新增 `frontend/src/common/components/Notify/AdminNotificationPublishDialog.vue`，持有发布表单状态和校验；标题空提示“请输入通知标题”，内容空提示“请输入通知内容”。
- 发布成功后弹窗组件提示“已发布给 N 个接收人”、清空表单、关闭弹窗，并向父组件发出 `published`；Notify 组件收到后刷新 `getNotificationSummaryApi` 和当前分类 `getNotificationPageApi`。
- 发布失败时弹窗保持打开，表单内容不清空，提示“通知发布失败，请稍后重试”。
- admin 判断使用 `useUserStore().roles`，只有角色小写后等于 `admin` 时显示铃铛弹层“发布通知”按钮。
- `frontend/src/router/index.ts` 已移除动态路由 `AdminNotifications`，管理端侧边栏不再展示“通知发布”独立菜单入口。
- 2026-05-17 整理时确认旧页面只剩测试和文档引用，已删除 `frontend/src/pages/admin/notifications/index.vue` 与 `frontend/tests/pages/AdminNotifications.test.ts`，通知发布能力只保留顶部铃铛弹窗。

## 2026-05-17 前端低风险无用文件整理
- 旧 `AdminNotifications` 独立页面已无路由入口，发布逻辑已经迁移到 `AdminNotificationPublishDialog`，可删除旧页面和旧页面测试。
- `SearchMenu`、`Screenfull`、`ThemeSwitch` 未被导航栏、布局或页面引用；删除后只剩 `showThemeSwitch` 配置迁移测试中的旧字段过滤断言。
- `useFetchSelect`、`useFullscreenLoading`、`usePagination`、`usePany`、`useWatermark`、`common/utils/permission.ts` 只在自身定义中出现，不参与当前业务。
- 旧 dashboard 角色首页 `Admin.vue`、`Editor.vue` 不再被概览路由使用；当前概览页直接渲染 `frontend/src/pages/dashboard/index.vue`。
- `screenfull` 仅由已删除的 `Screenfull` 组件引用，已从 `package.json` 和 `pnpm-lock.yaml` 移除。

## 2026-05-15 预约创建弹窗左右面板等高
- 问题位于 `frontend/src/components/ReservationCreateDialog.vue` 的 `.reservation-workbench` 双栏布局；grid item 默认可拉伸，但内部 `.panel-shell` 只是 `height: 100%`，在当前弹窗内容高度下没有稳定承接父级拉伸。
- 第一次修正只让外层面板等高，但 `.recommendation-list` 仍保留 `max-height: 460px`，导致右侧内容停在中部、底部留出大块空白；真实截图里的问题并未解决。
- 第二次反馈显示普通预约左侧表单更短时，单一 `align-items: stretch` 又会让右侧推荐卡片撑高左侧空白；普通预约和紧急会议需要分支策略。
- 修复方式：普通预约 `.reservation-workbench.is-standard` 使用 `align-items: start`，通过 `ResizeObserver` 读取左侧表单面板自然高度并写入右侧推荐面板高度；紧急会议 `.is-emergency` 保留 `align-items: stretch`。
- `.recommendation-list` 和 `.recommendation-empty` 继续参与纵向 flex 分配，推荐卡片过多时在右侧列表内部滚动，不再撑高弹窗。
- 回归测试在 `tests/components/ReservationCreateDialog.test.ts` 中覆盖普通预约右侧高度由左侧限制、紧急会议保留拉伸样式。
- Playwright Local 页面验证在 5172 同时检查普通预约和紧急会议：普通预约左/右约 722/723，右侧列表内部滚动；紧急会议左/右约 1106/1106，按钮仍贴近底部。

## 2026-05-18 GitHub 根 README 文档
- GitHub 官方 README 建议覆盖项目做什么、为什么有用、如何开始、从哪里获得帮助、谁维护；高星项目通常进一步补充徽章、语言入口、特性、预览、安装运行、贡献和许可。
- 本项目根目录此前没有 `README.md` 和 `README.zh-CN.md`；前端子目录已有中英文 README，但只覆盖前端，不足以作为 GitHub 仓库首页。
- 根 README 应覆盖全栈能力：用户预约、日历、通知、管理端、紧急会议抢占、AI 助手、后端 API 和启动配置。
- 仓库根目录目前没有 `LICENSE` 文件，因此 README 不声明整个仓库为 MIT，只提示发布前补充根许可证。

## 2026-05-18 发布前前端模板残留清理
- 发布前扫描发现模板残留集中在 `frontend/package.json`、`frontend/src/common/constants/cache-key.ts`、`frontend/tests/demo.test.ts` 和 `frontend/tests/pinia/TagsViewStore.test.ts`。
- `frontend/package.json` 的包名、描述、作者和 repository 已替换为本项目内容，避免 GitHub 展示仍指向上游模板。
- `CacheKey` 的 `SYSTEM_NAME` 从模板名改为 `meeting-room-system`，这会让旧浏览器本地 token/config 缓存失效，用户重新登录即可。
- `frontend/tests/demo.test.ts` 只是 Vitest 模板教学示例，含上游作者邮箱和 GitHub 链接，已删除。
- `frontend/LICENSE` 中的上游 MIT 版权声明保留，这是合规要求，不属于需要删除的模板残留。

## 2026-05-18 发布前许可证与历史风险梳理
- 根目录已新增 MIT `LICENSE`，中英文 README 已更新许可证说明。
- 前端目录保留上游模板 MIT 版权声明，根许可证只补齐本仓库整体分发协议，不删除第三方版权声明。
- Git 历史曾包含已删除的旧数据库连接和旧默认密码残留；用户确认后已通过历史重写和强推清理。

## 2026-05-18 Git 历史敏感信息清理
- 已用 `git-filter-repo` 清理历史中的旧数据库地址、旧明文密码配置和旧备份/种子文件名。
- 推送采用 `--force-with-lease`，避免覆盖远端新提交。
- 推送后本地与 `origin/master` 一致，全历史敏感字符串扫描无匹配。
