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
  // Coach-designated athlete: unlocks the personal training calendar tab
  isAthlete: boolean
  // Explicit consent (GDPR art. 9) to training-calendar data processing. False for every
  // athlete until they pass the one-time consent screen — the calendar API 409s without it.
  trainingConsentGiven: boolean
  emailNotificationsEnabled: boolean
  // Opt-out: everyone is on the public recent-ascents list unless they switch this off
  ascentsPublic: boolean
  preferredLanguage: string
  newsletterSubscribed: boolean
  newsletterChoiceMade: boolean
  hasPassword: boolean
  avatarUrl: string | null
  createdAt: string
}

/**
 * A row of the admin user listing (`GET /admin/users`). Deliberately its own type rather than
 * `User`: that one describes the signed-in account and carries fields this listing never sends
 * (nickname, consent, avatar), while this one carries `emailVerified`, which `/user/me` does not.
 */
export interface AdminUser {
  id: string
  firstName: string
  lastName: string
  email: string
  phone: string
  role: 'USER' | 'ADMIN'
  createdAt: string
  newsletterSubscribed: boolean
  isAthlete: boolean
  // Never confirmed the address: cannot log in, and every binding admin action refuses them
  emailVerified: boolean
}

/**
 * One user's card (`GET /admin/users/{id}`) — the listing row plus everything the four tabs
 * need in their header and Account section. Read-only: there is no request counterpart.
 */
export interface UserDetail {
  id: string
  firstName: string
  lastName: string
  nickname: string
  email: string
  phone: string
  avatarUrl: string | null
  role: 'USER' | 'ADMIN'
  /** Gates the Training tab. Not cosmetic — the endpoints behind it refuse everyone else. */
  athlete: boolean

  emailVerified: boolean
  emailVerifiedAt: string | null
  hasPassword: boolean
  oauthProvider: string | null
  preferredLanguage: string
  emailNotificationsEnabled: boolean
  newsletterSubscribed: boolean
  newsletterChoiceMade: boolean
  newsletterSubscribedAt: string | null
  ascentsPublic: boolean
  trainingConsentAt: string | null
  failedLoginAttempts: number
  lockedUntil: string | null
  accountLocked: boolean
  createdAt: string
  updatedAt: string

  counts: UserCounts
}

/**
 * Headline tiles. The two training numbers are `null` — not `0` — for a non-athlete: that data
 * is out of the admin's reach, and rendering it as a zero would state something false about
 * somebody whose calendar and logbook are private.
 */
export interface UserCounts {
  reservationsConfirmed: number
  reservationsCancelled: number
  trainingsCompleted: number | null
  ascents: number | null
}

export interface UserReservationHistory {
  upcoming: HistoryReservation[]
  past: HistoryReservation[]
  waitlist: HistoryWaitlistEntry[]
  invitations: HistoryInvite[]
  trainingRequests: HistoryRequest[]
}

export interface HistoryReservation {
  id: string
  slotId: string
  eventId: string | null
  date: string
  startTime: string
  endTime: string
  title: string
  eventTitle: string | null
  status: 'CONFIRMED' | 'CANCELLED' | 'CANCELLED_BY_ADMIN'
  participants: number
  comment: string | null
  createdByAdmin: boolean
  createdAt: string
}

/** `kind` says whether this queue hangs off a slot or an event — they are separate tables. */
export interface HistoryWaitlistEntry {
  id: string
  kind: 'SLOT' | 'EVENT'
  targetId: string
  title: string
  date: string | null
  startTime: string | null
  position: number
  status: 'WAITING' | 'PENDING_CONFIRMATION' | 'EXPIRED'
  confirmationDeadline: string | null
  createdAt: string
}

export interface HistoryInvite {
  id: string
  kind: 'SLOT' | 'EVENT'
  targetId: string
  title: string
  date: string | null
  startTime: string | null
  /** Set only when an admin sent the invitation email by hand; never automatic. */
  notifiedAt: string | null
  createdAt: string
}

export interface HistoryRequest {
  id: string
  requestedDate: string
  startTime: string
  endTime: string
  participants: number
  comment: string | null
  status: 'PENDING' | 'ACCEPTED' | 'CONTACTED' | 'REJECTED' | 'EXPIRED'
  adminNote: string | null
  courseTitle: string | null
  resolvedAt: string | null
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
  hasAvailabilityWindow: boolean
  // day with no seats free for the public, but with invitation-held seats
  hasReservedSeats: boolean
  // hours of instructor absence, sorted — an absence covers PART of a day, so the month cell
  // names the hours instead of colouring the whole cell
  unavailableRanges: UnavailableRange[]
}

