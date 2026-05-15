import type * as Stats from "./type"
import { request } from "@/http/axios"

export function getAdminStatsApi(days?: 1 | 7 | 30) {
  return request<Stats.AdminStatsResponseData>({
    url: "admin/stats",
    method: "get",
    params: days ? { days } : undefined
  })
}
