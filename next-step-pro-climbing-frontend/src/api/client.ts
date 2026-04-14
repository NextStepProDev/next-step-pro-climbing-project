import i18n from '../i18n'
import type {
  AssetDto,
  EventWaitlistEntry,
  User,
  MonthView,
  WeekView,
  DayView,
  TimeSlotDetail,
  CourseEvent,
  EventSummary,
  UserReservation,
  MyReservations,
  ReservationResult,
  EventReservationResult,
  TimeSlotAdmin,
  SlotParticipants,
  CreateTimeSlotRequest,
  CreateEventRequest,
  EventDetail,
  EventParticipants,
  ReservationAdmin,
  ActivityLog,
  InstructorPublic,
  InstructorAdmin,
  CreateInstructorRequest,
  UpdateInstructorRequest,
  AlbumSummary,
  AlbumDetail,
  AlbumAdmin,
  AlbumDetailAdmin,
  CreateAlbumRequest,
  UpdateAlbumRequest,
  ReorderAlbumsRequest,
  UpdatePhotoRequest,
  UploadPhotoResponse,
  SetThumbnailPhotoRequest,
  NewsDetail,
  NewsAdmin,
  NewsDetailAdmin,
  ContentBlockAdmin,
  CreateNewsRequest,
  UpdateNewsMetaRequest,
  AddTextBlockRequest,
  UpdateTextBlockRequest,
  UpdateImageBlockRequest,
  UploadBlockImageResponse,
  UploadThumbnailResponse,
  NewsPageDto,
  AdminNewsPageDto,
  CourseSummary,
  CourseDetail,
  CourseAdmin,
  CourseDetailAdmin,
  CreateCourseRequest,
  UpdateCourseMetaRequest,
  StorageAuditResult,
  DeleteOrphanedResult,
} from '../types'
import {
  getAccessToken,
  getRefreshToken,
  isAccessTokenExpired,
  saveTokens,
  clearTokens,
} from '../utils/tokenStorage'
import { refreshTokens } from './auth'

const API_BASE = '/api'

let refreshPromise: Promise<string | null> | null = null

async function ensureValidToken(): Promise<string | null> {
  const accessToken = getAccessToken()
  if (!accessToken) return null

  if (!isAccessTokenExpired()) return accessToken

  if (refreshPromise) return refreshPromise

  refreshPromise = doRefresh()
  try {
    return await refreshPromise
  } finally {
    refreshPromise = null
  }
}

async function doRefresh(): Promise<string | null> {
  const refresh = getRefreshToken()
  if (!refresh) {
    clearTokens()
    return null
  }

  try {
    const tokens = await refreshTokens(refresh)
    saveTokens(tokens)
    return tokens.accessToken
  } catch {
    clearTokens()
    window.dispatchEvent(new CustomEvent('auth:session-expired'))
    return null
  }
}

async function fetchApi<T>(
  endpoint: string,
  options?: RequestInit
): Promise<T> {
  const token = await ensureValidToken()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept-Language': i18n.language,
    ...(options?.headers as Record<string, string>),
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 30000)

  let response: Response
  try {
    response = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers,
      signal: controller.signal,
    })
  } catch {
    clearTimeout(timeoutId)
    throw new Error(i18n.t('network', { ns: 'errors' }))
  }

  // If 401, try one refresh and retry
  if (response.status === 401 && token) {
    const newToken = await doRefresh()
    if (newToken) {
      headers['Authorization'] = `Bearer ${newToken}`
      clearTimeout(timeoutId)
      const retryController = new AbortController()
      const retryTimeoutId = setTimeout(() => retryController.abort(), 30000)
      try {
        response = await fetch(`${API_BASE}${endpoint}`, {
          ...options,
          headers,
          signal: retryController.signal,
        })
      } catch {
        clearTimeout(retryTimeoutId)
        throw new Error(i18n.t('network', { ns: 'errors' }))
      }
      clearTimeout(retryTimeoutId)
    } else {
      clearTimeout(timeoutId)
      throw new Error(i18n.t('sessionExpired', { ns: 'errors' }))
    }
  } else {
    clearTimeout(timeoutId)
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const serverMessage = body?.message
    if (serverMessage) {
      throw new Error(serverMessage)
    }
    if (response.status === 500) {
      throw new Error(i18n.t('server', { ns: 'errors' }))
    }
    if (response.status === 503) {
      throw new Error(i18n.t('serviceUnavailable', { ns: 'errors' }))
    }
    if (response.status === 404) {
      throw new Error(i18n.t('notFound', { ns: 'errors' }))
    }
    if (response.status === 403) {
      throw new Error(i18n.t('forbidden', { ns: 'errors' }))
    }
    throw new Error(i18n.t('generic', { status: response.status, ns: 'errors' }))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}