export interface UnavailableRange {
  startTime: string
  endTime: string
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
  currentParticipants: number
  status: SlotStatus
  isUserRegistered: boolean
  eventTitle: string | null
  isAvailabilityWindow: boolean
  isUnavailable: boolean
  // Invitation-held seats
  reservedSeats: number
  isReservedForUser: boolean
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
  // Waitlist
  userWaitlistStatus: WaitlistStatus | null
  waitlistEntryId: string | null
  confirmationDeadline: string | null
  userWaitlistPosition: number
  isAvailabilityWindow: boolean
  isUnavailable: boolean
  title: string | null
  // Invitation-held seats
  reservedSeats: number
  isReservedForUser: boolean
}

export type SlotStatus = 'AVAILABLE' | 'FULL' | 'BLOCKED' | 'PAST' | 'BOOKING_CLOSED' | 'AVAILABILITY_WINDOW' | 'UNAVAILABLE'

export type WaitlistStatus = 'WAITING' | 'PENDING_CONFIRMATION'

export interface WaitlistEntry {
  id: string
  slotId: string
  slotDate: string
  slotStartTime: string
  slotEndTime: string
  slotTitle: string | null
  status: WaitlistStatus
  confirmationDeadline: string | null
  position: number
}

// Event types
export interface EventSummary {
  id: string
  title: string
  description: string | null
  location: string | null
  eventType: EventType
  startDate: string
  endDate: string
  startTime: string | null
  endTime: string | null
  isMultiDay: boolean
  maxParticipants: number
  currentParticipants: number
  isUserRegistered: boolean
  enrollmentOpen: boolean
  courseId: string | null
  coursePublished: boolean
  // Waitlist — null in list views, populated in getEventSummary (single event)
  userWaitlistStatus: WaitlistStatus | null
  waitlistEntryId: string | null
  confirmationDeadline: string | null
  userWaitlistPosition: number
  // 0 in list views, populated in getEventSummary when user is registered
  userParticipants: number
  // Invitation-held seats
  reservedSeats: number
  isReservedForUser: boolean
}

export interface EventWaitlistEntry {
  id: string
  eventId: string
  eventTitle: string
  eventStartDate: string
  eventEndDate: string
  status: WaitlistStatus
  confirmationDeadline: string | null
  position: number
}

// Pending invitation (seat held for the user) for an upcoming slot/event
export interface MyInvitation {
  type: 'SLOT' | 'EVENT'
  slotId: string | null
  eventId: string | null
  title: string | null
  eventType: EventType | null
  date: string
  endDate: string | null
  startTime: string | null
  endTime: string | null
  location: string | null
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
  currentParticipants: number
  active: boolean
  startTime: string | null
  endTime: string | null
  courseId: string | null
  courseTitle: string | null
  slots?: TimeSlotAdmin[]
}

export type EventType = 'COURSE' | 'TRAINING' | 'WORKSHOP' | 'CONTACT_DAY' | 'UNAVAILABLE'

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
  spotsAvailable: number
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
  spotsAvailable: number
  createdAt: string
  courseId: string | null
  cancelledByAdmin: boolean
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
  isAvailabilityWindow: boolean
  isUnavailable: boolean
}

export interface SlotParticipants {
  slotId: string
  date: string
  startTime: string
  endTime: string
  maxParticipants: number
  participants: Participant[]
  guestParticipants: GuestParticipant[]
}

export interface Participant {
  reservationId: string
  userId: string
  fullName: string
  email: string
  phone: string
  comment: string | null
  participants: number
  registeredAt: string
}

export interface GuestParticipant {
  id: string
  note: string
  participants: number
  createdAt: string
}

export interface EventParticipants {
  eventId: string
  maxParticipants: number
  participants: Participant[]
  guestParticipants: GuestParticipant[]
}

export interface CreateTimeSlotRequest {
  date: string
  startTime: string
  endTime: string
  maxParticipants: number
  title?: string
  eventId?: string
  isAvailabilityWindow?: boolean
  isUnavailable?: boolean
  invitedUserIds?: string[]
  // Training request this slot is created from (→ ACCEPTED + link)
  trainingRequestId?: string
}

// Invitee of a held seat (prefill for admin forms)
export interface InvitedUser {
  userId: string
  fullName: string
  email: string
  // When the admin manually sent the invitation email (null = not yet)
  notifiedAt?: string | null
}

