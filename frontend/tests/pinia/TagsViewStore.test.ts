import { CacheKey } from "@@/constants/cache-key"
import { LayoutModeEnum } from "@@/constants/app-key"
import { createPinia, setActivePinia } from "pinia"
import { beforeEach, describe, expect, it, vi } from "vitest"

describe("tags view store", () => {
  beforeEach(() => {
    localStorage.clear()
    vi.resetModules()
    setActivePinia(createPinia())
  })

  it("不再从本地缓存恢复标签数据", async () => {
    localStorage.setItem(CacheKey.CONFIG_LAYOUT, JSON.stringify({
      layoutMode: LayoutModeEnum.Left,
      showSettings: true,
      showLogo: true,
      fixedHeader: true,
      showNotify: true,
      cacheTagsView: true,
      showGreyMode: false,
      showColorWeakness: false
    }))
    localStorage.setItem("v3-admin-vite-visited-views-key", JSON.stringify([
      { path: "/rooms", fullPath: "/rooms", meta: { title: "会议室" } }
    ]))
    localStorage.setItem("v3-admin-vite-cached-views-key", JSON.stringify(["Rooms"]))

    const { useTagsViewStore } = await import("@/pinia/stores/tags-view")
    const tagsViewStore = useTagsViewStore()

    expect(tagsViewStore.visitedViews).toEqual([])
    expect(tagsViewStore.cachedViews).toEqual([])
  })
})
