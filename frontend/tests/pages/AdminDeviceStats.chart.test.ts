import { describe, expect, it } from "vitest"
import type { BindingLevel } from "@/common/apis/rooms/type"
import { formatBindingLegendLabel, getRankingBarColor } from "@/pages/admin/device-stats/chart"

describe("AdminDeviceStats chart helpers", () => {
  it("为设备排行分配不同柱状图颜色", () => {
    expect(getRankingBarColor(0)).not.toBe(getRankingBarColor(1))
    expect(getRankingBarColor(1)).not.toBe(getRankingBarColor(2))
    expect(getRankingBarColor(5)).toBeTruthy()
  })

  it("为会议室绑定强度图例拼出数量文案", () => {
    const distribution: Array<{ level: BindingLevel, label: string, value: number }> = [
      { level: "none", label: "未绑定", value: 1 },
      { level: "light", label: "轻绑定", value: 2 },
      { level: "medium", label: "中绑定", value: 3 },
      { level: "heavy", label: "重绑定", value: 4 }
    ]

    expect(formatBindingLegendLabel("轻绑定", distribution)).toBe("轻绑定 2间")
    expect(formatBindingLegendLabel("重绑定", distribution)).toBe("重绑定 4间")
  })
})
