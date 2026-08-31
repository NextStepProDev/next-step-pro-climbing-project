import i18n from '../i18n'
import { compressImage, validateImageFile } from '../utils/imageUtils'
import { ApiError, parseRetryAfter } from '../utils/errors'
import type {
  AssetDto,
  EventWaitlistEntry,
  MyInvitation,
  User,
  AdminUser,
  UserDetail,
  UserReservationHistory,
  UserStats,
  MonthView,
  WeekView,
  DayView,
  TimeSlotDetail,
  CourseEvent,
  EventSummary,
  MyReservations,
  ReservationResult,
  EventReservationResult,
  WaitlistEntry,
  TimeSlotAdmin,
  SlotParticipants,
  GuestParticipant,
  CreateTimeSlotRequest,
  CreateEventRequest,
  CreateTrainingRequest,
  TrainingRequest,
  CreatePersonalTraining,
  PersonalTraining,
  TrainingCalendarRange,
  TrainingCommentItem,
  TrainingCalendarNotifications,
  AthleteSummary,
  AthleteStats,
  AthleteGoal,
  AthleteGoals,
  AttachmentUpload,
  SaveGoal,
  SaveWeight,
  WeightSeries,
  WeightRange,
  Ascent,
  AscentLog,
  AscentOptions,
  AscentStats,
  AscentTerrain,
  PublicAscent,
  SaveAscent,
  SaveTrainingTemplate,
  TrainingMaterial,
  TrainingTemplate,
  AdminTrainingRequest,
  AdminTrainingRequestPage,
  AdminNotifications,
  AdminWaitlists,
  SlotWaitlistAdmin,
  EventWaitlistAdmin,
  InvitedUser,
  AdminNoteTarget,
  AdminPrivateNote,
  AdminNoteMarkers,
  SettlementTarget,
  SettlementPayer,
  SettlementSection,
  SettlementOverview,
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
  NewsTranslation,
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
  CourseTranslation,
  CourseAdmin,
  CourseDetailAdmin,
  CreateCourseRequest,
  UpdateCourseMetaRequest,
  StorageAuditResult,
  DeleteOrphanedResult,
  VideoDto,
  VideoAdmin,
  CreateVideoRequest,
  UpdateVideoRequest,
  HeroImageDto,
  BadgeImageDto,
  HomeSettingsDto,
  SlotTemplate,
  LocationActiveStateDto,
  LocationPresetDto,
  CalendarPromoSectionDto,
  CalendarPromoPresetDto,
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

let refreshPromise: Promise<RefreshOutcome> | null = null

async function ensureValidToken(): Promise<string | null> {
  const accessToken = getAccessToken()
  if (!accessToken) return null

  if (!isAccessTokenExpired()) return accessToken

  return (await refreshOnce()).token
}

/**
 * Refresh, sharing any attempt already in flight.
 *
 * Used both when the local clock says the token expired and when the server answers 401 on a
 * token we still believed valid (revoked server-side, clock skew). The 401 path used to call
 * doRefresh() directly and so skipped this dedupe: a page firing several requests at once burned
 * one refresh round-trip per request. The backend's REFRESH_ROTATION_GRACE meant nobody was
 * logged out by it, but the traffic was pure waste.
 */
async function refreshOnce(): Promise<RefreshOutcome> {
  if (refreshPromise) return refreshPromise

  refreshPromise = doRefresh()
  try {
    return await refreshPromise
  } finally {
    refreshPromise = null
  }
}

/**
 * Why a refresh failed, because the answer decides whether the user stays logged in.
 * `transient` keeps the tokens: the request fails, the session does not.
 */
type RefreshOutcome =
  | { token: string; reason?: undefined }
  | { token: null; reason: 'rejected' | 'transient' }

async function doRefresh(): Promise<RefreshOutcome> {
  const refresh = getRefreshToken()
  if (!refresh) {
    clearTokens()
    return { token: null, reason: 'rejected' }
  }

  try {
    const tokens = await refreshTokens(refresh)
    saveTokens(tokens)
    return { token: tokens.accessToken }
  } catch (error) {
    // Only a server that actively refused the refresh token means the session is over. A 429
    // (the limiter), a 5xx (a deploy) or a dead network are transient, and wiping the tokens
    // for one of those logged people out of a login that was still perfectly good — the
    // report that started this: "blocked for too many requests, then it logged me out".
    // A non-ApiError here is the network throw from authFetch — also transient, also not a
    // reason to log anybody out.
    if (!(error instanceof ApiError) || !error.isAuthRejection) {
      return { token: null, reason: 'transient' }
    }
    clearTokens()
    window.dispatchEvent(new CustomEvent('auth:session-expired'))
    return { token: null, reason: 'rejected' }
  }
}

/**
 * `responseType: 'blob'` is an internal option, not part of RequestInit — private files (training
 * materials, comment attachments) are fetched as bytes because they need the bearer token and an
 * `<img src>` sends no Authorization header.
 */
type FetchOptions = RequestInit & { responseType?: 'blob' }

async function fetchApi<T>(
  endpoint: string,
  options?: FetchOptions
): Promise<T> {
  const token = await ensureValidToken()

  const headers: Record<string, string> = {
    'Accept-Language': i18n.language,
    ...(options?.headers as Record<string, string>),
  }

  // FormData sets its own Content-Type with a boundary — do not override it.
  if (!(options?.body instanceof FormData)) {
    headers['Content-Type'] = headers['Content-Type'] ?? 'application/json'
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  let response: Response

  const doFetch = async (): Promise<Response> => {
    const ctrl = new AbortController()
    const tid = setTimeout(() => ctrl.abort(), 30000)
    try {
      const res = await fetch(`${API_BASE}${endpoint}`, {
        ...options,
        headers,
        signal: ctrl.signal,
      })
      clearTimeout(tid)
      return res
    } catch (err) {
      clearTimeout(tid)
      throw err
    }
  }

  try {
    response = await doFetch()
  } catch {
    console.warn(`[API] ${options?.method ?? 'GET'} ${endpoint} — network error, retrying in 1.5s…`)
    await new Promise(r => setTimeout(r, 1500))
    try {
      response = await doFetch()
    } catch {
      throw new Error(i18n.t('network', { ns: 'errors' }))
    }
  }

  // If 401, try one refresh and retry. Goes through ensureValidToken rather than doRefresh so
  // several requests 401-ing at once share one in-flight refresh instead of each firing their own.
  if (response.status === 401 && token) {
    const outcome = await refreshOnce()
    if (outcome.token) {
      headers['Authorization'] = `Bearer ${outcome.token}`
      try {
        response = await doFetch()
      } catch {
        throw new Error(i18n.t('network', { ns: 'errors' }))
      }
    } else if (outcome.reason === 'transient') {
      // The session is intact — say so, instead of sending someone to the login screen over
      // a rate limit or a restarting backend.
      throw new ApiError(i18n.t('refreshUnavailable', { ns: 'errors' }), response.status)
    } else {
      throw new ApiError(i18n.t('sessionExpired', { ns: 'errors' }), 401)
    }
  }

  // Gateway errors (502/503/504) almost always mean the backend is restarting — most
  // often a deploy, during which it can be unreachable for ~2 min while the JVM boots.
  // Retry a few times with backoff so a redeploy degrades to a brief "updating" blip
  // (combined with React Query's own retries) instead of a hard error screen. A genuine
  // 500 (app error) gets a single quick retry — no point waiting on a real bug.
  if (response.status >= 500 && response.status < 600) {
    const isGateway = response.status === 502 || response.status === 503 || response.status === 504
    const backoffs = isGateway ? [1500, 3000, 5000] : [1000]
    for (const delay of backoffs) {
      console.warn(`[API] ${options?.method ?? 'GET'} ${endpoint} → ${response.status}, retrying in ${delay}ms…`)
      await new Promise(r => setTimeout(r, delay))
      const retryToken = await ensureValidToken()
      if (retryToken) headers['Authorization'] = `Bearer ${retryToken}`
      try {
        response = await doFetch()
      } catch {
        continue // network error mid-retry — keep trying remaining backoffs
      }
      if (!(response.status >= 500 && response.status < 600)) break
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    // ApiError, not Error: callers that decide whether a session is over need the status.
    // The rate limiter's own message is already localised by the backend, so it wins.
    const fail = (message: string) => new ApiError(
      message,
      response.status,
      body?.code,
      parseRetryAfter(response.headers.get('Retry-After')),
    )
    const serverMessage = body?.message
    if (serverMessage) {
      throw fail(serverMessage)
    }
    if (response.status === 429) {
      throw fail(i18n.t('rateLimited', { ns: 'errors' }))
    }
    if (response.status === 500) {
      throw fail(i18n.t('server', { ns: 'errors' }))
    }
    // 502/503/504 survived the retry loop above -> backend still down (likely a deploy).
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      throw fail(i18n.t('serviceUpdating', { ns: 'errors' }))
    }
    if (response.status === 404) {
      throw fail(i18n.t('notFound', { ns: 'errors' }))
    }
    if (response.status === 403) {
      throw fail(i18n.t('forbidden', { ns: 'errors' }))
    }
    throw fail(i18n.t('generic', { status: response.status, ns: 'errors' }))
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (options?.responseType === 'blob') {
    return (await response.blob()) as T
  }

  return response.json()
}

/**
 * Fetches a private file as bytes. Takes the absolute path the API hands out (it already carries
 * the `/api` prefix that {@link fetchApi} adds itself).
 *
 * Goes through fetchApi rather than a bare fetch on purpose — the architecture gate forbids raw
 * fetch, and rightly: bypassing it would lose 401 refresh, 5xx retry across a redeploy and the
 * 30 s timeout, on requests that fire dozens at a time while scrolling a thread.
 */
export const fetchPrivateFile = (absolutePath: string) =>
  fetchApi<Blob>(absolutePath.replace(/^\/api/, ''), { responseType: 'blob' })


/**
 * Multipart upload routed through {@link fetchApi}.
 *
 * Every upload used to hand-roll its own fetch(), and so had none of what fetchApi provides:
 * no 401 refresh (an upload attempted after the access token expired just failed), no 5xx retry
 * across a redeploy, no 30 s timeout, and no shared error normalisation. fetchApi deliberately
 * leaves FormData alone so the browser sets its own multipart boundary.
 */
async function uploadApi<T>(endpoint: string, formData: FormData): Promise<T> {
  return fetchApi<T>(endpoint, { method: 'POST', body: formData })
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
  deleteAccount: (password: string | null) =>
    fetchApi<void>('/user/me', {
      method: 'DELETE',
      body: JSON.stringify({ password }),
    }),
  /** Adds or removes the climber from the public recent-ascents list (GDPR art. 21 objection). */
  updateAscentsVisibility: (publicVisible: boolean) =>
    fetchApi<void>('/user/me/ascents-visibility', {
      method: 'PUT',
      body: JSON.stringify({ publicVisible }),
    }),

  updateNotifications: (enabled: boolean) =>
    fetchApi<void>('/user/me/notifications', {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
  logoutAllDevices: () =>
    fetchApi<void>('/user/me/logout-all', { method: 'POST' }),
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
  uploadAvatar: (blob: Blob) => {
    const formData = new FormData()
    formData.append('file', blob, 'avatar.jpg')
    return fetchApi<User>('/user/me/avatar', { method: 'POST', body: formData })
  },
  deleteAvatar: () => fetchApi<User>('/user/me/avatar', { method: 'DELETE' }),
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

  getCourseEventsByTranslationGroup: (translationGroupId: string) =>
    fetchApi<CourseEvent[]>(`/calendar/course-group/${translationGroupId}/events`),
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
    fetchApi<ReservationResult>(`/reservations/${reservationId}/participants`, {
      method: 'PUT',
      body: JSON.stringify({ participants }),
    }),

  updateEventParticipants: (eventId: string, participants: number) =>
    fetchApi<EventReservationResult>(`/reservations/event/${eventId}/participants`, {
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
    fetchApi<WaitlistEntry[]>('/reservations/my/waitlist'),

  joinEventWaitlist: (eventId: string) =>
    fetchApi<{ success: boolean; message: string }>(`/reservations/event/${eventId}/waitlist`, { method: 'POST' }),

  leaveEventWaitlist: (eventId: string) =>
    fetchApi<void>(`/reservations/event/${eventId}/waitlist`, { method: 'DELETE' }),

  confirmEventWaitlistOffer: (waitlistId: string) =>
    fetchApi<{ eventId: string; success: boolean; message: string; slotsReserved: number }>(`/reservations/event-waitlist/${waitlistId}/confirm`, { method: 'POST' }),

  getMyEventWaitlist: () =>
    fetchApi<EventWaitlistEntry[]>('/reservations/my/event-waitlist'),

  getMyInvitations: () =>
    fetchApi<MyInvitation[]>('/reservations/my/invitations'),
}

// Training requests
export const trainingRequestApi = {
  create: (data: CreateTrainingRequest) =>
    fetchApi<{ id: string; message: string }>('/training-requests', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getMy: () =>
    fetchApi<TrainingRequest[]>('/training-requests/my'),

  cancel: (requestId: string) =>
    fetchApi<void>(`/training-requests/${requestId}`, { method: 'DELETE' }),
}

// Personal training calendar (athlete side; requires the coach-set athlete flag)
export const trainingCalendarApi = {
  getRange: (from: string, to: string) =>
    fetchApi<TrainingCalendarRange>(`/training-calendar?from=${from}&to=${to}`),

  createTraining: (data: CreatePersonalTraining) =>
    fetchApi<PersonalTraining>('/training-calendar/trainings', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateTraining: (trainingId: string, data: CreatePersonalTraining) =>
    fetchApi<PersonalTraining>(`/training-calendar/trainings/${trainingId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteTraining: (trainingId: string) =>
    fetchApi<void>(`/training-calendar/trainings/${trainingId}`, { method: 'DELETE' }),

  complete: (trainingId: string, data: { feedback?: string; rpe?: number }) =>
    fetchApi<PersonalTraining>(`/training-calendar/trainings/${trainingId}/complete`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  uncomplete: (trainingId: string) =>
    fetchApi<PersonalTraining>(`/training-calendar/trainings/${trainingId}/uncomplete`, {
      method: 'POST',
    }),

  getComments: (trainingId: string) =>
    fetchApi<TrainingCommentItem[]>(`/training-calendar/trainings/${trainingId}/comments`),

  addComment: (trainingId: string, body: string) =>
    fetchApi<TrainingCommentItem>(`/training-calendar/trainings/${trainingId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ body }),
    }),

  /** Multipart sibling of addComment: text optional, 1-3 files. */
  addCommentWithFiles: (trainingId: string, body: string | null, files: File[]) => {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file, file.name))
    if (body) formData.append('body', body)
    return fetchApi<TrainingCommentItem>(
      `/training-calendar/trainings/${trainingId}/comments/attachments`,
      { method: 'POST', body: formData },
    )
  },

  /**
   * Corrects the text of a message the caller wrote. One endpoint for both roles, like
   * deleteCommentFile — and author-only on the server, so the coach cannot rewrite what the athlete said.
   */
  editComment: (commentId: string, body: string) =>
    fetchApi<TrainingCommentItem>(`/training-calendar/comments/${commentId}`, {
      method: 'PUT',
      body: JSON.stringify({ body }),
    }),

  /** One endpoint for both roles — the backend decides from the token who is asking. */
  deleteCommentFile: (fileId: string) =>
    fetchApi<void>(`/training-calendar/comment-files/${fileId}`, { method: 'DELETE' }),

  /** One-time explicit consent to training-data processing; everything else here 409s without it. */
  acceptConsent: () =>
    fetchApi<void>('/training-calendar/consent', { method: 'POST' }),

  getNotifications: () =>
    fetchApi<TrainingCalendarNotifications>('/training-calendar/notifications'),

  markSeen: () =>
    fetchApi<void>('/training-calendar/notifications/seen', { method: 'POST' }),

  getStats: () =>
    fetchApi<AthleteStats>('/training-calendar/stats'),

  getGoals: () =>
    fetchApi<AthleteGoals>('/training-calendar/goals'),

  getWeights: (range?: WeightRange) =>
    fetchApi<WeightSeries>(`/training-calendar/weights${range ? `?range=${range}` : ''}`),

  /** Upsert: weighing twice in a day is a correction, not a second reading. */
  saveWeight: (data: SaveWeight) =>
    fetchApi<WeightSeries>('/training-calendar/weights', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteWeight: (measuredOn: string) =>
    fetchApi<WeightSeries>(`/training-calendar/weights/${measuredOn}`, { method: 'DELETE' }),

  uploadAttachment: (file: File) => {
    const formData = new FormData()
    formData.append('file', file, file.name)
    return fetchApi<AttachmentUpload>('/training-calendar/attachments/upload', { method: 'POST', body: formData })
  },

  rateReservation: (reservationId: string, rpe: number, note?: string) =>
    fetchApi<void>(`/training-calendar/reservations/${reservationId}/rpe`, {
      method: 'PUT',
      body: JSON.stringify({ rpe, note: note ?? null }),
    }),
}

// Climbing logbook. Its own base path, not under /training-calendar: it is open to every
// signed-in user and carries no health data, so neither the athlete flag nor the GDPR consent
// applies. The admin's read-only view of somebody else's logbook lives in `adminAscentApi`.
export const ascentApi = {
  /** `year` takes a four-digit year or 'all'; omitting it selects the newest year with data. */
  getLog: (terrain: AscentTerrain, year?: string) =>
    fetchApi<AscentLog>(`/ascents?terrain=${terrain}${year ? `&year=${year}` : ''}`),

  getStats: (terrain: AscentTerrain, year?: string) =>
    fetchApi<AscentStats>(`/ascents/stats?terrain=${terrain}${year ? `&year=${year}` : ''}`),

  create: (data: SaveAscent) =>
    fetchApi<Ascent>('/ascents', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (ascentId: string, data: SaveAscent) =>
    fetchApi<Ascent>(`/ascents/${ascentId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  remove: (ascentId: string) =>
    fetchApi<void>(`/ascents/${ascentId}`, { method: 'DELETE' }),

  /** One endpoint for both roles: a twin admin route would be a second copy of the same list. */
  getOptions: () =>
    fetchApi<AscentOptions>('/ascents/options'),

  /**
   * Public — no login needed. Fifteen newest ascents across everyone who has not opted out,
   * ordered by the date CLIMBED rather than by when the entry was typed in.
   */
  getRecentPublic: () =>
    fetchApi<PublicAscent[]>('/ascents/recent'),
}

/**
 * The admin's read-only window into one user's logbook. Addressed by user rather than by athlete,
 * and separate from `adminTrainingCalendarApi` for the same reason the backend keeps it out of
 * /training-calendar: the logbook is not part of the coaching relationship. The server refuses
 * anyone who switched their ascents off — `UserDetail.ascentsReadable` says so before we ask.
 */
export const adminAscentApi = {
  getLog: (userId: string, terrain: AscentTerrain, year?: string) =>
    fetchApi<AscentLog>(
      `/admin/ascents/users/${userId}?terrain=${terrain}${year ? `&year=${year}` : ''}`,
    ),

  getStats: (userId: string, terrain: AscentTerrain, year?: string) =>
    fetchApi<AscentStats>(
      `/admin/ascents/users/${userId}/stats?terrain=${terrain}${year ? `&year=${year}` : ''}`,
    ),

  /**
   * Takes one entry off the public list, or puts it back. Addressed by entry, not by author: this
   * removes a row from the noticeboard, not a person from the list. The only write the admin has
   * here — the entry itself stays the author's.
   */
  setPublicVisibility: (ascentId: string, hidden: boolean) =>
    fetchApi<Ascent>(`/admin/ascents/entries/${ascentId}/public-visibility`, {
      method: 'PUT',
      body: JSON.stringify({ hidden }),
    }),
}

// Personal training calendar (coach side)
export const adminTrainingCalendarApi = {
  getAthletes: () =>
    fetchApi<AthleteSummary[]>('/admin/training-calendar/athletes'),

  getRange: (athleteId: string, from: string, to: string) =>
    fetchApi<TrainingCalendarRange>(`/admin/training-calendar/athletes/${athleteId}?from=${from}&to=${to}`),

  createTraining: (athleteId: string, data: CreatePersonalTraining) =>
    fetchApi<PersonalTraining>(`/admin/training-calendar/athletes/${athleteId}/trainings`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateTraining: (trainingId: string, data: CreatePersonalTraining) =>
    fetchApi<PersonalTraining>(`/admin/training-calendar/trainings/${trainingId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteTraining: (trainingId: string) =>
    fetchApi<void>(`/admin/training-calendar/trainings/${trainingId}`, { method: 'DELETE' }),

  getComments: (trainingId: string) =>
    fetchApi<TrainingCommentItem[]>(`/admin/training-calendar/trainings/${trainingId}/comments`),

  addComment: (trainingId: string, body: string) =>
    fetchApi<TrainingCommentItem>(`/admin/training-calendar/trainings/${trainingId}/comments`, {
      method: 'POST',
      body: JSON.stringify({ body }),
    }),

  addCommentWithFiles: (trainingId: string, body: string | null, files: File[]) => {
    const formData = new FormData()
    files.forEach(file => formData.append('files', file, file.name))
    if (body) formData.append('body', body)
    return fetchApi<TrainingCommentItem>(
      `/admin/training-calendar/trainings/${trainingId}/comments/attachments`,
      { method: 'POST', body: formData },
    )
  },

  markSeen: (athleteId: string) =>
    fetchApi<void>(`/admin/training-calendar/athletes/${athleteId}/seen`, { method: 'POST' }),

  getStats: (athleteId: string) =>
    fetchApi<AthleteStats>(`/admin/training-calendar/athletes/${athleteId}/stats`),

  getGoals: (athleteId: string) =>
    fetchApi<AthleteGoals>(`/admin/training-calendar/athletes/${athleteId}/goals`),

  /** Read-only on purpose: only the athlete records their own weight. */
  getWeights: (athleteId: string, range?: WeightRange) =>
    fetchApi<WeightSeries>(
      `/admin/training-calendar/athletes/${athleteId}/weights${range ? `?range=${range}` : ''}`,
    ),


  createGoal: (athleteId: string, data: SaveGoal) =>
    fetchApi<AthleteGoal>(`/admin/training-calendar/athletes/${athleteId}/goals`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateGoal: (goalId: string, data: SaveGoal) =>
    fetchApi<AthleteGoal>(`/admin/training-calendar/goals/${goalId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteGoal: (goalId: string) =>
    fetchApi<void>(`/admin/training-calendar/goals/${goalId}`, { method: 'DELETE' }),

  achieveGoal: (goalId: string, achievedDate?: string) =>
    fetchApi<AthleteGoal>(`/admin/training-calendar/goals/${goalId}/achieve`, {
      method: 'POST',
      // Backdatable: null/omitted = today; the backend rejects future dates
      body: JSON.stringify({ achievedDate: achievedDate ?? null }),
    }),

  /** Undo for a weight goal closed by a mistyped weigh-in; manual closures are refused (409). */
  reopenGoal: (goalId: string) =>
    fetchApi<AthleteGoal>(`/admin/training-calendar/goals/${goalId}/reopen`, { method: 'POST' }),

  uploadAttachment: (file: File) => {
    const formData = new FormData()
    formData.append('file', file, file.name)
    return fetchApi<AttachmentUpload>('/admin/training-calendar/attachments/upload', { method: 'POST', body: formData })
  },

  getTemplates: () =>
    fetchApi<TrainingTemplate[]>('/admin/training-calendar/templates'),

  createTemplate: (data: SaveTrainingTemplate) =>
    fetchApi<TrainingTemplate>('/admin/training-calendar/templates', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateTemplate: (templateId: string, data: SaveTrainingTemplate) =>
    fetchApi<TrainingTemplate>(`/admin/training-calendar/templates/${templateId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteTemplate: (templateId: string) =>
    fetchApi<void>(`/admin/training-calendar/templates/${templateId}`, { method: 'DELETE' }),

  getMaterials: () =>
    fetchApi<TrainingMaterial[]>('/admin/training-calendar/materials'),

  deleteMaterial: (attachmentId: string) =>
    fetchApi<void>(`/admin/training-calendar/materials/${attachmentId}`, { method: 'DELETE' }),
}

// Admin
export const adminApi = {
  // Time Slots
  createTimeSlot: (data: CreateTimeSlotRequest) =>
    fetchApi<TimeSlotAdmin>('/admin/slots', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  // notifiedCount = how many participants the edit actually mailed (people who switched email
  // notifications off are not counted, because they were not written to either).
  // hadParticipants tells the two silences apart: nobody booked vs booked and not written to.
  updateTimeSlot: (slotId: string, data: { date?: string; startTime?: string; endTime?: string; maxParticipants?: number; title?: string; isAvailabilityWindow?: boolean; isUnavailable?: boolean; sendNotifications?: boolean; invitedUserIds?: string[] }) =>
    fetchApi<{ slot: TimeSlotAdmin; notifiedCount: number; hadParticipants: boolean }>(`/admin/slots/${slotId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  getSlotInvites: (slotId: string) =>
    fetchApi<InvitedUser[]>(`/admin/slots/${slotId}/invites`),

  notifySlotInvites: (slotId: string, onlyUnnotified = true) =>
    fetchApi<{ notifiedCount: number }>(`/admin/slots/${slotId}/invites/notify?onlyUnnotified=${onlyUnnotified}`, {
      method: 'POST',
    }),

  notifySlotParticipants: (slotId: string, previousSlot?: { previousDate?: string; previousStartTime?: string; previousEndTime?: string }) =>
    fetchApi<{ notifiedCount: number }>(`/admin/slots/${slotId}/notify-participants`, {
      method: 'POST',
      body: previousSlot ? JSON.stringify(previousSlot) : undefined,
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

  getSlotWaitlist: (slotId: string) =>
    fetchApi<SlotWaitlistAdmin>(`/admin/slots/${slotId}/waitlist`),

  getEventWaitlist: (eventId: string) =>
    fetchApi<EventWaitlistAdmin>(`/admin/events/${eventId}/waitlist`),

  // All active waitlists (Reservations tab)
  getAdminWaitlists: () =>
    fetchApi<AdminWaitlists>('/admin/waitlists'),

  getUpcomingSlots: (from?: string) =>
    fetchApi<TimeSlotAdmin[]>(`/admin/slots/upcoming${from ? `?from=${from}` : ''}`),

  getPastSlots: () =>
    fetchApi<TimeSlotAdmin[]>('/admin/slots/past'),

  // Private notes — the CALLING admin's own, never anybody else's. One target type in the path
  // keeps slot/event/training on a single code path here and on the server.
  getPrivateNote: (target: AdminNoteTarget, targetId: string) =>
    fetchApi<AdminPrivateNote>(`/admin/notes/${target}/${targetId}`),

  savePrivateNote: (target: AdminNoteTarget, targetId: string, body: string) =>
    fetchApi<void>(`/admin/notes/${target}/${targetId}`, {
      method: 'PUT',
      body: JSON.stringify({ body }),
    }),

  deletePrivateNote: (target: AdminNoteTarget, targetId: string) =>
    fetchApi<void>(`/admin/notes/${target}/${targetId}`, { method: 'DELETE' }),

  // Ids only, for drawing markers over a visible range. Never the text.
  getPrivateNoteMarkers: (from: string, to: string) =>
    fetchApi<AdminNoteMarkers>(`/admin/notes/markers?from=${from}&to=${to}`),

  // Events
  createEvent: (data: CreateEventRequest) =>
    fetchApi<EventDetail>('/admin/events', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  // notifiedCount / hadParticipants as in updateTimeSlot.
  updateEvent: (eventId: string, data: Partial<CreateEventRequest> & { active?: boolean; courseId?: string | null; removeCourse?: boolean; invitedUserIds?: string[] }) =>
    fetchApi<{ event: EventDetail; notifiedCount: number; hadParticipants: boolean }>(`/admin/events/${eventId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  getEventInvites: (eventId: string) =>
    fetchApi<InvitedUser[]>(`/admin/events/${eventId}/invites`),

  notifyEventInvites: (eventId: string, onlyUnnotified = true) =>
    fetchApi<{ notifiedCount: number }>(`/admin/events/${eventId}/invites/notify?onlyUnnotified=${onlyUnnotified}`, {
      method: 'POST',
    }),

  // Training requests
  getTrainingRequests: (params: { status?: 'PENDING'; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams()
    if (params.status) query.set('status', params.status)
    if (params.page != null) query.set('page', String(params.page))
    if (params.size != null) query.set('size', String(params.size))
    const qs = query.toString()
    return fetchApi<AdminTrainingRequestPage>(`/admin/training-requests${qs ? `?${qs}` : ''}`)
  },

  // Admin panel notifications (badges: Requests + new reservations)
  getNotifications: () =>
    fetchApi<AdminNotifications>('/admin/notifications'),

  markReservationsSeen: () =>
    fetchApi<void>('/admin/notifications/reservations-seen', { method: 'POST' }),

  markUsersSeen: () =>
    fetchApi<void>('/admin/notifications/users-seen', { method: 'POST' }),

  updateTrainingRequestStatus: (requestId: string, data: { status: 'PENDING' | 'CONTACTED' | 'REJECTED'; adminNote?: string; notifyUser?: boolean }) =>
    fetchApi<AdminTrainingRequest>(`/admin/training-requests/${requestId}/status`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteEvent: (eventId: string) =>
    fetchApi<void>(`/admin/events/${eventId}`, { method: 'DELETE' }),

  getAllEvents: () =>
    fetchApi<EventDetail[]>('/admin/events'),

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

  deleteReservationPermanently: (reservationId: string) =>
    fetchApi<void>(`/admin/reservations/${reservationId}/permanent`, { method: 'DELETE' }),

  deletePastEventReservations: (eventId: string) =>
    fetchApi<void>(`/admin/events/${eventId}/reservations/permanent`, { method: 'DELETE' }),

  updateReservationParticipants: (reservationId: string, participants: number) =>
    fetchApi<void>(`/admin/reservations/${reservationId}/participants`, {
      method: 'PATCH',
      body: JSON.stringify({ participants }),
    }),

  addRegisteredParticipantToSlot: (slotId: string, userId: string, participants: number, comment?: string) =>
    fetchApi<void>(`/admin/slots/${slotId}/participants/registered`, {
      method: 'POST',
      body: JSON.stringify({ userId, participants, comment: comment || null }),
    }),

  addGuestParticipantToSlot: (slotId: string, note: string, participants: number) =>
    fetchApi<GuestParticipant>(`/admin/slots/${slotId}/participants/guest`, {
      method: 'POST',
      body: JSON.stringify({ note, participants }),
    }),

  deleteGuestParticipantFromSlot: (slotId: string, guestId: string) =>
    fetchApi<void>(`/admin/slots/${slotId}/participants/guest/${guestId}`, { method: 'DELETE' }),

  addRegisteredParticipantToEvent: (eventId: string, userId: string, participants: number, comment?: string) =>
    fetchApi<void>(`/admin/events/${eventId}/participants/registered`, {
      method: 'POST',
      body: JSON.stringify({ userId, participants, comment: comment || null }),
    }),

  addGuestParticipantToEvent: (eventId: string, note: string, participants: number) =>
    fetchApi<GuestParticipant>(`/admin/events/${eventId}/participants/guest`, {
      method: 'POST',
      body: JSON.stringify({ note, participants }),
    }),

  deleteGuestParticipantFromEvent: (eventId: string, guestId: string) =>
    fetchApi<void>(`/admin/events/${eventId}/participants/guest/${guestId}`, { method: 'DELETE' }),

  // Reservations
  getUpcomingReservations: () =>
    fetchApi<ReservationAdmin[]>('/admin/reservations/upcoming'),

  getPastReservations: () =>
    fetchApi<ReservationAdmin[]>('/admin/reservations/past'),

  // Users
  getAllUsers: () =>
    fetchApi<AdminUser[]>('/admin/users'),

  makeAdmin: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}/make-admin`, { method: 'POST' }),

  removeAdmin: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}/remove-admin`, { method: 'POST' }),

  setAthlete: (userId: string, isAthlete: boolean) =>
    fetchApi<void>(`/admin/users/${userId}/set-athlete`, {
      method: 'POST',
      body: JSON.stringify({ isAthlete }),
    }),

  deleteUser: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}`, { method: 'DELETE' }),

  forceLogout: (userId: string) =>
    fetchApi<void>(`/admin/users/${userId}/logout-all`, { method: 'POST' }),

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

/**
 * The admin's read-only card for one user. Read-only is the whole shape of it: every mutation
 * this panel offers stays in `adminApi` above, next to the user list where it already lives.
 *
 * The Training tab is deliberately absent here — it reuses `adminTrainingCalendarApi` and
 * `adminAscentApi`, which already refuse users without the athlete flag.
 */
export const adminUserHistoryApi = {
  getUser: (userId: string) =>
    fetchApi<UserDetail>(`/admin/users/${userId}`),

  getActivity: (userId: string, page = 0, size = 20) =>
    fetchApi<ActivityLog[]>(`/admin/users/${userId}/activity?page=${page}&size=${size}`),

  getReservations: (userId: string, pastPage = 0, pastSize = 25) =>
    fetchApi<UserReservationHistory>(
      `/admin/users/${userId}/reservations?pastPage=${pastPage}&pastSize=${pastSize}`,
    ),
}

// Aggregate statistics about the whole user base (Users panel → Statistics).
// Its own base path, not /admin/users/stats: that would sit next to /admin/users/{userId} and only
// win by Spring's specificity rules — it works, and it looks like a bug to whoever reads it next.
export const adminUserStatsApi = {
  get: () => fetchApi<UserStats>('/admin/user-stats'),
}

// Per-participant price and payment status for a session. Admin-only, and its own endpoint on
// purpose: money about named people must never ride along in the calendar payloads, which are
// served to anonymous visitors and cached.
// Both the target kind and the payer kind are path segments, so the four combinations stay on one
// code path here and on the server.
export const adminSettlementsApi = {
  getSection: (target: SettlementTarget, targetId: string) =>
    fetchApi<SettlementSection>(`/admin/settlements/${target}/${targetId}`),

  save: (
    target: SettlementTarget,
    targetId: string,
    payerType: SettlementPayer,
    payerId: string,
    amount: number,
    settledOn: string | null,
  ) =>
    fetchApi<void>(`/admin/settlements/${target}/${targetId}/${payerType}/${payerId}`, {
      method: 'PUT',
      body: JSON.stringify({ amount, settledOn }),
    }),

  remove: (
    target: SettlementTarget,
    targetId: string,
    payerType: SettlementPayer,
    payerId: string,
  ) =>
    fetchApi<void>(`/admin/settlements/${target}/${targetId}/${payerType}/${payerId}`, {
      method: 'DELETE',
    }),

  // The Settlements tab. Omit the year for the newest one holding data — not the current one, which
  // would make an empty January look like lost history. 'all' for everything.
  getOverview: (year?: string) =>
    fetchApi<SettlementOverview>(`/admin/settlements/overview${year ? `?year=${year}` : ''}`),
}

// Instructors (public)
export const instructorApi = {
  getAll: (language?: string) => fetchApi<InstructorPublic[]>(`/instructors${language ? `?language=${language}` : ''}`),
}

// Gallery (public)
export const galleryApi = {
  getAlbums: () => fetchApi<AlbumSummary[]>('/gallery/albums'),
  getAlbum: (id: string) => fetchApi<AlbumDetail>(`/gallery/albums/${id}`),
}

// Admin Instructors
export const adminInstructorApi = {
  getAll: () => fetchApi<InstructorAdmin[]>('/admin/instructors'),
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
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    await uploadApi<void>(`/admin/instructors/${id}/photo`, formData)
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
  moveUp: (id: string) =>
    fetchApi<InstructorAdmin[]>(`/admin/instructors/${id}/move-up`, { method: 'POST' }),
  moveDown: (id: string) =>
    fetchApi<InstructorAdmin[]>(`/admin/instructors/${id}/move-down`, { method: 'POST' }),
  setPhotoUrl: (id: string, photoUrl: string | null) =>
    fetchApi<InstructorAdmin>(`/admin/instructors/${id}/photo-url`, {
      method: 'PUT',
      body: JSON.stringify({ photoUrl }),
    }),
  duplicateAsTranslation: (id: string, targetLanguage: string) =>
    fetchApi<InstructorAdmin>(`/admin/instructors/${id}/duplicate-translation`, {
      method: 'POST',
      body: JSON.stringify({ targetLanguage }),
    }),
  syncMediaToTranslations: (id: string) =>
    fetchApi<{ updatedCount: number }>(`/admin/instructors/${id}/sync-media-to-translations`, {
      method: 'POST',
    }),
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
  publishAlbum: (id: string) =>
    fetchApi<AlbumAdmin>(`/admin/gallery/albums/${id}/publish`, { method: 'POST' }),
  unpublishAlbum: (id: string) =>
    fetchApi<AlbumAdmin>(`/admin/gallery/albums/${id}/unpublish`, { method: 'POST' }),

  // Photos
  uploadPhoto: async (albumId: string, file: File, caption?: string) => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    if (caption) {
      formData.append('caption', caption)
    }
    return uploadApi<UploadPhotoResponse>(`/admin/gallery/albums/${albumId}/photos`, formData)
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

// ==================== News (public) ====================
export const newsApi = {
  getAll: (page = 0, size = 12, language?: string, q?: string, starred?: boolean) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (language) params.set('language', language)
    if (q && q.trim()) params.set('q', q.trim())
    if (starred) params.set('starred', 'true')
    return fetchApi<NewsPageDto>(`/news?${params}`)
  },
  getById: (id: string) => fetchApi<NewsDetail>(`/news/${id}`),
  getTranslations: (translationGroupId: string) =>
    fetchApi<NewsTranslation[]>(`/news/by-group/${translationGroupId}`),
  star: (id: string) => fetchApi<void>(`/news/${id}/star`, { method: 'POST' }),
  unstar: (id: string) => fetchApi<void>(`/news/${id}/star`, { method: 'DELETE' }),
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

  updatePublishedAt: (id: string, publishedAt: string) =>
    fetchApi<NewsAdmin>(`/admin/news/${id}/published-at`, {
      method: 'PUT',
      body: JSON.stringify({ publishedAt }),
    }),

  delete: (id: string) =>
    fetchApi<void>(`/admin/news/${id}`, { method: 'DELETE' }),

  uploadThumbnail: async (id: string, file: File): Promise<UploadThumbnailResponse> => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    return uploadApi<UploadThumbnailResponse>(`/admin/news/${id}/thumbnail`, formData)
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
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    if (caption) formData.append('caption', caption)
    return uploadApi<UploadBlockImageResponse>(`/admin/news/${newsId}/blocks/image`, formData)
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

  duplicateAsTranslation: (id: string, targetLanguage: string) =>
    fetchApi<NewsDetailAdmin>(`/admin/news/${id}/duplicate-translation`, {
      method: 'POST',
      body: JSON.stringify({ targetLanguage }),
    }),

  syncMediaToTranslations: (id: string) =>
    fetchApi<{ blocksAdded: number }>(`/admin/news/${id}/sync-media-to-translations`, {
      method: 'POST',
    }),
}

// ==================== Courses (public) ====================
export const coursesApi = {
  getAll: (language?: string) =>
    fetchApi<CourseSummary[]>(`/courses${language ? `?language=${language}` : ''}`),
  getById: (id: string) => fetchApi<CourseDetail>(`/courses/${id}`),
  getTranslations: (translationGroupId: string) =>
    fetchApi<CourseTranslation[]>(`/courses/by-group/${translationGroupId}`),
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
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    return uploadApi<UploadThumbnailResponse>(`/admin/courses/${id}/thumbnail`, formData)
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
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    if (caption) formData.append('caption', caption)
    return uploadApi<UploadBlockImageResponse>(`/admin/courses/${courseId}/blocks/image`, formData)
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

  duplicateAsTranslation: (id: string, targetLanguage: string) =>
    fetchApi<CourseDetailAdmin>(`/admin/courses/${id}/duplicate-translation`, {
      method: 'POST',
      body: JSON.stringify({ targetLanguage }),
    }),

  syncMediaToTranslations: (id: string) =>
    fetchApi<{ blocksAdded: number }>(`/admin/courses/${id}/sync-media-to-translations`, {
      method: 'POST',
    }),
}

export const adminAssetsApi = {
  list: () => fetchApi<AssetDto[]>('/admin/assets'),

  upload: async (file: File): Promise<AssetDto> => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    return uploadApi<AssetDto>(`/admin/assets`, formData)
  },

  delete: (id: string) =>
    fetchApi<void>(`/admin/assets/${id}`, { method: 'DELETE' }),
}

export const adminStorageApi = {
  audit: () => fetchApi<StorageAuditResult>('/admin/storage/audit'),
  deleteOrphaned: () => fetchApi<DeleteOrphanedResult>('/admin/storage/orphaned', { method: 'DELETE' }),
}

export const videoApi = {
  getAll: () => fetchApi<VideoDto[]>('/videos'),
}

export const adminVideoApi = {
  getAll: () => fetchApi<VideoAdmin[]>('/admin/videos'),
  create: (data: CreateVideoRequest) =>
    fetchApi<VideoAdmin>('/admin/videos', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  update: (id: string, data: UpdateVideoRequest) =>
    fetchApi<VideoAdmin>(`/admin/videos/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  delete: (id: string) =>
    fetchApi<void>(`/admin/videos/${id}`, { method: 'DELETE' }),
  publish: (id: string) =>
    fetchApi<VideoAdmin>(`/admin/videos/${id}/publish`, { method: 'POST' }),
  unpublish: (id: string) =>
    fetchApi<VideoAdmin>(`/admin/videos/${id}/unpublish`, { method: 'POST' }),
  moveUp: (id: string) =>
    fetchApi<VideoAdmin[]>(`/admin/videos/${id}/move-up`, { method: 'POST' }),
  moveDown: (id: string) =>
    fetchApi<VideoAdmin[]>(`/admin/videos/${id}/move-down`, { method: 'POST' }),
}

// Site Settings (public)
export const siteSettingsApi = {
  getHome: () => fetchApi<HomeSettingsDto>('/settings/home'),
  getCalendarPromo: () => fetchApi<CalendarPromoSectionDto>('/settings/calendar-promo'),
}

// Admin Site Settings
export const adminSiteApi = {
  getHero: () => fetchApi<HeroImageDto>('/admin/settings/hero'),

  uploadHeroImage: async (file: File, focalPointX?: number, focalPointY?: number): Promise<HeroImageDto> => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    if (focalPointX != null) formData.append('focalPointX', String(focalPointX))
    if (focalPointY != null) formData.append('focalPointY', String(focalPointY))
    return uploadApi<HeroImageDto>(`/admin/settings/hero`, formData)
  },

  setHeroImageUrl: (url: string, focalPointX?: number, focalPointY?: number) =>
    fetchApi<HeroImageDto>('/admin/settings/hero/url', {
      method: 'PUT',
      body: JSON.stringify({ url, focalPointX: focalPointX ?? null, focalPointY: focalPointY ?? null }),
    }),

  setFocalPoint: (x: number, y: number) =>
    fetchApi<HeroImageDto>('/admin/settings/hero/focal-point', {
      method: 'PUT',
      body: JSON.stringify({ x, y }),
    }),

  deleteHeroImage: () =>
    fetchApi<void>('/admin/settings/hero', { method: 'DELETE' }),

  // --- Hero MOBILE (separate vertical image for phones) ---
  getHeroMobile: () => fetchApi<HeroImageDto>('/admin/settings/hero-mobile'),

  uploadHeroMobileImage: async (file: File, focalPointX?: number, focalPointY?: number): Promise<HeroImageDto> => {
    const error = validateImageFile(file)
    if (error) throw new Error(error)
    const compressed = await compressImage(file)
    const formData = new FormData()
    formData.append('file', compressed)
    if (focalPointX != null) formData.append('focalPointX', String(focalPointX))
    if (focalPointY != null) formData.append('focalPointY', String(focalPointY))
    return uploadApi<HeroImageDto>(`/admin/settings/hero-mobile`, formData)
  },

  setHeroMobileImageUrl: (url: string, focalPointX?: number, focalPointY?: number) =>
    fetchApi<HeroImageDto>('/admin/settings/hero-mobile/url', {
      method: 'PUT',
      body: JSON.stringify({ url, focalPointX: focalPointX ?? null, focalPointY: focalPointY ?? null }),
    }),

  setHeroMobileFocalPoint: (x: number, y: number) =>
    fetchApi<HeroImageDto>('/admin/settings/hero-mobile/focal-point', {
      method: 'PUT',
      body: JSON.stringify({ x, y }),
    }),

  deleteHeroMobileImage: () =>
    fetchApi<void>('/admin/settings/hero-mobile', { method: 'DELETE' }),

  getBadge: () => fetchApi<BadgeImageDto>('/admin/settings/badge'),

  setBadgeUrl: (url: string, linkUrl?: string) =>
    fetchApi<BadgeImageDto>('/admin/settings/badge/url', {
      method: 'PUT',
      body: JSON.stringify({ url, linkUrl: linkUrl || null }),
    }),

  deleteBadge: () =>
    fetchApi<void>('/admin/settings/badge', { method: 'DELETE' }),

  getBadgeLeft: () => fetchApi<BadgeImageDto>('/admin/settings/badge-left'),

  setBadgeLeftUrl: (url: string, linkUrl?: string) =>
    fetchApi<BadgeImageDto>('/admin/settings/badge-left/url', {
      method: 'PUT',
      body: JSON.stringify({ url, linkUrl: linkUrl || null }),
    }),

  deleteBadgeLeft: () =>
    fetchApi<void>('/admin/settings/badge-left', { method: 'DELETE' }),

  getSlotTemplates: () =>
    fetchApi<SlotTemplate[]>('/admin/settings/slot-templates'),

  saveSlotTemplates: (templates: SlotTemplate[]) =>
    fetchApi<SlotTemplate[]>('/admin/settings/slot-templates', {
      method: 'PUT',
      body: JSON.stringify(templates),
    }),

  getActiveState: () =>
    fetchApi<LocationActiveStateDto>('/admin/settings/home-location'),

  setActivePreset: (presetId: string | null) =>
    fetchApi<LocationActiveStateDto>('/admin/settings/home-location', {
      method: 'PUT',
      body: JSON.stringify({ activePresetId: presetId }),
    }),

  getLocationPresets: () =>
    fetchApi<LocationPresetDto[]>('/admin/settings/home-location/presets'),

  saveLocationPreset: (preset: LocationPresetDto) =>
    fetchApi<LocationPresetDto>('/admin/settings/home-location/presets', {
      method: 'POST',
      body: JSON.stringify(preset),
    }),

  deleteLocationPreset: (id: string) =>
    fetchApi<void>(`/admin/settings/home-location/presets/${id}`, {
      method: 'DELETE',
    }),

  getCalendarPromoActiveState: () =>
    fetchApi<LocationActiveStateDto>('/admin/settings/calendar-promo'),

  setCalendarPromoActivePreset: (presetId: string | null) =>
    fetchApi<LocationActiveStateDto>('/admin/settings/calendar-promo', {
      method: 'PUT',
      body: JSON.stringify({ activePresetId: presetId }),
    }),

  getCalendarPromoPresets: () =>
    fetchApi<CalendarPromoPresetDto[]>('/admin/settings/calendar-promo/presets'),

  saveCalendarPromoPreset: (preset: CalendarPromoPresetDto) =>
    fetchApi<CalendarPromoPresetDto>('/admin/settings/calendar-promo/presets', {
      method: 'POST',
      body: JSON.stringify(preset),
    }),

  deleteCalendarPromoPreset: (id: string) =>
    fetchApi<void>(`/admin/settings/calendar-promo/presets/${id}`, {
      method: 'DELETE',
    }),
}
