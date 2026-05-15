export type CurrentUserResponseData = ApiResponseData<{ username: string, roles: string[] }>

export interface UserSearchOption {
  id: number
  username: string
  displayName: string
}

export interface UserSearchRequestData {
  keyword: string
  limit?: number
}

export type UserSearchResponseData = ApiResponseData<UserSearchOption[]>
