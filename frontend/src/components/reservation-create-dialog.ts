import dayjs from "dayjs"

export interface ReservationCreateDeviceRequirementDraft {
  deviceId: number | null
  quantity: number
}

export interface ReservationCreateDraft {
  roomId: number | null
  title: string
  meetingDate: string
  startClock: string
  endClock: string
  attendees: number
  participantUserIds: number[]
  deviceRequirements: ReservationCreateDeviceRequirementDraft[]
  remark: string
}

export function createReservationCreateDraft(): ReservationCreateDraft {
  return {
    roomId: null,
    title: "",
    meetingDate: dayjs().format("YYYY-MM-DD"),
    startClock: "09:00",
    endClock: "10:00",
    attendees: 1,
    participantUserIds: [],
    deviceRequirements: [],
    remark: ""
  }
}
