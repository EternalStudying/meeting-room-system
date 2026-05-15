import { readFileSync } from "node:fs"
import { resolve } from "node:path"
import { describe, expect, it } from "vitest"

const projectRoot = resolve(__dirname, "..", "..")

function readSource(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), "utf-8")
}

describe("NavigationBar layout", () => {
  it("keeps settings trigger to the left of notify and opens the shared settings panel", () => {
    const source = readSource("src/layouts/components/NavigationBar/index.vue")

    expect(source).toContain("showSettings")
    expect(source).toContain("settings-trigger")
    expect(source).toContain("appStore.settingsPanelOpen = true")
    expect(source.indexOf("settings-trigger")).toBeLessThan(source.indexOf("<Notify"))
  })

  it("right panel uses the shared settings panel state and removes the floating handle", () => {
    const source = readSource("src/layouts/components/RightPanel/index.vue")

    expect(source).toContain('v-model="appStore.settingsPanelOpen"')
    expect(source).not.toContain("handle-button")
    expect(source).not.toContain("<Setting />")
  })
})
