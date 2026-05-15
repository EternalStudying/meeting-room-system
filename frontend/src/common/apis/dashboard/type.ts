export interface OverviewSummaryData {
  todayMeetingCount?: number
  utilizationRate?: number
  pendingCount?: number
  availableRoomCount?: number
  totalRoomCount?: number
}

export interface OverviewScheduleItem {
  id: number | string
  startTime: string
  endTime?: string
  title: string
  roomId?: number
  roomName: string
  attendees?: number
  status?: string
  deviceSummary?: string
}

export interface OverviewRoomStatusItem {
  roomId: number | string
  roomName: string
  status?: string
  displayStatus: string
  detail?: string
}

export interface OverviewData {
  peakWindow?: string
  summary?: OverviewSummaryData
  todaySchedules?: OverviewScheduleItem[]
  roomStatuses?: OverviewRoomStatusItem[]
  todoItems?: string[]
}

export type OverviewResponseData = ApiResponseData<OverviewData>

export interface QuoteData {
  quote?: string
  quoteAuthor?: string
}

export type QuoteResponseData = ApiResponseData<QuoteData>
