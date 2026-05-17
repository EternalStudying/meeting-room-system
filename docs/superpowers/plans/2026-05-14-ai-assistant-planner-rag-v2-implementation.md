# AI Assistant Planner RAG v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the AI assistant from rule-first semantic matching to an LLM-first Planner with deterministic fallback and RAG-based system knowledge answers.

**Architecture:** Keep Tool Registry and existing handlers as the execution boundary. Add a Router + Planner v2 layer that calls Ollama first, validates a strict JSON plan, falls back to deterministic parsing when needed, and routes knowledge/help questions to RAG. Frontend cards stay compatible, with clarification and RAG answers rendered as existing cards.

**Tech Stack:** Spring Boot, Spring AI/Ollama, Jackson JSON schema-style validation, MyBatis services, Vue 3 + Vite + TypeScript + Element Plus, Vitest, JUnit/Mockito.

---

## Spec Reference

- `docs/superpowers/specs/2026-05-14-ai-assistant-planner-rag-v2-design.md`
- Existing baseline spec: `docs/superpowers/specs/2026-05-13-ai-assistant-planner-design.md`

## Files To Touch

Backend create:

- `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlan.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlanFields.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlanValidator.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlannerService.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantRequestRouter.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/rag/AiAssistantKnowledgeItem.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/rag/AiAssistantKnowledgeService.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/rag/AiAssistantRagService.java`
- `backend/meeting-room-server/src/main/resources/ai/assistant-planner-v2-prompt.md`
- `backend/meeting-room-server/src/main/resources/ai/assistant-knowledge.json`
- `backend/meeting-room-server/src/test/java/com/llf/assistant/planner/AiAssistantPlannerServiceTest.java`
- `backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantTimeResolverTest.java`
- `backend/meeting-room-server/src/test/java/com/llf/assistant/rag/AiAssistantRagServiceTest.java`

Backend modify:

- `backend/meeting-room-server/src/main/java/com/llf/service/impl/AiAssistantServiceImpl.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantSemanticService.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantIntentParser.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantTimeResolver.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantIntentFields.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/handler/RoomAssistantActionHandler.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/handler/ReservationAssistantActionHandler.java`
- `backend/meeting-room-server/src/main/java/com/llf/assistant/AiAssistantSessionStore.java`
- `backend/meeting-room-server/src/main/resources/ai/assistant-intent.schema.json`
- `backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java`
- `backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantSemanticServiceTest.java`

Frontend modify only if tests show a rendering gap:

- `frontend/src/pages/assistant/index.vue`
- `frontend/tests/pages/Assistant.test.ts`

Docs modify:

- `完整功能测试清单.md`
- `backend/findings.md`
- `backend/progress.md`
- `backend/task_plan.md`
- `frontend/findings.md`
- `frontend/progress.md`
- `frontend/task_plan.md`

## Task 1: Add Backend Regression Tests For User Failures

**Files:**

- Modify: `backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java`
- Modify: `backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantSemanticServiceTest.java`

- [ ] **Step 1: Add failing service tests for the 5 user samples**

Add tests equivalent to:

```java
@Test
void message_specificDateMeetings_shouldQueryMyReservations() {
    AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
    when(reservationService.myReservations(eq(1L), eq("2026-05-15 00:00:00"), eq("2026-05-16 00:00:00"), eq("all"), eq(null), eq(false)))
            .thenReturn(List.of(activeReservation(9101L, "需求评审会", "2026-05-15 09:00:00", "2026-05-15 10:00:00", "云杉会议室")));

    AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "2026-05-15有哪些会议"));

    assertEquals("reply", result.getStage());
    assertTrue(result.getAssistantText().contains("需求评审会"));
}

@Test
void message_cancelThisRoom_shouldClarifyInsteadOfExecuting() {
    AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

    AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消这个会议室"));

    assertEquals("collect", result.getStage());
    assertTrue(result.getAssistantText().contains("取消预约") || result.getAssistantText().contains("放弃当前选择"));
    assertNull(result.getPendingAction());
}
```

Also add tests for:

- `上周我参加了哪些会议`
- `下周我有哪些日程`
- `明天9点到11点有哪些会议室可以用`

