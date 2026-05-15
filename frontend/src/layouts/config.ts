import { LayoutModeEnum } from "@@/constants/app-key"
import { getLayoutsConfig } from "@@/utils/local-storage"

export interface LayoutsConfig {
  showSettings: boolean
  layoutMode: LayoutModeEnum
  showLogo: boolean
  fixedHeader: boolean
  showNotify: boolean
  showGreyMode: boolean
  showColorWeakness: boolean
}

const DEFAULT_CONFIG: LayoutsConfig = {
  layoutMode: LayoutModeEnum.Left,
  showSettings: true,
  fixedHeader: true,
  showLogo: true,
  showNotify: true,
  showGreyMode: false,
  showColorWeakness: false
}

function resolveLayoutsConfig() {
  const persistedConfig = getLayoutsConfig()
  const nextConfig: LayoutsConfig = { ...DEFAULT_CONFIG }

  if (!persistedConfig) return nextConfig

  for (const key of Object.keys(DEFAULT_CONFIG) as Array<keyof LayoutsConfig>) {
    if (persistedConfig[key] !== undefined) {
      Object.assign(nextConfig, { [key]: persistedConfig[key] })
    }
  }

  return nextConfig
}

export const layoutsConfig: LayoutsConfig = resolveLayoutsConfig()