// Auth
export const authApi = {
  getCurrentUser: () => fetchApi<User>('/user/me'),
  logout: () => {
    clearTokens()
  },
  changePassword: (currentPassword: string, newPassword: string) =>
    fetchApi<void>('/user/me/password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword }),
    }),
  deleteAccount: (password: string) =>
    fetchApi<void>('/user/me', {
      method: 'DELETE',
      body: JSON.stringify({ password }),
    }),
  updateNotifications: (enabled: boolean) =>
    fetchApi<void>('/user/me/notifications', {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
  updateLanguage: (language: string) =>
    fetchApi<void>('/user/me/language', {
      method: 'PUT',
      body: JSON.stringify({ language }),
    }),
  updateNewsletter: (subscribed: boolean) =>
    fetchApi<void>('/user/me/newsletter', {
      method: 'PUT',
      body: JSON.stringify({ subscribed }),
    }),
  updateProfile: (firstName: string, lastName: string, phone: string, nickname: string) =>
    fetchApi<User>('/user/me', {
      method: 'PUT',
      body: JSON.stringify({ firstName, lastName, phone, nickname }),
    }),
}

// Calendar
export const calendarApi = {
  getMonthView: (yearMonth: string) =>
    fetchApi<MonthView>(`/calendar/month/${yearMonth}`),

  getWeekView: (date: string) =>
    fetchApi<WeekView>(`/calendar/week/${date}`),

  getDayView: (date: string) =>
    fetchApi<DayView>(`/calendar/day/${date}`),

  getSlotDetails: (slotId: string) =>
    fetchApi<TimeSlotDetail>(`/calendar/slot/${slotId}`),

  getEventSummary: (eventId: string) =>
    fetchApi<EventSummary>(`/calendar/event/${eventId}`),

  getCourseEvents: (courseId: string) =>
    fetchApi<CourseEvent[]>(`/calendar/course/${courseId}/events`),
}

// Reservations
export const reservationApi = {
  create: (slotId: string, comment?: string, participants?: number) =>
    fetchApi<ReservationResult>(`/reservations/slot/${slotId}`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment || null, participants: participants || 1 }),
    }),

  cancel: (reservationId: string) =>
    fetchApi<void>(`/reservations/${reservationId}`, { method: 'DELETE' }),

  getMyReservations: () =>
    fetchApi<UserReservation[]>('/reservations/my'),

  getMyUpcoming: () =>
    fetchApi<MyReservations>('/reservations/my/upcoming'),

  getMyPast: () =>
    fetchApi<MyReservations>('/reservations/my/past'),

  createForEvent: (eventId: string, comment?: string, participants?: number) =>
    fetchApi<EventReservationResult>(`/reservations/event/${eventId}`, {
      method: 'POST',
      body: JSON.stringify({ comment: comment || null, participants: participants || 1 }),
    }),

  cancelForEvent: (eventId: string) =>
    fetchApi<void>(`/reservations/event/${eventId}`, { method: 'DELETE' }),

  updateParticipants: (reservationId: string, participants: number) =>
    fetchApi<import('../types').ReservationResult>(`/reservations/${reservationId}/participants`, {
      method: 'PUT',
      body: JSON.stringify({ participants }),
    }),

  updateEventParticipants: (eventId: string, participants: number) =>
    fetchApi<import('../types').EventReservationResult>(`/reservations/event/${eventId}/participants`, {
      method: 'PUT',
      body: JSON.stringify({ participants }),
    }),

  joinWaitlist: (slotId: string) =>
    fetchApi<{ success: boolean; message: string }>(`/reservations/slot/${slotId}/waitlist`, { method: 'POST' }),

  leaveWaitlist: (slotId: string) =>
    fetchApi<void>(`/reservations/slot/${slotId}/waitlist`, { method: 'DELETE' }),

  confirmWaitlistOffer: (waitlistId: string) =>
    fetchApi<{ reservationId: string; success: boolean; message: string }>(`/reservations/waitlist/${waitlistId}/confirm`, { method: 'POST' }),

  getMyWaitlist: () =>
    fetchApi<import('../types').WaitlistEntry[]>('/reservations/my/waitlist'),

  joinEventWaitlist: (eventId: string) =>
    fetchApi<{ success: boolean; message: string }>(`/reservations/event/${eventId}/waitlist`, { method: 'POST' }),

  leaveEventWaitlist: (eventId: string) =>
    fetchApi<void>(`/reservations/event/${eventId}/waitlist`, { method: 'DELETE' }),

  confirmEventWaitlistOffer: (waitlistId: string) =>
    fetchApi<{ eventId: string; success: boolean; message: string; slotsReserved: number }>(`/reservations/event-waitlist/${waitlistId}/confirm`, { method: 'POST' }),

  getMyEventWaitlist: () =>
    fetchApi<EventWaitlistEntry[]>('/reservations/my/event-waitlist'),
}