- [ ] **Step 2: Run the failing tests**

Run:

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantServiceImplTest,AiAssistantSemanticServiceTest\" test"
```

Expected: at least one new assertion fails or the current implementation queries the wrong date/tool.

- [ ] **Step 3: Commit failing tests**

```bash
git add backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantSemanticServiceTest.java
git commit -m "test: capture assistant planner v2 failures"
```

## Task 2: Define Planner v2 DTOs, Prompt, And Validator

**Files:**

- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlan.java`
- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlanFields.java`
- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlanValidator.java`
- Create: `backend/meeting-room-server/src/main/resources/ai/assistant-planner-v2-prompt.md`
- Create: `backend/meeting-room-server/src/test/java/com/llf/assistant/planner/AiAssistantPlannerServiceTest.java`

- [ ] **Step 1: Write validator tests**

Cover:

- valid `reservations.list` plan
- invalid unknown `toolName`
- invalid confidence outside 0-1
- write operation with ambiguity
- knowledge request without `toolName`

- [ ] **Step 2: Implement DTOs**

Use simple Jackson-friendly classes:

```java
@Data
public class AiAssistantPlan {
    private String intentType;
    private String toolName;
    private Double confidence;
    private AiAssistantPlanFields fields = new AiAssistantPlanFields();
    private List<String> missingFields = List.of();
    private String ambiguity;
    private String reason;
}
```

```java
@Data
public class AiAssistantPlanFields {
    private Long reservationId;
    private Long roomId;
    private String roomName;
    private String title;
    private String meetingDate;
    private String dateFrom;
    private String dateTo;
    private String startClock;
    private String endClock;
    private Integer attendees;
    private Integer rating;
    private String content;
    private String deviceRequirements;
    private String targetScope;
    private String timeRangeLabel;
    private String relativeTarget;
    private String mutationHint;
    private Integer timeShiftMinutes;
    private String quantityHint;
    private List<Long> participantUserIds;
}
```

- [ ] **Step 3: Implement validator**

Rules:

- `intentType` must be one of `operation`, `knowledge`, `mixed`, `clarification`, `out_of_scope`.
- `operation` requires a registered `toolName`.
- `knowledge`, `clarification`, and `out_of_scope` must not require execution.
- `confidence` defaults to 0 when missing.
- `confidence < 0.70` is not executable.
- Unknown fields from JSON must fail by configuring ObjectMapper or by schema validation.

- [ ] **Step 4: Add prompt template**

Prompt must include:

```markdown
You are the Planner for a meeting-room management system.
Return JSON only.
Never execute actions.
Choose only tools from the provided tool list.
If the user asks for rules/help/how-to, return intentType=knowledge.
If the user asks to operate system data, return intentType=operation.
If the user says "取消这个会议室", return intentType=clarification with ambiguity.
```

- [ ] **Step 5: Run tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantPlannerServiceTest\" test"
```

Expected: validator tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/meeting-room-server/src/main/java/com/llf/assistant/planner backend/meeting-room-server/src/main/resources/ai/assistant-planner-v2-prompt.md backend/meeting-room-server/src/test/java/com/llf/assistant/planner/AiAssistantPlannerServiceTest.java
git commit -m "feat: add assistant planner v2 schema"
```

## Task 3: Switch Semantic Flow To LLM-First With Fallback

**Files:**

- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantPlannerService.java`
- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/planner/AiAssistantRequestRouter.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantSemanticService.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/service/impl/AiAssistantServiceImpl.java`
- Modify: `backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantSemanticServiceTest.java`

- [ ] **Step 1: Add tests proving LLM result wins over rules**

Mock the LLM client to return a valid plan for:

```json
{
  "intentType": "operation",
  "toolName": "reservations.list",
  "confidence": 0.93,
  "fields": {
    "dateFrom": "2026-05-15 00:00:00",
    "dateTo": "2026-05-16 00:00:00",
    "targetScope": "mine"
  },
  "missingFields": [],
  "ambiguity": null,
  "reason": "specified date meeting query"
}
```

Assert that `AiAssistantSemanticService.parse()` returns `reservations.list` and those dates.

- [ ] **Step 2: Add tests proving fallback still works**