export interface CourseEvent {
  eventId: string
  startDate: string
  endDate: string
  startTime: string | null
  endTime: string | null
  status: SlotStatus
  availableSpots: number
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
  eventId: string | null
  isNew: boolean
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
  courseId?: string
  invitedUserIds?: string[]
  // Training request this event is created from (→ ACCEPTED + link)
  trainingRequestId?: string
}

// Training requests
export type TrainingRequestStatus = 'PENDING' | 'ACCEPTED' | 'CONTACTED' | 'REJECTED' | 'EXPIRED'

export interface CreateTrainingRequest {
  requestedDate: string
  startTime: string
  endTime: string
  participants: number
  comment?: string
  courseId?: string
  windowSlotId?: string
}

export interface TrainingRequest {
  id: string
  requestedDate: string
  startTime: string
  endTime: string
  participants: number
  comment: string | null
  status: TrainingRequestStatus
  adminNote: string | null
  courseTitle: string | null
  createdSlotId: string | null
  createdSlotDate: string | null
  createdEventId: string | null
  createdEventStartDate: string | null
  createdAt: string
}

// Admin panel notification counters (badges on tabs + the Admin navbar link)
export interface AdminNotifications {
  pendingRequests: number
  newReservations: number
  // New waitlist joins since last "read" (same marker as newReservations)
  newWaitlistEntries: number
  // Unread athlete activity in training calendars (per this admin's read markers)
  athleteActivity: number
}

// Waitlist entry in admin views (participants modal + Reservations tab)
export interface WaitlistAdminEntry {
  waitlistId: string
  userId: string
  fullName: string
  email: string
  phone: string
  position: number
  status: 'WAITING' | 'PENDING_CONFIRMATION' | 'EXPIRED'
  confirmationDeadline: string | null
  joinedAt: string
  isNew: boolean
}

export interface SlotWaitlistAdmin {
  slotId: string
  date: string
  startTime: string
  endTime: string
  entries: WaitlistAdminEntry[]
}

export interface EventWaitlistAdmin {
  eventId: string
  title: string
  startDate: string
  endDate: string
  entries: WaitlistAdminEntry[]
}

// Global "Waitlists" view: upcoming slots/events someone is waiting for
export interface AdminWaitlists {
  slotWaitlists: (SlotWaitlistAdmin & { title: string | null })[]
  eventWaitlists: EventWaitlistAdmin[]
}

export interface AdminTrainingRequestPage {
  content: AdminTrainingRequest[]
  page: number
  totalPages: number
  totalElements: number
}

export interface AdminTrainingRequest {
  id: string
  userId: string
  userFullName: string
  userEmail: string
  userPhone: string
  requestedDate: string
  startTime: string
  endTime: string
  participants: number
  comment: string | null
  status: TrainingRequestStatus
  adminNote: string | null
  courseId: string | null
  courseTitle: string | null
  inWindow: boolean
  windowSlotId: string | null
  windowStartTime: string | null
  windowEndTime: string | null
  createdSlotId: string | null
  createdSlotDate: string | null
  createdEventId: string | null
  createdEventStartDate: string | null
  createdAt: string
  resolvedAt: string | null
}

// Activity Log types
export type ActivityActionType =
  | 'RESERVATION_CREATED'
  | 'RESERVATION_CANCELLED'
  | 'RESERVATION_REACTIVATED'
  | 'RESERVATION_UPDATED'
  | 'EVENT_RESERVATION_CREATED'
  | 'EVENT_RESERVATION_UPDATED'
  | 'EVENT_RESERVATION_CANCELLED'
  | 'RESERVATION_CANCELLED_BY_ADMIN'
  | 'ADMIN_SLOT_CREATED'
  | 'ADMIN_SLOT_UPDATED'
  | 'ADMIN_SLOT_DELETED'
  | 'ADMIN_SLOT_BLOCKED'
  | 'ADMIN_SLOT_UNBLOCKED'
  | 'ADMIN_EVENT_CREATED'
  | 'ADMIN_EVENT_UPDATED'
  | 'ADMIN_EVENT_DELETED'
  | 'ADMIN_USER_MAKE_ADMIN'
  | 'ADMIN_USER_ADMIN_REMOVED'
  | 'ADMIN_USER_DELETED'
  | 'ADMIN_USER_FORCE_LOGOUT'
  | 'ADMIN_USER_ATHLETE_TOGGLED'
  | 'ADMIN_TRAINING_CREATED'
  | 'ADMIN_TRAINING_UPDATED'
  | 'ADMIN_TRAINING_DELETED'
  | 'ADMIN_GOAL_CREATED'
  | 'ADMIN_GOAL_UPDATED'
  | 'ADMIN_GOAL_DELETED'
  | 'ADMIN_GOAL_ACHIEVED'
  | 'ADMIN_GOAL_REOPENED'

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
  description: string | null
  createdAt: string
}