// Admin
export const adminApi = {
  // Time Slots
  createTimeSlot: (data: CreateTimeSlotRequest) =>
    fetchApi<TimeSlotAdmin>('/admin/slots', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateTimeSlot: (slotId: string, data: { startTime?: string; endTime?: string; maxParticipants?: number; title?: string }) =>
    fetchApi<TimeSlotAdmin>(`/admin/slots/${slotId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  blockTimeSlot: (slotId: string, reason?: string) =>
    fetchApi<void>(`/admin/slots/${slotId}/block${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`, {
      method: 'POST',
    }),

  unblockTimeSlot: (slotId: string) =>
    fetchApi<void>(`/admin/slots/${slotId}/unblock`, { method: 'POST' }),

  deleteTimeSlot: (slotId: string) =>
    fetchApi<void>(`/admin/slots/${slotId}`, { method: 'DELETE' }),

  getSlotParticipants: (slotId: string) =>
    fetchApi<SlotParticipants>(`/admin/slots/${slotId}/participants`),

  getUpcomingSlots: (from?: string) =>
    fetchApi<TimeSlotAdmin[]>(`/admin/slots/upcoming${from ? `?from=${from}` : ''}`),

  // Events
  createEvent: (data: CreateEventRequest) =>
    fetchApi<EventDetail>('/admin/events', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateEvent: (eventId: string, data: Partial<CreateEventRequest> & { active?: boolean; courseId?: string | null; removeCourse?: boolean }) =>
    fetchApi<EventDetail>(`/admin/events/${eventId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteEvent: (eventId: string) =>
    fetchApi<void>(`/admin/events/${eventId}`, { method: 'DELETE' }),

  getAllEvents: () =>
    fetchApi<EventDetail[]>('/admin/events'),

  getEventDetails: (eventId: string) =>
    fetchApi<EventDetail>(`/admin/events/${eventId}`),

  getEventParticipants: (eventId: string) =>
    fetchApi<EventParticipants>(`/admin/events/${eventId}/participants`),

  cancelEventParticipant: (eventId: string, userId: string) =>
    fetchApi<void>(`/admin/events/${eventId}/participants/${userId}`, { method: 'DELETE' }),

  updateEventReservationParticipants: (eventId: string, userId: string, participants: number) =>
    fetchApi<void>(`/admin/events/${eventId}/participants/${userId}`, {
      method: 'PATCH',
      body: JSON.stringify({ participants }),
    }),

  cancelReservationByAdmin: (reservationId: string) =>
    fetchApi<void>(`/admin/reservations/${reservationId}`, { method: 'DELETE' }),

  updateReservationParticipants: (reservationId: string, participants: number) =>
    fetchApi<void>(`/admin/reservations/${reservationId}/participants`, {
      method: 'PATCH',
      body: JSON.stringify({ participants }),
    }),

  // Reservations
  getUpcomingReservations: () =>
    fetchApi<ReservationAdmin[]>('/admin/reservations/upcoming'),

  getPastReservations: () =>
    fetchApi<ReservationAdmin[]>('/admin/reservations/past'),

  getReservationsByDate: (date: string) =>
    fetchApi<ReservationAdmin[]>(`/admin/reservations/date/${date}`),

  // Users
  getAllUsers: () =>
    fetchApi<User[]>('/admin/users'),

  makeAdmin: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}/make-admin`, { method: 'POST' }),

  removeAdmin: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}/remove-admin`, { method: 'POST' }),

  deleteUser: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}`, { method: 'DELETE' }),

  // Mail
  sendMail: (data: { recipientType: 'ALL' | 'NEWSLETTER' | 'SELECTED'; userIds?: string[]; subject: string; body: string }) =>
    fetchApi<{ recipientCount: number }>('/admin/mail/send', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  // Activity Logs
  getActivityLogs: (page = 0, size = 20) =>
    fetchApi<ActivityLog[]>(`/admin/activity-logs?page=${page}&size=${size}`),
}

// Instructors (public)
export const instructorApi = {
  getAll: () => fetchApi<InstructorPublic[]>('/instructors'),
  getById: (id: string) => fetchApi<InstructorPublic>(`/instructors/${id}`),
}

// Gallery (public)
export const galleryApi = {
  getAlbums: () => fetchApi<AlbumSummary[]>('/gallery/albums'),
  getAlbum: (id: string) => fetchApi<AlbumDetail>(`/gallery/albums/${id}`),
}

// Admin Instructors
export const adminInstructorApi = {
  getAll: () => fetchApi<InstructorAdmin[]>('/admin/instructors'),
  getById: (id: string) => fetchApi<InstructorAdmin>(`/admin/instructors/${id}`),
  create: (data: CreateInstructorRequest) =>
    fetchApi<InstructorAdmin>('/admin/instructors', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: string, data: UpdateInstructorRequest) =>
    fetchApi<InstructorAdmin>(`/admin/instructors/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: string) =>
    fetchApi<void>(`/admin/instructors/${id}`, { method: 'DELETE' }),
  uploadPhoto: async (id: string, file: File) => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)

    const headers: Record<string, string> = {
      'Accept-Language': i18n.language,
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_BASE}/admin/instructors/${id}/photo`, {
      method: 'POST',
      headers,
      body: formData,
    })

    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }
  },
  deletePhoto: (id: string) =>
    fetchApi<void>(`/admin/instructors/${id}/photo`, { method: 'DELETE' }),
  setBadge: (id: string, badgeUrl: string) =>
    fetchApi<InstructorAdmin>(`/admin/instructors/${id}/badge`, {
      method: 'PUT',
      body: JSON.stringify({ badgeUrl }),
    }),
  deleteBadge: (id: string) =>
    fetchApi<InstructorAdmin>(`/admin/instructors/${id}/badge`, { method: 'DELETE' }),
}

// Admin Gallery
export const adminGalleryApi = {
  // Albums
  getAllAlbums: () => fetchApi<AlbumAdmin[]>('/admin/gallery/albums'),
  getAlbum: (id: string) => fetchApi<AlbumDetailAdmin>(`/admin/gallery/albums/${id}`),
  createAlbum: (data: CreateAlbumRequest) =>
    fetchApi<AlbumAdmin>('/admin/gallery/albums', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  updateAlbum: (id: string, data: UpdateAlbumRequest) =>
    fetchApi<AlbumAdmin>(`/admin/gallery/albums/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  deleteAlbum: (id: string) =>
    fetchApi<void>(`/admin/gallery/albums/${id}`, { method: 'DELETE' }),
  deleteAllPhotos: (id: string) =>
    fetchApi<void>(`/admin/gallery/albums/${id}/photos`, { method: 'DELETE' }),
  reorderAlbums: (data: ReorderAlbumsRequest) =>
    fetchApi<void>('/admin/gallery/albums/reorder', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  // Photos
  uploadPhoto: async (albumId: string, file: File, caption?: string) => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)
    if (caption) {
      formData.append('caption', caption)
    }

    const headers: Record<string, string> = {
      'Accept-Language': i18n.language,
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_BASE}/admin/gallery/albums/${albumId}/photos`, {
      method: 'POST',
      headers,
      body: formData,
    })

    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }

    return response.json() as Promise<UploadPhotoResponse>
  },
  updatePhoto: (photoId: string, data: UpdatePhotoRequest) =>
    fetchApi<void>(`/admin/gallery/photos/${photoId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  deletePhoto: (photoId: string) =>
    fetchApi<void>(`/admin/gallery/photos/${photoId}`, { method: 'DELETE' }),
  setThumbnailPhoto: (albumId: string, data: SetThumbnailPhotoRequest) =>
    fetchApi<void>(`/admin/gallery/albums/${albumId}/thumbnail-photo`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
}

// ==================== News (publiczne) ====================
export const newsApi = {
  getAll: (page = 0, size = 12) => fetchApi<NewsPageDto>(`/news?page=${page}&size=${size}`),
  getById: (id: string) => fetchApi<NewsDetail>(`/news/${id}`),
}

// ==================== Admin News ====================
export const adminNewsApi = {
  getAll: (page = 0, size = 20) => fetchApi<AdminNewsPageDto>(`/admin/news?page=${page}&size=${size}`),
  getById: (id: string) => fetchApi<NewsDetailAdmin>(`/admin/news/${id}`),

  create: (data: CreateNewsRequest) =>
    fetchApi<NewsAdmin>('/admin/news', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateMeta: (id: string, data: UpdateNewsMetaRequest) =>
    fetchApi<NewsAdmin>(`/admin/news/${id}/meta`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  publish: (id: string) =>
    fetchApi<NewsAdmin>(`/admin/news/${id}/publish`, { method: 'POST' }),

  unpublish: (id: string) =>
    fetchApi<NewsAdmin>(`/admin/news/${id}/unpublish`, { method: 'POST' }),

  delete: (id: string) =>
    fetchApi<void>(`/admin/news/${id}`, { method: 'DELETE' }),

  uploadThumbnail: async (id: string, file: File): Promise<UploadThumbnailResponse> => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)
    const headers: Record<string, string> = { 'Accept-Language': i18n.language }
    if (token) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(`${API_BASE}/admin/news/${id}/thumbnail`, {
      method: 'POST',
      headers,
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }
    return response.json()
  },

  deleteThumbnail: (id: string) =>
    fetchApi<void>(`/admin/news/${id}/thumbnail`, { method: 'DELETE' }),

  updateThumbnailFocalPoint: (id: string, focalPointX: number | null, focalPointY: number | null) =>
    fetchApi<void>(`/admin/news/${id}/thumbnail-focal-point`, {
      method: 'PUT',
      body: JSON.stringify({ focalPointX, focalPointY }),
    }),

  addTextBlock: (newsId: string, data: AddTextBlockRequest) =>
    fetchApi<ContentBlockAdmin>(`/admin/news/${newsId}/blocks/text`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  addImageBlock: async (newsId: string, file: File, caption?: string): Promise<UploadBlockImageResponse> => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)
    if (caption) formData.append('caption', caption)
    const headers: Record<string, string> = { 'Accept-Language': i18n.language }
    if (token) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(`${API_BASE}/admin/news/${newsId}/blocks/image`, {
      method: 'POST',
      headers,
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }
    return response.json()
  },

  updateTextBlock: (blockId: string, data: UpdateTextBlockRequest) =>
    fetchApi<void>(`/admin/news/blocks/${blockId}/text`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  updateImageBlock: (blockId: string, data: UpdateImageBlockRequest) =>
    fetchApi<void>(`/admin/news/blocks/${blockId}/image`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  moveBlock: (blockId: string, direction: 'UP' | 'DOWN') =>
    fetchApi<void>(`/admin/news/blocks/${blockId}/move`, {
      method: 'POST',
      body: JSON.stringify({ direction }),
    }),

  deleteBlock: (blockId: string) =>
    fetchApi<void>(`/admin/news/blocks/${blockId}`, { method: 'DELETE' }),

  addImageBlockFromUrl: (newsId: string, imageUrl: string, caption?: string) =>
    fetchApi<ContentBlockAdmin>(`/admin/news/${newsId}/blocks/image-from-url`, {
      method: 'POST',
      body: JSON.stringify({ imageUrl, caption: caption ?? null }),
    }),

  addVideoEmbedBlock: (newsId: string, url: string) =>
    fetchApi<ContentBlockAdmin>(`/admin/news/${newsId}/blocks/video`, {
      method: 'POST',
      body: JSON.stringify({ url }),
    }),

  updateVideoEmbedBlock: (blockId: string, url: string) =>
    fetchApi<void>(`/admin/news/blocks/${blockId}/video`, {
      method: 'PUT',
      body: JSON.stringify({ url }),
    }),

  setThumbnailUrl: (newsId: string, thumbnailUrl: string) =>
    fetchApi<void>(`/admin/news/${newsId}/thumbnail-url`, {
      method: 'PUT',
      body: JSON.stringify({ thumbnailUrl }),
    }),

  sendNewsletter: (newsId: string) =>
    fetchApi<{ subscriberCount: number }>(`/admin/news/${newsId}/send-newsletter`, { method: 'POST' }),
}

// ==================== Courses (publiczne) ====================
export const coursesApi = {
  getAll: () => fetchApi<CourseSummary[]>('/courses'),
  getById: (id: string) => fetchApi<CourseDetail>(`/courses/${id}`),
}

// ==================== Admin Courses ====================
export const adminCoursesApi = {
  getAll: () => fetchApi<CourseAdmin[]>('/admin/courses'),
  getById: (id: string) => fetchApi<CourseDetailAdmin>(`/admin/courses/${id}`),

  create: (data: CreateCourseRequest) =>
    fetchApi<CourseAdmin>('/admin/courses', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateMeta: (id: string, data: UpdateCourseMetaRequest) =>
    fetchApi<CourseAdmin>(`/admin/courses/${id}/meta`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  publish: (id: string) =>
    fetchApi<CourseAdmin>(`/admin/courses/${id}/publish`, { method: 'POST' }),

  unpublish: (id: string) =>
    fetchApi<CourseAdmin>(`/admin/courses/${id}/unpublish`, { method: 'POST' }),

  delete: (id: string) =>
    fetchApi<void>(`/admin/courses/${id}`, { method: 'DELETE' }),

  reorder: (orderedIds: string[]) =>
    fetchApi<void>('/admin/courses/reorder', {
      method: 'PUT',
      body: JSON.stringify({ orderedIds }),
    }),

  uploadThumbnail: async (id: string, file: File): Promise<UploadThumbnailResponse> => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)
    const headers: Record<string, string> = { 'Accept-Language': i18n.language }
    if (token) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(`${API_BASE}/admin/courses/${id}/thumbnail`, {
      method: 'POST',
      headers,
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }
    return response.json()
  },

  deleteThumbnail: (id: string) =>
    fetchApi<void>(`/admin/courses/${id}/thumbnail`, { method: 'DELETE' }),

  updateThumbnailFocalPoint: (id: string, focalPointX: number | null, focalPointY: number | null) =>
    fetchApi<void>(`/admin/courses/${id}/thumbnail-focal-point`, {
      method: 'PUT',
      body: JSON.stringify({ focalPointX, focalPointY }),
    }),

  addTextBlock: (courseId: string, data: AddTextBlockRequest) =>
    fetchApi<ContentBlockAdmin>(`/admin/courses/${courseId}/blocks/text`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  addImageBlock: async (courseId: string, file: File, caption?: string): Promise<UploadBlockImageResponse> => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)
    if (caption) formData.append('caption', caption)
    const headers: Record<string, string> = { 'Accept-Language': i18n.language }
    if (token) headers['Authorization'] = `Bearer ${token}`
    const response = await fetch(`${API_BASE}/admin/courses/${courseId}/blocks/image`, {
      method: 'POST',
      headers,
      body: formData,
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }
    return response.json()
  },

  updateTextBlock: (blockId: string, data: UpdateTextBlockRequest) =>
    fetchApi<void>(`/admin/courses/blocks/${blockId}/text`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  updateImageBlock: (blockId: string, data: UpdateImageBlockRequest) =>
    fetchApi<void>(`/admin/courses/blocks/${blockId}/image`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  moveBlock: (blockId: string, direction: 'UP' | 'DOWN') =>
    fetchApi<void>(`/admin/courses/blocks/${blockId}/move`, {
      method: 'POST',
      body: JSON.stringify({ direction }),
    }),

  deleteBlock: (blockId: string) =>
    fetchApi<void>(`/admin/courses/blocks/${blockId}`, { method: 'DELETE' }),

  addImageBlockFromUrl: (courseId: string, imageUrl: string, caption?: string) =>
    fetchApi<ContentBlockAdmin>(`/admin/courses/${courseId}/blocks/image-from-url`, {
      method: 'POST',
      body: JSON.stringify({ imageUrl, caption: caption ?? null }),
    }),

  setThumbnailUrl: (courseId: string, thumbnailUrl: string) =>
    fetchApi<void>(`/admin/courses/${courseId}/thumbnail-url`, {
      method: 'PUT',
      body: JSON.stringify({ thumbnailUrl }),
    }),
}

export const adminAssetsApi = {
  list: () => fetchApi<AssetDto[]>('/admin/assets'),

  upload: async (file: File): Promise<AssetDto> => {
    const token = await ensureValidToken()
    const formData = new FormData()
    formData.append('file', file)

    const headers: Record<string, string> = {
      'Accept-Language': i18n.language,
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }

    const response = await fetch(`${API_BASE}/admin/assets`, {
      method: 'POST',
      headers,
      body: formData,
    })

    if (!response.ok) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.message || i18n.t('uploadFailed', { ns: 'errors' }))
    }

    return response.json()
  },

  delete: (id: string) =>
    fetchApi<void>(`/admin/assets/${id}`, { method: 'DELETE' }),
}

export const adminStorageApi = {
  audit: () => fetchApi<StorageAuditResult>('/admin/storage/audit'),
  deleteOrphaned: () => fetchApi<DeleteOrphanedResult>('/admin/storage/orphaned', { method: 'DELETE' }),
}