Mock LLM response as `null`, blank string, invalid JSON, unknown tool, and low confidence. Assert fallback handles `明天有哪些会`.

- [ ] **Step 3: Implement planner service**

Implement:

- build prompt with current tool registry
- call existing `AiAssistantIntentLlmClient` or a new planner client wrapper
- parse JSON into `AiAssistantPlan`
- validate plan
- convert plan to `AiAssistantIntentParseResult`
- fallback to existing `parseByRules` when invalid

- [ ] **Step 4: Wire semantic service**

Change order from rule-first to:

1. normalize text
2. planner LLM parse
3. validate
4. time/context post-processing
5. fallback rules if invalid

- [ ] **Step 5: Run assistant semantic tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantSemanticServiceTest,AiAssistantPlannerServiceTest\" test"
```

Expected: all pass.

- [ ] **Step 6: Commit**

```bash
git add backend/meeting-room-server/src/main/java/com/llf/assistant/planner backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantSemanticService.java backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantSemanticServiceTest.java
git commit -m "feat: make assistant planner llm first"
```

## Task 4: Expand Time Parsing And Stop Handler Re-Parsing From Raw Text

**Files:**

- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantTimeResolver.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantIntentFields.java`
- Create: `backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantTimeResolverTest.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/handler/RoomAssistantActionHandler.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/handler/ReservationAssistantActionHandler.java`

- [ ] **Step 1: Add time resolver tests**

Use `LocalDate.now()` in tests and assert:

- `上周` gives previous Monday to current Monday
- `下周` gives next Monday to following Monday
- `2026-05-15` gives `2026-05-15 00:00:00` to `2026-05-16 00:00:00`
- `明天9点到11点` gives tomorrow `09:00` to `11:00`

- [ ] **Step 2: Implement missing labels**

Add support for:

- `last_week`
- `last_weekend`
- `next_week`
- direct date windows
- point-hour ranges such as `9点到11点`

- [ ] **Step 3: Make handlers read draft first**

In `RoomAssistantActionHandler.handleRoomSearch`, use `session.getDraft()` values before parsing raw text:

- `meetingDate`
- `startClock`
- `endClock`
- `dateFrom`
- `dateTo`
- `attendees`
- `roomName`

In `ReservationAssistantActionHandler.resolveTimeWindow`, ensure `dateFrom` and `dateTo` from draft always win over raw message parsing.

- [ ] **Step 4: Run tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantTimeResolverTest,AiAssistantServiceImplTest\" test"
```

Expected: user failure time cases pass.

- [ ] **Step 5: Commit**

```bash
git add backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantTimeResolver.java backend/meeting-room-server/src/main/java/com/llf/assistant/handler/RoomAssistantActionHandler.java backend/meeting-room-server/src/main/java/com/llf/assistant/handler/ReservationAssistantActionHandler.java backend/meeting-room-server/src/test/java/com/llf/assistant/semantic/AiAssistantTimeResolverTest.java
git commit -m "feat: expand assistant time parsing"
```

## Task 5: Fix Room Availability Queries With Time Window And Optional Attendees

**Files:**

- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/handler/RoomAssistantActionHandler.java`
- Modify: `backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java`

- [ ] **Step 1: Add test for `明天9点到11点有哪些会议室可以用`**

Mock `reservationService.recommend()` and assert:

- start time is tomorrow `09:00:00`
- end time is tomorrow `11:00:00`
- attendees defaults to `1` when user did not specify人数
- returned room names appear in assistant text

- [ ] **Step 2: Implement room search behavior**

When date and time range are available:

- if `attendees` is null, use `1`
- call `reservationService.recommend()`
- display up to 3 rooms
- if no rooms, say no available room for that time window

- [ ] **Step 3: Keep existing capacity/location search**

If there is no time window, keep using `roomService.pageRooms()` for general room list/filter search.

- [ ] **Step 4: Run tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantServiceImplTest\" test"
```

Expected: room availability query passes and old room tests still pass.

- [ ] **Step 5: Commit**

```bash
git add backend/meeting-room-server/src/main/java/com/llf/assistant/handler/RoomAssistantActionHandler.java backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java
git commit -m "fix: handle assistant room availability windows"
```

## Task 6: Add Context Memory And Ambiguity Guard

**Files:**

- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/AiAssistantSessionStore.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantReferenceResolver.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantIntentParser.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/service/impl/AiAssistantServiceImpl.java`
- Modify: `backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java`

