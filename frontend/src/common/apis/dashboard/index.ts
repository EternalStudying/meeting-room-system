import type * as Dashboard from "./type"
import { request } from "@/http/axios"

export function getOverviewApi() {
  return request<Dashboard.OverviewResponseData>({
    url: "dashboard/overview",
    method: "get"
  })
}

export function getQuoteApi() {
  return request<Dashboard.QuoteResponseData>({
    url: "dashboard/quote",
    method: "get"
  })
}
