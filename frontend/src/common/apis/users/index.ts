import type * as Users from "./type"
import { request } from "@/http/axios"

/** 获取当前登录用户详情 */
export function getCurrentUserApi() {
  return request<Users.CurrentUserResponseData>({
    url: "users/me",
    method: "get"
  })
}

export function searchUsersApi(params: Users.UserSearchRequestData) {
  return request<Users.UserSearchResponseData>({
    url: "users/search",
    method: "get",
    params,
    silentError: true
  })
}