- [ ] **Step 1: Add ambiguity tests**

Cover:

- `取消这个会议室` returns collect/clarification and no pending action.
- after reservation detail, `取消这个会` can enter cancel flow.
- after room detail, `取消这个会议室` still clarifies.

- [ ] **Step 2: Extend session state**

Add fields:

```java
private String lastToolName;
private String lastMentionedEntityType;
private List<AiAssistantIntentCandidate> lastQueryResultCandidates;
private String currentTaskType;
```

Keep existing `lastReservationId` and `lastRoomId`.

- [ ] **Step 3: Mark entity context**

After successful reservation detail/list match, set `lastMentionedEntityType=reservation`.

After successful room detail/search match, set `lastMentionedEntityType=room`.

- [ ] **Step 4: Add guard before execution**

If normalized text contains `取消` and `会议室`, and action would be `reservations.cancel`, return clarification unless text also clearly mentions `预约` or `会议` as an event.

Use message:

```text
你是想取消某个预约，还是只是放弃当前选择的会议室？
```

- [ ] **Step 5: Run tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantServiceImplTest\" test"
```

Expected: ambiguity behavior passes.

- [ ] **Step 6: Commit**

```bash
git add backend/meeting-room-server/src/main/java/com/llf/assistant/AiAssistantSessionStore.java backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantReferenceResolver.java backend/meeting-room-server/src/main/java/com/llf/assistant/semantic/AiAssistantIntentParser.java backend/meeting-room-server/src/main/java/com/llf/service/impl/AiAssistantServiceImpl.java backend/meeting-room-server/src/test/java/com/llf/service/impl/AiAssistantServiceImplTest.java
git commit -m "feat: add assistant context ambiguity guard"
```

## Task 7: Add RAG Knowledge Answering

**Files:**

- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/rag/AiAssistantKnowledgeItem.java`
- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/rag/AiAssistantKnowledgeService.java`
- Create: `backend/meeting-room-server/src/main/java/com/llf/assistant/rag/AiAssistantRagService.java`
- Create: `backend/meeting-room-server/src/main/resources/ai/assistant-knowledge.json`
- Create: `backend/meeting-room-server/src/test/java/com/llf/assistant/rag/AiAssistantRagServiceTest.java`
- Modify: `backend/meeting-room-server/src/main/java/com/llf/service/impl/AiAssistantServiceImpl.java`

- [ ] **Step 1: Add RAG tests**

Cover:

- `怎么取消预约` returns cancel rule and suggestions.
- `审批驳回需要填原因吗` returns admin approval rule.
- `今天天气怎么样` returns out-of-scope.
- RAG answer never creates `pendingAction`.

- [ ] **Step 2: Create knowledge JSON**

Include entries:

- `reservation_create_rule`
- `reservation_cancel_rule`
- `reservation_update_rule`
- `reservation_review_rule`
- `admin_approval_rule`
- `room_status_rule`
- `assistant_capability_scope`
- `out_of_scope`

- [ ] **Step 3: Implement keyword retrieval**

Use the existing `ReservationKnowledgeService` scoring style, but put the new class under `com.llf.assistant.rag`.

- [ ] **Step 4: Route knowledge requests**

In request router, route questions containing:

- `怎么`
- `如何`
- `规则`
- `什么意思`
- `需要`
- `帮助`

to RAG unless the same message also contains a clear operation verb and object. For mixed requests, return a short RAG answer then continue Planner only when safe.

- [ ] **Step 5: Return card**

Use `AiAssistantCardVO.text()` or `query_result` with title `系统帮助`.

- [ ] **Step 6: Run tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantRagServiceTest,AiAssistantServiceImplTest\" test"
```

