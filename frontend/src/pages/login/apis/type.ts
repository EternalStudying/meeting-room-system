export interface LoginRequestData {
  username: string
  password: string
  code: string
  captchaId: string
}

export interface CaptchaData {
  captchaId: string
  imageBase64: string
}

export type CaptchaResponseData = ApiResponseData<CaptchaData>

export type LoginResponseData = ApiResponseData<{ token: string }>
