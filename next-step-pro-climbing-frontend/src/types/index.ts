// Shared Assets (Media Library)
export interface AssetDto {
  id: string
  filename: string
  originalName: string
  mimeType: string
  sizeBytes: number
  url: string
  createdAt: string
}

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
  preferredLanguage: string
  newsletterSubscribed: boolean
  newsletterChoiceMade: boolean
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

export interface WeekView {
  startDate: string
  endDate: string
  days: WeekDay[]
  events: EventSummary[]
}

export interface WeekDay {
  date: string
  slots: TimeSlot[]
}

export interface TimeSlot {
  id: string
  startTime: string
  endTime: string
  maxParticipants: number
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
  status: SlotStatus
  isUserRegistered: boolean
  eventId: string | null
  eventTitle: string | null
  eventDescription: string | null
  reservationId: string | null
}

export type SlotStatus = 'AVAILABLE' | 'FULL' | 'BLOCKED' | 'PAST' | 'BOOKING_CLOSED'

// Event types
export interface EventSummary {
  id: string
  title: string
  description: string | null
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
  timeSlotId: string
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

export interface EventParticipants {
  eventId: string
  maxParticipants: number
  participants: Participant[]
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
  eventStartDate: string | null
  eventEndDate: string | null
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

// Activity Log types
export type ActivityActionType =
  | 'RESERVATION_CREATED'
  | 'RESERVATION_CANCELLED'
  | 'RESERVATION_REACTIVATED'
  | 'EVENT_RESERVATION_CREATED'
  | 'EVENT_RESERVATION_CANCELLED'
  | 'RESERVATION_CANCELLED_BY_ADMIN'

export interface ActivityLog {
  id: string
  userFullName: string
  userEmail: string
  actionType: ActivityActionType
  slotDate: string | null
  slotStartTime: string | null
  slotEndTime: string | null
  slotTitle: string | null
  eventTitle: string | null
  eventStartDate: string | null
  eventEndDate: string | null
  participants: number | null
  createdAt: string
}

// Instructor types
export interface InstructorPublic {
  id: string
  firstName: string
  lastName: string
  photoUrl: string | null
  focalPointX: number | null
  focalPointY: number | null
  bio: string | null
  certifications: string | null
  badgeUrl: string | null
  createdAt: string
}

export interface InstructorAdmin {
  id: string
  firstName: string
  lastName: string
  photoFilename: string | null
  photoUrl: string | null
  focalPointX: number | null
  focalPointY: number | null
  bio: string | null
  certifications: string | null
  badgeUrl: string | null
  displayOrder: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateInstructorRequest {
  firstName: string
  lastName: string
  bio?: string
  certifications?: string
}

export interface UpdateInstructorRequest {
  firstName?: string
  lastName?: string
  bio?: string
  certifications?: string
  active?: boolean
  displayOrder?: number
  focalPointX?: number
  focalPointY?: number
}

// Gallery types
export interface AlbumSummary {
  id: string
  name: string
  description: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  photoCount: number
  createdAt: string
}

export interface AlbumDetail {
  id: string
  name: string
  description: string | null
  photos: Photo[]
  createdAt: string
  updatedAt: string
}

export interface Photo {
  id: string
  url: string
  caption: string | null
  focalPointX: number | null
  focalPointY: number | null
  createdAt: string
}

export interface AlbumAdmin {
  id: string
  name: string
  description: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  photoCount: number
  displayOrder: number
  createdAt: string
  updatedAt: string
}

export interface AlbumDetailAdmin {
  id: string
  name: string
  description: string | null
  thumbnailPhotoId: string | null
  photos: PhotoAdmin[]
  createdAt: string
  updatedAt: string
}

export interface SetThumbnailPhotoRequest {
  photoId: string
}

export interface PhotoAdmin {
  id: string
  filename: string
  url: string
  caption: string | null
  displayOrder: number
  focalPointX: number | null
  focalPointY: number | null
  createdAt: string
  updatedAt: string
}

export interface CreateAlbumRequest {
  name: string
  description?: string
}

export interface UpdateAlbumRequest {
  name?: string
  description?: string
}

export interface ReorderAlbumsRequest {
  orderedIds: string[]
}

export interface UpdatePhotoRequest {
  caption?: string
  displayOrder?: number
  focalPointX?: number
  focalPointY?: number
}

export interface UploadPhotoResponse {
  id: string
  filename: string
  url: string
}

// ==================== News ====================

export type BlockType = 'TEXT' | 'IMAGE' | 'VIDEO_EMBED'

export interface ContentBlock {
  id: string
  blockType: BlockType
  content: string | null
  imageUrl: string | null
  caption: string | null
  displayOrder: number
}

export interface NewsSummary {
  id: string
  title: string
  excerpt: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  publishedAt: string
}

export interface NewsDetail {
  id: string
  title: string
  excerpt: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  blocks: ContentBlock[]
  publishedAt: string
}

export interface ContentBlockAdmin {
  id: string
  blockType: BlockType
  content: string | null
  imageFilename: string | null
  imageUrl: string | null
  caption: string | null
  displayOrder: number
}

export interface NewsAdmin {
  id: string
  title: string
  excerpt: string | null
  thumbnailUrl: string | null
  published: boolean
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface NewsDetailAdmin {
  id: string
  title: string
  excerpt: string | null
  thumbnailFilename: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  published: boolean
  publishedAt: string | null
  blocks: ContentBlockAdmin[]
  createdAt: string
  updatedAt: string
}

export interface CreateNewsRequest {
  title: string
  excerpt?: string
}

export interface UpdateNewsMetaRequest {
  title?: string
  excerpt?: string
}

export interface AddTextBlockRequest {
  content: string
}

export interface UpdateTextBlockRequest {
  content: string
}

export interface UpdateImageBlockRequest {
  caption?: string
}

export interface MoveBlockRequest {
  direction: 'UP' | 'DOWN'
}

export interface UploadBlockImageResponse {
  blockId: string
  imageFilename: string
  imageUrl: string
  displayOrder: number
}

export interface UploadThumbnailResponse {
  filename: string
  url: string
}

export interface NewsPageDto {
  content: NewsSummary[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

export interface AdminNewsPageDto {
  content: NewsAdmin[]
  page: number
  size: number
  totalElements: number
  hasNext: boolean
}

// ==================== Courses ====================

export interface CourseSummary {
  id: string
  title: string
  excerpt: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  publishedAt: string | null
}

export interface CourseDetail {
  id: string
  title: string
  excerpt: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  blocks: ContentBlock[]
  publishedAt: string | null
}

export interface CourseAdmin {
  id: string
  title: string
  excerpt: string | null
  thumbnailUrl: string | null
  displayOrder: number
  published: boolean
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CourseDetailAdmin {
  id: string
  title: string
  excerpt: string | null
  thumbnailFilename: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  published: boolean
  publishedAt: string | null
  blocks: ContentBlockAdmin[]
  createdAt: string
  updatedAt: string
}

export interface CreateCourseRequest {
  title: string
  excerpt?: string
}

export interface UpdateCourseMetaRequest {
  title?: string
  excerpt?: string
}