// Instructor types
export type InstructorType = 'INSTRUCTOR' | 'COMPETITOR'

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
  memberType: InstructorType
  profile8aUrl: string | null
  createdAt: string
  language: string
  translationGroupId: string
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
  memberType: InstructorType
  profile8aUrl: string | null
  displayOrder: number
  active: boolean
  createdAt: string
  updatedAt: string
  language: string
  translationGroupId: string
}

export interface CreateInstructorRequest {
  firstName: string
  lastName: string
  bio?: string
  certifications?: string
  memberType?: InstructorType
  profile8aUrl?: string
  language?: string
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
  memberType?: InstructorType
  profile8aUrl?: string
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
  published: boolean
  publishedAt: string | null
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
  starred: boolean | null
  language: string
  translationGroupId: string
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
  starred: boolean | null
  language: string
  translationGroupId: string
}

export interface NewsTranslation {
  id: string
  language: string
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
  language: string
  translationGroupId: string
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
  language: string
  translationGroupId: string
  blocks: ContentBlockAdmin[]
  createdAt: string
  updatedAt: string
}

export interface CreateNewsRequest {
  title: string
  excerpt?: string
  language?: string
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
  price: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  language: string
  translationGroupId: string
  publishedAt: string | null
}

export interface CourseDetail {
  id: string
  title: string
  price: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  language: string
  translationGroupId: string
  blocks: ContentBlock[]
  publishedAt: string | null
}

export interface CourseTranslation {
  id: string
  language: string
}

