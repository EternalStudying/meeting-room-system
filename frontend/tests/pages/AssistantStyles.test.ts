import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

describe("AssistantPageStyles", () => {
  it("收成单列聊天工作区布局", () => {
    const source = readFileSync(resolve(process.cwd(), "src/pages/assistant/index.vue"), "utf-8")

    expect(source).toContain("grid-template-columns: minmax(0, 1fr);")
    expect(source).not.toContain("<aside class=\"support-stage\">")
    expect(source).toContain("class=\"prompt-wall\"")
    expect(source).toContain("class=\"assistant-card\"")
    expect(source).not.toContain("class=\"composer-shortcuts\"")
    expect(source).not.toContain("class=\"composer-topline\"")
    expect(source).not.toContain("border: 1px solid var(--shell-line-strong);")
    expect(source).toContain("class=\"send-icon-button\"")
    expect(source).not.toContain("class=\"launch-button\"")
  })
})
