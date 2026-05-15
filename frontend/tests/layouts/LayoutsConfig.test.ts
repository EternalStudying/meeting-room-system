import { CacheKey } from "@@/constants/cache-key"
import { LayoutModeEnum } from "@@/constants/app-key"
import { beforeEach, describe, expect, it, vi } from "vitest"

describe("layouts config", () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
  })

  it("忽略本地缓存中已经废弃的设置项", async () => {
    localStorage.setItem(CacheKey.CONFIG_LAYOUT, JSON.stringify({
      layoutMode: LayoutModeEnum.Top,
      showSettings: true,
      showLogo: false,
      fixedHeader: false,
      showNotify: false,
      cacheTagsView: true,
      showGreyMode: true,
      showColorWeakness: true,
      showTagsView: true,
      showThemeSwitch: true,
      showWatermark: true
    }))

    const { layoutsConfig } = await import("@/layouts/config")

    expect(layoutsConfig).toMatchObject({
      layoutMode: LayoutModeEnum.Top,
      showSettings: true,
      showLogo: false,
      fixedHeader: false,
      showNotify: false,
      showGreyMode: true,
      showColorWeakness: true
    })
    expect(layoutsConfig).not.toHaveProperty("cacheTagsView")
    expect(layoutsConfig).not.toHaveProperty("showTagsView")
    expect(layoutsConfig).not.toHaveProperty("showThemeSwitch")
    expect(layoutsConfig).not.toHaveProperty("showWatermark")
  })
})
