import type { BindingLevel } from "@/common/apis/rooms/type"

const rankingBarPalette = [
  "#6c8de8",
  "#53c5b1",
  "#d6a64b",
  "#ec8aa4",
  "#7f88eb",
  "#69b8f1"
]

export function getRankingBarColor(index: number) {
  return rankingBarPalette[index % rankingBarPalette.length]
}

export function formatBindingLegendLabel(
  label: string,
  distribution: Array<{ level: BindingLevel, label: string, value: number }>
) {
  const matched = distribution.find(item => item.label === label)
  return `${label} ${matched?.value ?? 0}间`
}