Expected: RAG and assistant service tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/meeting-room-server/src/main/java/com/llf/assistant/rag backend/meeting-room-server/src/main/resources/ai/assistant-knowledge.json backend/meeting-room-server/src/test/java/com/llf/assistant/rag/AiAssistantRagServiceTest.java backend/meeting-room-server/src/main/java/com/llf/service/impl/AiAssistantServiceImpl.java
git commit -m "feat: add assistant rag knowledge answers"
```

## Task 8: Frontend Card Regression

**Files:**

- Modify: `frontend/tests/pages/Assistant.test.ts`
- Modify only if needed: `frontend/src/pages/assistant/index.vue`

- [ ] **Step 1: Add frontend tests**

Mock backend turns for:

- clarification card for `取消这个会议室`
- RAG text answer for `怎么取消预约`
- no duplicate welcome text

- [ ] **Step 2: Run tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd frontend; pnpm test -- run tests/pages/Assistant.test.ts"
```

Expected: tests pass if existing card renderer is enough; otherwise fail with a concrete missing render state.

- [ ] **Step 3: Patch frontend only if tests fail**

If clarification or text cards are not rendered, update `visibleTurnCards` and the card template to render them. Do not redesign the page.

- [ ] **Step 4: Run frontend build**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd frontend; pnpm build:staging"
```

Expected: build passes, except any already-known `%VITE_APP_TITLE%` warning.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/assistant/index.vue frontend/tests/pages/Assistant.test.ts
git commit -m "test: cover assistant planner v2 cards"
```

## Task 9: Update Manual Test Checklist And Context Docs

**Files:**

- Modify: `完整功能测试清单.md`
- Modify: `backend/findings.md`
- Modify: `backend/progress.md`
- Modify: `backend/task_plan.md`
- Modify: `frontend/findings.md`
- Modify: `frontend/progress.md`
- Modify: `frontend/task_plan.md`

- [ ] **Step 1: Add the 5 user failure samples to the AI assistant section**

Mark them as required regression items.

- [ ] **Step 2: Record backend findings**

Document:

- LLM-first Planner behavior.
- fallback boundaries.
- time parsing additions.
- ambiguity rule for `取消这个会议室`.
- RAG knowledge scope.

- [ ] **Step 3: Record frontend findings**

Document whether frontend changes were needed.

- [ ] **Step 4: Commit docs**

```bash
git add 完整功能测试清单.md backend/findings.md backend/progress.md backend/task_plan.md frontend/findings.md frontend/progress.md frontend/task_plan.md
git commit -m "docs: update assistant planner v2 verification"
```

## Task 10: Final Verification

**Files:**

- No code files unless fixing failures found during verification.

- [ ] **Step 1: Run backend assistant target tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server \"-Dtest=AiAssistantServiceImplTest,AiAssistantSemanticServiceTest,AiAssistantPlannerServiceTest,AiAssistantTimeResolverTest,AiAssistantRagServiceTest\" test"
```

Expected: all target tests pass.

- [ ] **Step 2: Run backend module tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd backend; mvn -pl meeting-room-server test"
```

Expected: all backend module tests pass.

- [ ] **Step 3: Run frontend assistant tests**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd frontend; pnpm test -- run tests/pages/Assistant.test.ts tests/common/apis/AssistantApi.test.ts"
```

Expected: assistant frontend tests pass.

- [ ] **Step 4: Run frontend build**

```powershell
D:\PowerShell-7.6.1\pwsh.exe -NoLogo -NoProfile -Command "cd frontend; pnpm build:staging"
```

Expected: build passes.

- [ ] **Step 5: Manual smoke**

Start backend on `8081` and frontend on `5172`, then test:

- `2026-05-15有哪些会议`
- `上周我参加了哪些会议`
- `下周我有哪些日程`
- `明天9点到11点有哪些会议室可以用`
- `取消这个会议室`
- `怎么取消预约`

Expected: all match the spec acceptance behavior.

- [ ] **Step 6: Commit final fixes if any**

```bash
git status --short
git add <only files changed by this task>
git commit -m "fix: complete assistant planner v2 verification"
```

## Self-Review

- Spec coverage: tasks cover LLM-first Planner, fallback, time parsing, room availability, context ambiguity, RAG, frontend cards, docs, and final verification.
- Placeholder scan: no unfinished implementation sections are left.
- Type consistency: plan classes use `AiAssistantPlan` and `AiAssistantPlanFields`; service tests continue to use existing `AiAssistantTurnVO`.