export interface CourseAdmin {
  id: string
  title: string
  price: string | null
  thumbnailUrl: string | null
  displayOrder: number
  published: boolean
  language: string
  translationGroupId: string
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CourseDetailAdmin {
  id: string
  title: string
  price: string | null
  thumbnailFilename: string | null
  thumbnailUrl: string | null
  thumbnailFocalPointX: number | null
  thumbnailFocalPointY: number | null
  published: boolean
  language: string
  translationGroupId: string
  publishedAt: string | null
  blocks: ContentBlockAdmin[]
  createdAt: string
  updatedAt: string
}

export interface CreateCourseRequest {
  title: string
  price?: string
  language?: string
}

export interface UpdateCourseMetaRequest {
  title?: string
  price?: string
}

export interface OrphanedFileDto {
  folder: string
  filename: string
  sizeBytes: number
}

export interface MissingFileDto {
  folder: string
  filename: string
}

export interface StorageAuditResult {
  orphanedFiles: OrphanedFileDto[]
  missingFiles: MissingFileDto[]
  totalFilesOnDisk: number
  totalFilesInDb: number
  totalSizeBytesOnDisk: number
}

export interface DeleteOrphanedResult {
  deletedCount: number
}

// Videos

export interface VideoDto {
  id: string
  title: string
  excerpt: string | null
  content: string | null
  youtubeUrl: string
  displayOrder: number
  publishedAt: string
}

export interface VideoAdmin {
  id: string
  title: string
  excerpt: string | null
  content: string | null
  youtubeUrl: string
  displayOrder: number
  published: boolean
  publishedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateVideoRequest {
  title: string
  excerpt?: string
  content?: string
  youtubeUrl: string
}

export interface UpdateVideoRequest {
  title?: string
  excerpt?: string
  content?: string
  youtubeUrl?: string
}

// Site Settings
export interface HeroImageDto {
  imageUrl: string | null
  focalPointX: number | null
  focalPointY: number | null
}

export interface BadgeImageDto {
  imageUrl: string | null
  linkUrl: string | null
}

export interface HomeSettingsDto {
  hero: HeroImageDto
  heroMobile: HeroImageDto
  badge: BadgeImageDto
  badgeLeft: BadgeImageDto
  location: LocationSectionDto
}

export interface SlotTemplate {
  name: string
  maxParticipants: number
}

// "Where I teach now" — editable location section.
// The title is fixed (translated via i18n). Badge, subtitle and the list of
// places are editable per language.
export interface LocationContentDto {
  badge: string
  subtitle: string
  locations: string[]
}

export interface LocationSectionDto {
  enabled: boolean
  translations: Record<string, LocationContentDto>
}

export interface LocationPresetDto {
  id: string | null
  name: string
  translations: Record<string, LocationContentDto>
}

// Which template is currently live on the page (null = section hidden)
export interface LocationActiveStateDto {
  activePresetId: string | null
}

// Calendar promo. Required: title + description.
// Opcjonalne: badge (plakietka) i przycisk CTA (ctaLabel + ctaUrl).
export interface CalendarPromoContentDto {
  badge: string
  title: string
  description: string
  ctaLabel: string
  ctaUrl: string
}

export interface CalendarPromoSectionDto {
  enabled: boolean
  translations: Record<string, CalendarPromoContentDto>
}

export interface CalendarPromoPresetDto {
  id: string | null
  name: string
  translations: Record<string, CalendarPromoContentDto>
}

// ===== Personal training calendar (TrainingPeaks-style) =====

// MISSED is derived server-side: planned + end time in the past, never stored
export type PersonalTrainingStatus = 'PLANNED' | 'COMPLETED' | 'MISSED'

export type AttachmentKind = 'LINK' | 'FILE'

// A material attached to a training: a LINK (embedUrl set for YouTube/Instagram → iframe) or
// an uploaded FILE (url points at the serve endpoint; fileName/mimeType drive the card).
export interface TrainingAttachment {
  id: string
  kind: AttachmentKind
  url: string | null
  label: string | null
  embedUrl: string | null
  // FILE only
  filename: string | null
  fileName: string | null
  mimeType: string | null
  sizeBytes: number | null
}

// One material as sent to the API: either a link (url) or a file (uploaded filename + metadata)
export interface AttachmentInput {
  kind: AttachmentKind
  url?: string
  filename?: string
  originalName?: string
  mimeType?: string
  sizeBytes?: number
  label?: string
}

// Response from the file-upload endpoint; echoed back as a FILE attachment on save
export interface AttachmentUpload {
  filename: string
  originalName: string
  mimeType: string
  sizeBytes: number
}

export interface CreatePersonalTraining {
  // Omitted means TRAINING. Ignored by the server on update — an entry is a training or a task
  // from birth, so the edit form never sends it.
  kind?: TrainingKind
  date: string
  // Untimed ("all-day") training: omit/null both. Otherwise both set. A task must omit both.
  startTime?: string | null
  endTime?: string | null
  title: string
  description?: string
  // Task only; the server rejects it on a training rather than dropping it silently
  targetCalories?: number | null
  // undefined = leave attachments untouched (move/drag); [] = clear; a list = replace
  attachments?: AttachmentInput[]
}

/**
 * What an entry on the plan is. A TASK ("stay under 2200 kcal today", "drink 3 litres of water")
 * is a separate entry from the training it shares a day with, so the session and the commitment
 * are ticked off — and fail — independently. Fixed at creation; no endpoint changes it.
 */
export type TrainingKind = 'TRAINING' | 'TASK'

export interface PersonalTraining {
  id: string
  kind: TrainingKind
  date: string
  // Null for untimed ("all-day") trainings, and always null for a task
  startTime: string | null
  endTime: string | null
  title: string
  description: string | null
  // Task only, and optional there too — "drink 3 litres" carries its number in the title
  targetCalories: number | null
  createdByAdmin: boolean
  status: PersonalTrainingStatus
  completedAt: string | null
  feedback: string | null
  rpe: number | null
  // Unread activity from the other side (viewer-dependent)
  hasUnreadActivity: boolean
  createdAt: string
  attachments: TrainingAttachment[]
}

// Read-only overlay: athlete's confirmed booking from the public reservation system
export interface ReservationOverlayItem {
  id: string
  // Slot behind the booking — used to open the full slot-detail modal in place
  slotId: string
  date: string
  startTime: string
  endTime: string
  title: string | null
  // Coach view only: booked by the athlete since the coach's last visit (unread dot)
  isNew: boolean
  // Athlete RPE rating for this attended booking (null = not rated)
  rpe: number | null
  rpeNote: string | null
  // The booking is over → the athlete may rate it (UI also gates on !coach view)
  canRate: boolean
}

// A future training removed by the other side since the viewer's last visit
export interface TrainingDeletionItem {
  date: string
  // Null when the deleted training was untimed ("all-day")
  startTime: string | null
  endTime: string | null
  title: string
  deletedByAdmin: boolean
  deletedAt: string
}

// Held seat awaiting booking — a call-to-action, deliberately NOT styled like a reservation
export interface InvitationOverlayItem {
  slotId: string | null
  eventId: string | null
  date: string
  startTime: string | null
  endTime: string | null
  title: string | null
}

export interface TrainingCalendarRange {
  trainings: PersonalTraining[]
  reservations: ReservationOverlayItem[]
  invitations: InvitationOverlayItem[]
  deletions: TrainingDeletionItem[]
}

export interface TrainingCommentItem {
  id: string
  // Null when the message is nothing but attachments
  body: string | null
  authorIsAdmin: boolean
  authorName: string
  authorAvatarUrl: string | null
  createdAt: string
  // Whether the viewer wrote this message (chat alignment)
  mine: boolean
  files: TrainingCommentFile[]
}

export interface TrainingCommentFile {
  id: string
  /** Needs the bearer token — these never enter the public /api/files namespace. */
  url: string
  mimeType: string
  fileName: string | null
  sizeBytes: number
  /** Null for a PDF. Present for images so the thread reserves space before the bytes arrive. */
  width: number | null
  height: number | null
  /** When the retention sweep removes it. Shown, so its disappearance is never a surprise. */
  expiresAt: string
  canDelete: boolean
}

export interface TrainingCalendarNotifications {
  newCount: number
}

export interface TrainingTypeBreakdown {
  personal: number
  individualSlot: number
  course: number
  training: number
  workshop: number
}

// SHORT/MEDIUM/LONG-term goal set by the coach; also picks the trophy size in the chest
export type GoalHorizon = 'SHORT' | 'MEDIUM' | 'LONG'

// Which banner row the goal belongs to. WEIGHT goals close themselves on a weigh-in.
export type GoalKind = 'GENERAL' | 'WEIGHT'

export interface AthleteGoal {
  id: string
  kind: GoalKind
  horizon: GoalHorizon
  content: string
  targetDate: string
  // WEIGHT only: the target, and the trend snapshot the progress bar starts from
  targetWeightKg: number | null
  startWeightKg: number | null
  // Closed by a weigh-in rather than by the coach — the only case that may be reopened
  achievedAutomatically: boolean
  achievedAt: string | null
  createdAt: string
}

// Banner cards (active, one per kind + horizon) + trophy chest (achieved, newest first)
export interface AthleteGoals {
  active: AthleteGoal[]
  achieved: AthleteGoal[]
}

export interface SaveGoal {
  kind?: GoalKind
  horizon: GoalHorizon
  content: string
  targetDate: string
  targetWeightKg?: number | null
}

// ---------- body weight ----------

export interface WeightEntry {
  measuredOn: string
  weightKg: number
  // Trailing 7-day average as of that day — computed server-side so there is one implementation
  trendKg: number
}

// Everything the weight panel draws, derived server-side so the trend has one implementation
export interface WeightSeries {
  entries: WeightEntry[]
  currentTrendKg: number | null
  // How many readings back the trend (0-7); below 3 it is shown but cannot close a goal
  trendSampleCount: number
  trendConfirmed: boolean
  weeklyChangePercent: number | null
  // Losing faster than 1%/week — surfaced to the coach only
  rapidLoss: boolean
  latestWeightKg: number | null
  latestMeasuredOn: string | null
  // How far back a reading may be backfilled. Fixed policy, NOT the selected range — the date
  // picker derives its `min` from this so it cannot drift from what the server accepts
  backfillDays: number
}

// Closed set of named windows. Keeping this an enum (rather than a day count) means no
// client can ask the API for an unbounded history.
export type WeightRange = 'RECENT' | 'YEAR' | 'ALL'

export interface SaveWeight {
  measuredOn: string
  weightKg: number
}

// ---------- climbing logbook ----------

// Every entry is a COMPLETED ascent. Attempts and open projects are not modelled: the pyramid
// and the onsight rate count ascents, so a "tried it" row would sit in the denominator of both.
export type AscentDiscipline = 'SPORT' | 'BOULDER' | 'TRAD'

// OS > FLASH > RP > PP > TR in cleanliness. Never translated — international shorthand.
// OS > FLASH > RP > TR in cleanliness, with SOLO/FREE_SOLO above them as a different kind of
// commitment. Pinkpoint was dropped; the two-letter names stay untranslated, SOLO/FREE_SOLO do not.
export type AscentStyle = 'OS' | 'FLASH' | 'RP' | 'TR' | 'SOLO' | 'FREE_SOLO' | 'A0'

// Two separate axes. 7a (French route) and 7A (Font) are three grades apart, so nothing
// compares a grade from one scale with a grade from the other.
export type GradeScale = 'FRENCH_ROUTE' | 'FONT_BOULDER'

// Where the ascent happened, and therefore which shape the entry takes. Rock entries carry a
// discipline, attempts and a star rating; mountain entries carry a season, length, pitches,
// duration, partners and what the author led. Both share one grade axis: French.
export type AscentTerrain = 'ROCK' | 'MOUNTAIN'

export interface Ascent {
  id: string
  terrain: AscentTerrain
  climbedOn: string
  // Rock only — mountains tell entries apart by season instead
  discipline: AscentDiscipline | null
  gradeScale: GradeScale
  // Enum constant (FR_7A_PLUS) — what goes back on update
  grade: string
  // What the climber reads on the topo (7a+). Comes from the server; never derived here.
  gradeLabel: string
  // Ordering within gradeScale only. Sorting the table by difficulty uses this, not the label.
  gradeRank: number
  style: AscentStyle
  area: string
  crag: string
  routeName: string
  attempts: number | null
  qualityStars: number | null
  comment: string | null
  // ---- mountain only ----
  winter: boolean | null
  originalGrade: string | null
  lengthMeters: number | null
  pitches: number | null
  durationMinutes: number | null
  ledGrade: string | null
  ledGradeLabel: string | null
  ledGradeRank: number | null
  ledPitches: number | null
  partners: string | null
  createdAt: string
}

export interface SaveAscent {
  terrain: AscentTerrain
  climbedOn: string
  discipline: AscentDiscipline | null
  grade: string
  style: AscentStyle
  area: string
  // The crag on rock, the summit in the mountains — the same slot either way
  crag: string
  routeName: string
  attempts: number | null
  qualityStars: number | null
  comment: string | null
  winter: boolean | null
  originalGrade: string | null
  lengthMeters: number | null
  pitches: number | null
  durationMinutes: number | null
  ledGrade: string | null
  ledPitches: number | null
  partners: string | null
}

// Areas the athlete has logged before, with their crags — computed across every year, so a crag
// from 2019 still autocompletes while they are looking at 2026.
export interface PlaceSuggestion {
  area: string
  crags: string[]
}

export interface AscentLog {
  entries: Ascent[]
  availableYears: number[]
  // null means "all years"; echoed by the server so the picker cannot drift from the filter
  selectedYear: number | null
  // Across every year — tells an empty year apart from an empty logbook
  totalCount: number
  places: PlaceSuggestion[]
}

// The grade/style catalogue, served by the backend so the form never hardcodes a list:
// a form offering a value the API refuses is a 400 for something that looked available.
export interface GradeOption {
  value: string
  label: string
  rank: number
}

export interface DisciplineOption {
  value: AscentDiscipline
  gradeScale: GradeScale
  styles: AscentStyle[]
}

export interface AscentOptions {
  disciplines: DisciplineOption[]
  // Styles the mountains allow — no toprope, and no discipline to hang them off
  mountainStyles: AscentStyle[]
  gradesByScale: Record<GradeScale, GradeOption[]>
}

// One entry of the public recent-ascents list on the news page. Carries the climber's name —
// and deliberately nothing private: no comment, no attempts, no rating.
export interface PublicAscent {
  id: string
  climberName: string
  climbedOn: string
  terrain: AscentTerrain
  // Null on mountain entries — they carry a season instead
  discipline: AscentDiscipline | null
  gradeScale: GradeScale
  gradeLabel: string
  gradeRank: number
  style: AscentStyle
  area: string
  crag: string
  routeName: string
}

export interface PyramidRow {
  grade: string
  gradeLabel: string
  rank: number
  byStyle: Partial<Record<AscentStyle, number>>
  total: number
}

export interface BestAscent {
  grade: string
  gradeLabel: string
  rank: number
  routeName: string
  crag: string
  climbedOn: string
}

export interface GradeProgressPoint {
  year: number
  bestGradeLabel: string | null
  bestRank: number | null
  bestOnsightLabel: string | null
  bestOnsightRank: number | null
}

// One block per discipline. Grades are only ever compared inside a block.
export interface AscentDisciplineStats {
  discipline: AscentDiscipline
  gradeScale: GradeScale
  ascentCount: number
  pyramid: PyramidRow[]
  hardestByStyle: Partial<Record<AscentStyle, BestAscent>>
  styleDistribution: Partial<Record<AscentStyle, number>>
  onsightRatePercent: number | null
  // Always all-time, whatever year is selected — a progression cropped to one year is a point
  progressionByYear: GradeProgressPoint[]
}

export interface AreaCount {
  area: string
  ascentCount: number
}

// Deliberately no "days on rock": only sends are logged, so a day of failed attempts or a
// rained-off day leaves no row, and the count would have measured diligence, not climbing.
// Every mountain total ships with the count it was built from: length, pitches and duration are
// optional, so "4200 m" alone would not say whether that is a season or two measured entries.
export interface MountainStats {
  summerCount: number
  winterCount: number
  totalMeters: number
  entriesWithLength: number
  totalPitches: number
  entriesWithPitches: number
  totalMinutes: number
  entriesWithDuration: number
  summitCount: number
  // The routes themselves — mountains have no discipline blocks, so this is the only place
  // the level being climbed shows up
  pyramid: PyramidRow[]
  hardestByStyle: Partial<Record<AscentStyle, BestAscent>>
  // What the author LED — a different question from what the route was graded
  leadPyramid: PyramidRow[]
  hardestLed: BestAscent | null
  ledPitchesTotal: number
}

export interface AscentStats {
  selectedYear: number | null
  totalAscents: number
  firstAscentDate: string | null
  // Distinct places, grouped on the normalized key so one crag spelled two ways counts once
  areaCount: number
  cragCount: number
  avgAttemptsToRedpoint: number | null
  // How many redpoints the average is built from — the attempts field is optional, so the
  // figure means nothing without its denominator
  redpointsWithAttempts: number
  avgQualityStars: number | null
  topAreas: AreaCount[]
  // Only disciplines with at least one ascent, busiest first. Empty on a mountain logbook.
  disciplines: AscentDisciplineStats[]
  // Present only on a mountain logbook
  mountain: MountainStats | null
}

// Reusable coach training template (shared library). Materials reuse TrainingAttachment.
export interface TrainingTemplate {
  id: string
  // What applying this template creates. Editable here, unlike an entry's own kind:
  // a template holds no completion or rating to lose.
  kind: TrainingKind
  title: string
  description: string | null
  // Set for a TRAINING, null for a TASK (a whole-day commitment has no span to prefill)
  defaultDurationMinutes: number | null
  targetCalories: number | null
  attachments: TrainingAttachment[]
  updatedAt: string
}

export interface SaveTrainingTemplate {
  kind: TrainingKind
  title: string
  description?: string
  // Exactly one of the two, matching the kind — the backend rejects the mismatch
  defaultDurationMinutes?: number | null
  targetCalories?: number | null
  attachments?: AttachmentInput[]
}

// One uploaded file in the central materials-management list
export interface TrainingMaterial {
  id: string
  fileName: string | null
  mimeType: string | null
  sizeBytes: number | null
  url: string
  ownerType: 'TRAINING' | 'TEMPLATE'
  ownerLabel: string
  createdAt: string
}

// Live-derived stats under the training calendar; null fields mean "no data — hide the tile"
export interface AthleteStats {
  thisMonthCount: number
  prevMonthCount: number
  totalCount: number
  firstActivityDate: string | null
  currentStreakWeeks: number
  bestStreakWeeks: number
  avgPerMonth: number | null
  // yyyy-MM-dd -> activity count, last 365 days, non-zero days only
  heatmap: Record<string, number>
  byType: TrainingTypeBreakdown
  attendanceRatePercent: number | null
  avgRpeOverall: number | null
  avgRpeLast30Days: number | null
  // Session counts per intensity band over the last 90 days (both sources)
  rpeDistribution: RpeDistribution
  // Last 5 ratings all >= 9 → overtraining/inflation hint
  sustainedHighRpe: boolean
  // Past attended reservations not yet rated (athlete nudge)
  unratedActivitiesCount: number
  // Tasks, counted apart from every training number above
  tasks: TaskStats
}

/**
 * Every count ships the denominator it belongs to: "3 done" cannot tell three-of-three from
 * three-of-twelve, and a bare 0 cannot tell a month of blown ceilings from a month where none
 * were set.
 */
export interface TaskStats {
  thisMonthDone: number
  thisMonthDue: number
  // Rolling 30 days
  windowDone: number
  windowDue: number
  // null until something has come due — 0% would claim a failure that never happened
  completionPercent: number | null
}

export interface RpeDistribution {
  light: number
  medium: number
  hard: number
}

// Coach's roster entry (admin panel)
export interface AthleteSummary {
  id: string
  firstName: string
  lastName: string
  nickname: string
  avatarUrl: string | null
  newCount: number
  lastActivityAt: string | null
}
