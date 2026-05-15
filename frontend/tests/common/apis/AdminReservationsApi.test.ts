import { beforeEach, describe, expect, it, vi } from "vitest"

const requestMock = vi.hoisted(() => vi.fn())

vi.mock("@/http/axios", () => ({
  request: requestMock
}))

describe("admin reservations api", () => {
  beforeEach(() => {
    requestMock.mockReset()
    vi.resetModules()
  })

  it("requests admin reservation list", async () => {
    const response = { code: 0, message: "ok", data: { list: [], total: 0 } }
    requestMock.mockResolvedValueOnce(response)

    const { getAdminReservationListApi } = await import("@/common/apis/reservations")
    await expect(getAdminReservationListApi({
      currentPage: 1,
      size: 8,
      status: "PENDING",
      keyword: "周会"
    })).resolves.toEqual(response)

    expect(requestMock).toHaveBeenCalledWith({
      url: "admin/reservations",
      method: "get",
      params: {
        currentPage: 1,
        size: 8,
        status: "PENDING",
        keyword: "周会"
      }
    })
  })

  it("requests approve reject and exception actions", async () => {
    requestMock.mockResolvedValue({ code: 0, message: "ok", data: null })

    const {
      approveAdminReservationApi,
      markAdminReservationExceptionApi,
      rejectAdminReservationApi
    } = await import("@/common/apis/reservations")

    await approveAdminReservationApi(101, { remark: "通过" })
    await rejectAdminReservationApi(102, { reason: "时间冲突" })
    await markAdminReservationExceptionApi(103, { reason: "现场未到" })

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      url: "admin/reservations/101/approve",
      method: "patch",
      data: { remark: "通过" }
    })
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      url: "admin/reservations/102/reject",
      method: "patch",
      data: { reason: "时间冲突" }
    })
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      url: "admin/reservations/103/exception",
      method: "patch",
      data: { reason: "现场未到" }
    })
  })
})
