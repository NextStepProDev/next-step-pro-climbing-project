// User types
export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  phone: string
  nickname: string
  role: 'USER' | 'ADMIN'
  isAdmin: boolean
  emailNotificationsEnabled: boolean
  createdAt: string
}

// Calendar types
export interface MonthView {
  yearMonth: string
  days: DaySummary[]
  events: EventSummary[]
}

export interface DaySummary {
  date: string
  totalSlots: number
  availableSlots: number
  hasUserReservation: boolean
}

export interface DayView {
  date: string
  slots: TimeSlot[]
  events: EventSummary[]
}

export interface TimeSlot {
  id: string
  startTime: string
  endTime: string
  status: SlotStatus
  isUserRegistered: boolean
  eventTitle: string | null
}

export interface TimeSlotDetail {
  id: string
  date: string
  startTime: string
  endTime: string
  maxParticipants: number
  currentParticipants: number
  waitlistCount: number
  status: SlotStatus
  isUserRegistered: boolean
  isUserOnWaitlist: boolean
  waitlistPosition: number | null
  eventId: string | null
  eventTitle: string | null
  reservationId: string | null
  waitlistEntryId: string | null
}

export type SlotStatus = 'AVAILABLE' | 'FULL' | 'BLOCKED' | 'PAST'

// Event types
export interface EventSummary {
  id: string
  title: string
  location: string | null
  eventType: EventType
  startDate: string
  endDate: string
  isMultiDay: boolean
  maxParticipants: number
  currentParticipants: number
  isUserRegistered: boolean
  enrollmentOpen: boolean
}

export interface EventDetail {
  id: string
  title: string
  description: string | null
  location: string | null
  eventType: EventType
  startDate: string
  endDate: string
  maxParticipants: number
  active: boolean
  startTime: string | null
  endTime: string | null
  slots?: TimeSlotAdmin[]
}

export type EventType = 'COURSE' | 'TRAINING' | 'WORKSHOP'

// Reservation types
export interface UserReservation {
  id: string
  date: string
  startTime: string
  endTime: string
  status: string
  eventTitle: string | null
  comment: string | null
  participants: number
  createdAt: string
}

export interface ReservationResult {
  reservationId: string
  success: boolean
  message: string
}

export interface WaitlistResult {
  entryId: string
  position: number
  message: string
}

export interface EventReservationResult {
  eventId: string
  success: boolean
  message: string
  slotsReserved: number
}

export interface UserEventReservation {
  eventId: string
  eventTitle: string
  eventType: EventType
  startDate: string
  endDate: string
  comment: string | null
  participants: number
  slotsCount: number
  createdAt: string
}

export interface MyReservations {
  slots: UserReservation[]
  events: UserEventReservation[]
}

// Admin types
export interface TimeSlotAdmin {
  id: string
  date: string
  startTime: string
  endTime: string
  maxParticipants: number
  currentParticipants: number
  blocked: boolean
  blockReason: string | null
  title: string | null
  eventId: string | null
}

export interface SlotParticipants {
  slotId: string
  date: string
  startTime: string
  endTime: string
  maxParticipants: number
  participants: Participant[]
  waitlist: WaitlistParticipant[]
}

export interface Participant {
  userId: string
  fullName: string
  email: string
  phone: string
  comment: string | null
  participants: number
  registeredAt: string
}

export interface WaitlistParticipant {
  entryId: string
  userId: string
  fullName: string
  email: string
  position: number
  notified: boolean
}

export interface CreateTimeSlotRequest {
  date: string
  startTime: string
  endTime: string
  maxParticipants: number
  title?: string
  eventId?: string
}

export interface ReservationAdmin {
  id: string
  userFullName: string
  userEmail: string
  userPhone: string
  date: string
  startTime: string
  endTime: string
  title: string | null
  comment: string | null
  participants: number
}

export interface CreateEventRequest {
  title: string
  description?: string
  location?: string
  eventType: EventType
  startDate: string
  endDate: string
  maxParticipants: number
  startTime?: string
  endTime?: string
}
