import i18n from '../i18n'
import type {
  User,
  MonthView,
  WeekView,
  DayView,
  TimeSlotDetail,
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

  // Events
  createEvent: (data: CreateEventRequest) =>
    fetchApi<EventDetail>('/admin/events', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateEvent: (eventId: string, data: Partial<CreateEventRequest> & { active?: boolean }) =>
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
}
