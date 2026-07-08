import { lazy, Suspense, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Calendar, ExternalLink, MapPin, Users, Trash2, AlertTriangle, Pencil } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { SuccessCheckmark } from '../ui/SuccessCheckmark'
import { ShareButtons } from '../ui/ShareButtons'
import { AddToCalendarButton } from '../ui/AddToCalendarButton'
import { CompleteProfileModal } from '../ui/CompleteProfileModal'
import { useAuth } from '../../context/AuthContext'
import { saveRedirectPath } from '../../utils/redirect'
import { adminApi, calendarApi, reservationApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import type { EventSummary } from '../../types'

// Pełny formularz edycji wydarzenia z panelu admina (kurs, daty, zaproszenia itd.).
// Lazy — widzi go tylko admin, więc publiczny chunk kalendarza nie ciągnie kodu panelu.
const EditEventModal = lazy(() =>
  import('../../pages/admin/AdminEventsPanel').then((m) => ({ default: m.EditEventModal }))
)

// Build a URL that re-opens this event modal when the user comes back from the
// linked course detail page. Reuses the calendar `?event=<id>` deep-link so the
// same modal pops open again (see CalendarPage deepLinkEvent effect).
function buildEventReturnTo(location: { pathname: string; search: string }, eventId: string): string {
  const params = new URLSearchParams(location.search)
  params.set('event', eventId)
  return `${location.pathname}?${params.toString()}`
}

interface EventSignupModalProps {
  event: EventSummary | null
  isOpen: boolean
  onClose: () => void
}

export function EventSignupModal({ event, isOpen, onClose }: EventSignupModalProps) {
  const { t } = useTranslation('calendar')
  const { t: tc } = useTranslation('common')
  const { t: ta } = useTranslation('admin')
  const { isAuthenticated, isAdmin, user } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [participants, setParticipants] = useState(1)
  const [showParticipants, setShowParticipants] = useState(false)
  // null = user hasn't touched yet → falls back to freshEvent.userParticipants
  const [userEditParticipants, setUserEditParticipants] = useState<number | null>(null)
  const [showCompleteProfile, setShowCompleteProfile] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [showEditEvent, setShowEditEvent] = useState(false)
  const [showSuccess, setShowSuccess] = useState(false)
  const pendingAction = useRef<(() => void) | null>(null)

  const requireProfile = (action: () => void) => {
    if (user?.firstName && user?.lastName && user?.phone) {
      action()
    } else {
      pendingAction.current = action
      setShowCompleteProfile(true)
    }
  }


  // Fetch fresh event data (with waitlist status) when modal is open
  const { data: freshEvent } = useQuery({
    queryKey: ['eventSummary', event?.id],
    queryFn: () => calendarApi.getEventSummary(event!.id),
    enabled: isOpen && !!event?.id && isAuthenticated,
    staleTime: 0,
  })

  const ev = freshEvent ?? event

  // Derive current edit value: user's own change takes priority, falls back to server data
  const editParticipants = userEditParticipants ?? (freshEvent?.userParticipants ?? ev?.userParticipants ?? 1)

  const reservationMutation = useMutation({
    mutationFn: (data: { eventId: string; comment?: string; participants: number }) =>
      reservationApi.createForEvent(data.eventId, data.comment, data.participants),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      setComment('')
      setParticipants(1)
      setShowSuccess(true)
    },
  })

  const cancelMutation = useMutation({
    mutationFn: (eventId: string) => reservationApi.cancelForEvent(eventId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      onClose()
    },
  })

  const updateParticipantsMutation = useMutation({
    mutationFn: (participants: number) => reservationApi.updateEventParticipants(ev!.id, participants),
    onSuccess: () => {
      setUserEditParticipants(null)
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
    },
  })

  const joinWaitlistMutation = useMutation({
    mutationFn: (eventId: string) => reservationApi.joinEventWaitlist(eventId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const leaveWaitlistMutation = useMutation({
    mutationFn: (eventId: string) => reservationApi.leaveEventWaitlist(eventId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const confirmOfferMutation = useMutation({
    mutationFn: (waitlistId: string) => reservationApi.confirmEventWaitlistOffer(waitlistId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      onClose()
    },
  })

  const { data: deleteConfirmParticipants } = useQuery({
    queryKey: ['admin', 'events', event?.id, 'participants'],
    queryFn: () => adminApi.getEventParticipants(event!.id),
    enabled: isAdmin && showDeleteConfirm && !!event,
  })

  const deleteEventMutation = useMutation({
    mutationFn: adminApi.deleteEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      setShowDeleteConfirm(false)
      onClose()
    },
  })

  if (!ev) return null

  const enrollmentClosed = !ev.enrollmentOpen
  // Jak w SlotDetailModal: edycja tylko dla przyszłych/trwających terminów (ISO daty porównują się jako stringi)
  const isPastEvent = ev.endDate < format(new Date(), 'yyyy-MM-dd')
  // Miejsca trzymane "na zaproszenie" — niedostępne dla niezaproszonych; własne zaproszenie nie blokuje.
  const reservedSeats = ev.reservedSeats ?? 0
  const reservedForOthers = Math.max(0, reservedSeats - (ev.isReservedForUser ? 1 : 0))
  const spotsLeft = ev.maxParticipants - ev.currentParticipants - reservedForOthers
  const isFull = spotsLeft <= 0
  const blockedByReserved = isFull && reservedForOthers > 0 && !ev.isReservedForUser && !ev.isUserRegistered
  // Pełny dla tego widza (`isFull`) = można dołączyć do kolejki, także gdy blokują wyłącznie miejsca
  // trzymane na zaproszenie. Zwolnią się przy anulowaniu potwierdzonej rezerwacji LUB zdjęciu
  // zaproszenia przez admina (oba wołają notifyAll), więc kolejka nie jest ślepym zaułkiem. Backend
  // liczy cudze trzymane miejsca jako zajęte przy zapisie do kolejki — front i API są spójne.
  const waitlistStatus = ev.userWaitlistStatus
  const waitlistEntryId = ev.waitlistEntryId
  const confirmationDeadline = ev.confirmationDeadline

  const handleLoginRedirect = () => {
    saveRedirectPath('/calendar')
    navigate('/login')
  }

  const renderWaitlistSection = () => {
    if (!isFull || ev.isUserRegistered || enrollmentClosed) return null

    if (!isAuthenticated) {
      // Przy blockedByReserved fioletowy box wyżej już tłumaczy sytuację — nie dublujemy.
      if (blockedByReserved) return null
      return (
        <div className="p-4 bg-amber-500/10 border border-amber-500/20 rounded-lg">
          <p className="text-sm font-semibold text-amber-400 mb-1">
            {t('event.waitlist.fullTitle')}
          </p>
          <p className="text-sm text-amber-300/80">
            {t('event.waitlist.fullLoginHint')}
          </p>
        </div>
      )
    }

    if (waitlistStatus === 'PENDING_CONFIRMATION') {
      const deadline = confirmationDeadline
        ? format(new Date(confirmationDeadline), 'dd.MM.yyyy HH:mm')
        : ''
      return (
        <div className="space-y-3">
          {confirmOfferMutation.isError && (
            <div className="p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
              <p className="text-sm text-rose-400">{getErrorMessage(confirmOfferMutation.error)}</p>
            </div>
          )}
          <div className="p-4 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <p className="text-sm font-semibold text-amber-400 mb-1">
              {t('event.waitlist.offerTitle')}
            </p>
            <p className="text-sm text-amber-300/80">
              {t('event.waitlist.offerBody')}
            </p>
            {deadline && (
              <p className="text-xs text-amber-300/60 mt-2">
                {t('event.waitlist.deadline', { deadline })}
              </p>
            )}
          </div>
        </div>
      )
    }

    if (waitlistStatus === 'WAITING') {
      return (
        <div className="p-4 bg-primary-500/10 border border-primary-500/20 rounded-lg">
          <p className="text-sm font-semibold text-primary-400 mb-1">
            {t('event.waitlist.waitingTitle')}
          </p>
          <p className="text-sm text-primary-300/80">
            {t('event.waitlist.waitingBody')}
          </p>
        </div>
      )
    }

    // Authenticated, full, not yet on waitlist — hint shown above the button.
    // Przy blockedByReserved fioletowy box wyżej już tłumaczy pełność — nie dublujemy komunikatu.
    if (blockedByReserved) return null
    return (
      <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
        <p className="text-sm text-amber-400">
          {t('event.waitlist.fullHint')}
        </p>
      </div>
    )
  }

  const renderActions = () => {
    if (enrollmentClosed && !ev.isUserRegistered) {
      return (
        <>
          <Button variant="ghost" className="flex-1" onClick={onClose}>
            {t('event.close')}
          </Button>
        </>
      )
    }

    if (!isAuthenticated) {
      return (
        <>
          <Button
            variant={isFull ? 'secondary' : 'primary'}
            className="flex-1"
            onClick={handleLoginRedirect}
          >
            {isFull ? t('event.waitlist.loginToJoin') : t('event.loginToBook')}
          </Button>
          <Button variant="ghost" onClick={onClose}>{t('event.close')}</Button>
        </>
      )
    }

    if (ev.isUserRegistered) {
      const participantsChanged = userEditParticipants !== null && editParticipants !== ev.userParticipants
      if (ev.enrollmentOpen && participantsChanged) {
        if (editParticipants === 0) {
          return (
            <>
              <Button
                variant="danger"
                className="flex-1"
                loading={cancelMutation.isPending}
                onClick={() => cancelMutation.mutate(ev.id)}
              >
                {t('event.confirmCancelParticipants')}
              </Button>
              <Button variant="ghost" onClick={() => setUserEditParticipants(null)}>
                {t('event.discardChanges')}
              </Button>
            </>
          )
        }
        return (
          <>
            <Button
              variant="primary"
              className="flex-1"
              loading={updateParticipantsMutation.isPending}
              onClick={() => updateParticipantsMutation.mutate(editParticipants)}
            >
              {t('event.saveParticipants')}
            </Button>
            <Button variant="ghost" onClick={() => setUserEditParticipants(null)}>
              {t('event.discardChanges')}
            </Button>
          </>
        )
      }
      return (
        <>
          <Button
            variant="danger"
            loading={cancelMutation.isPending}
            onClick={() => cancelMutation.mutate(ev.id)}
          >
            {t('event.cancelSignup')}
          </Button>
          <Button variant="ghost" onClick={onClose}>{t('event.close')}</Button>
        </>
      )
    }

    if (isFull) {
      if (waitlistStatus === 'PENDING_CONFIRMATION' && waitlistEntryId) {
        return (
          <>
            <Button
              variant="primary"
              className="flex-1"
              loading={confirmOfferMutation.isPending}
              onClick={() => requireProfile(() => confirmOfferMutation.mutate(waitlistEntryId))}
            >
              {t('event.waitlist.confirmOffer')}
            </Button>
            <Button
              variant="ghost"
              loading={leaveWaitlistMutation.isPending}
              onClick={() => leaveWaitlistMutation.mutate(ev.id)}
            >
              {t('event.waitlist.leave')}
            </Button>
          </>
        )
      }

      if (waitlistStatus === 'WAITING') {
        return (
          <>
            <Button
              variant="secondary"
              className="flex-1"
              loading={leaveWaitlistMutation.isPending}
              onClick={() => leaveWaitlistMutation.mutate(ev.id)}
            >
              {t('event.waitlist.leave')}
            </Button>
            <Button variant="ghost" onClick={onClose}>{t('event.close')}</Button>
          </>
        )
      }

      // Not on waitlist yet
      return (
        <>
          <Button
            variant="secondary"
            className="flex-1"
            loading={joinWaitlistMutation.isPending}
            onClick={() => requireProfile(() => joinWaitlistMutation.mutate(ev.id))}
          >
            {t('event.waitlist.join')}
          </Button>
          <Button variant="ghost" onClick={onClose}>{t('event.close')}</Button>
        </>
      )
    }

    // Spots available
    return (
      <>
        <Button
          variant="primary"
          className="flex-1"
          loading={reservationMutation.isPending}
          onClick={() => requireProfile(() => reservationMutation.mutate({
            eventId: ev.id,
            comment: comment || undefined,
            participants,
          }))}
        >
          {participants > 1 ? t('event.bookMultiple', { count: participants }) : t('event.bookSingle')}
        </Button>
        <Button variant="ghost" onClick={onClose}>{t('event.close')}</Button>
      </>
    )
  }

  return (
    <>
    {showSuccess && <SuccessCheckmark onDone={() => { setShowSuccess(false); onClose(); }} />}
    <Modal isOpen={isOpen} onClose={onClose} title={ev.eventType === 'UNAVAILABLE' ? tc('eventTypes.UNAVAILABLE') : t('event.title')}>
      <div className="space-y-6">
        {/* Event type badge */}
        <div>
          <span className="inline-block px-2 py-1 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
            {tc(`eventTypes.${ev.eventType}`)}
          </span>
        </div>

        {/* Title + course link */}
        <div className="flex items-start justify-between gap-3">
          <h3 className="text-lg font-semibold text-surface-100">{ev.title}</h3>
          {ev.courseId && (
            <Link
              to={`/kursy/${ev.courseId}`}
              state={{ returnTo: buildEventReturnTo(location, ev.id) }}
              onClick={onClose}
              className="flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors shrink-0"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              {t('event.courseDetails')}
            </Link>
          )}
        </div>

        {/* Description */}
        {ev.description && (
          <p className="text-sm text-surface-300 whitespace-pre-wrap">{ev.description}</p>
        )}

        {/* Date / time */}
        <div className="flex items-center gap-2 text-surface-300">
          <Calendar className="w-5 h-5" />
          <span>
            {(() => {
              const startD = format(new Date(ev.startDate), 'dd.MM.yyyy')
              const endD = format(new Date(ev.endDate), 'dd.MM.yyyy')
              const startT = ev.startTime ? ev.startTime.slice(0, 5) : null
              const endT = ev.endTime ? ev.endTime.slice(0, 5) : null
              // Unavailable: show the full span — first day (opt. start hour) → last day (opt. end hour)
              if (ev.eventType === 'UNAVAILABLE') {
                if (ev.isMultiDay) return `${startD}${startT ? ` ${startT}` : ''} – ${endD}${endT ? ` ${endT}` : ''}`
                if (startT && endT) return `${startD} · ${startT}–${endT}`
                return `${startD} · ${tc('allDay')}`
              }
              return ev.isMultiDay ? `${startD} - ${endD}` : startD
            })()}
          </span>
        </div>

        {/* Location */}
        {ev.location && (
          <div className="flex items-center gap-2 text-surface-300">
            <MapPin className="w-5 h-5" />
            <span>{ev.location}</span>
          </div>
        )}

        {/* Capacity — hidden for unavailable (absence) events */}
        {ev.eventType !== 'UNAVAILABLE' && (
          <div className="flex items-center gap-2 text-surface-300">
            <Users className="w-5 h-5" />
            <span>
              {t('event.participants', { current: ev.currentParticipants, max: ev.maxParticipants })}
              {spotsLeft > 0 && (
                <span className="text-green-300 ml-2">{t('event.spotsFree', { count: spotsLeft })}</span>
              )}
              {isFull && (
                <span className="text-amber-400 ml-2">{t('event.full')}</span>
              )}
              {reservedForOthers > 0 && (!isAuthenticated || ev.isReservedForUser) && (
                <span className="text-violet-300/90 ml-2">{t('event.reservedForInvited', { count: reservedForOthers })}</span>
              )}
            </span>
          </div>
        )}

        {/* Zaproszony: miejsce trzymane dla Ciebie */}
        {ev.eventType !== 'UNAVAILABLE' && ev.isReservedForUser && !ev.isUserRegistered && !enrollmentClosed && (
          <div className="p-3 bg-violet-500/10 border border-violet-500/30 rounded-lg">
            <span className="text-violet-200 font-medium">🎟 {t('event.reservedForYou')}</span>
          </div>
        )}

        {/* Niezalogowanym podpowiadamy logowanie; zalogowany nie-zaproszony dostaje zwięzły fakt + listę rezerwową. */}
        {ev.eventType !== 'UNAVAILABLE' && blockedByReserved && !enrollmentClosed && (
          <div className="p-3 bg-violet-500/10 border border-violet-500/20 rounded-lg">
            <span className="text-violet-200/90 text-sm">
              {isAuthenticated ? t('event.reservedOnlyLeft') : t('event.reservedOnlyLeftGuest')}
            </span>
          </div>
        )}

        {/* User registered info */}
        {ev.isUserRegistered && (
          <div className="space-y-4">
            <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
              <span className="text-primary-400 font-medium">
                {t('event.hasReservation')}
              </span>
            </div>
            <AddToCalendarButton
              title={ev.title}
              date={ev.startDate}
              location={ev.location}
              description={ev.description}
            />

            {ev.enrollmentOpen && ev.userParticipants > 0 && (
              <div>
                <label className="block text-sm text-surface-400 mb-2">{t('event.editParticipantsLabel')}</label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setUserEditParticipants(Math.max(0, editParticipants - 1))}
                    className="w-9 h-9 rounded-lg bg-surface-800 border border-surface-700 text-surface-200 hover:bg-surface-700 transition-colors text-lg font-bold"
                  >
                    -
                  </button>
                  <span className={`text-lg font-semibold w-8 text-center ${editParticipants === 0 ? 'text-rose-400' : 'text-surface-100'}`}>
                    {editParticipants}
                  </span>
                  <button
                    type="button"
                    onClick={() => setUserEditParticipants(editParticipants + 1)}
                    disabled={editParticipants >= spotsLeft + ev.userParticipants}
                    className="w-9 h-9 rounded-lg bg-surface-800 border border-surface-700 text-surface-200 hover:bg-surface-700 transition-colors text-lg font-bold disabled:opacity-40"
                  >
                    +
                  </button>
                  <span className="text-sm text-surface-500">{t('event.spotsOf', { count: spotsLeft + ev.userParticipants })}</span>
                </div>
                {editParticipants === 0 && (
                  <p className="text-sm text-rose-400/80 mt-2">{t('event.zeroParticipantsHint')}</p>
                )}
                {updateParticipantsMutation.isError && (
                  <p className="text-sm text-rose-400/80 mt-2">{getErrorMessage(updateParticipantsMutation.error)}</p>
                )}
              </div>
            )}
          </div>
        )}

        {/* Unavailable (absence) info */}
        {ev.eventType === 'UNAVAILABLE' && (
          <div className="p-3 bg-slate-500/10 border border-slate-500/20 rounded-lg">
            <span className="text-slate-300 text-sm">
              {t('event.unavailableInfo')}
            </span>
          </div>
        )}

        {/* Enrollment closed info */}
        {ev.eventType !== 'UNAVAILABLE' && enrollmentClosed && !ev.isUserRegistered && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <span className="text-amber-400 text-sm">
              {t('event.bookingClosed')}
            </span>
          </div>
        )}

        {/* Waitlist section */}
        {renderWaitlistSection()}

        {/* Participants & Comment for reservation */}
        {isAuthenticated && !ev.isUserRegistered && !isFull && !enrollmentClosed && (
          <>
            {spotsLeft > 1 && !showParticipants && (
              <button
                type="button"
                onClick={() => setShowParticipants(true)}
                className="text-sm text-primary-400 hover:text-primary-300 transition-colors text-left"
              >
                {t('event.addMoreParticipants')} →
              </button>
            )}
            {spotsLeft > 1 && showParticipants && (
              <div>
                <label className="block text-sm text-surface-400 mb-1">{t('event.spotsLabel')}</label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setParticipants(Math.max(1, participants - 1))}
                    className="w-9 h-9 rounded-lg bg-surface-800 border border-surface-700 text-surface-200 hover:bg-surface-700 transition-colors text-lg font-bold"
                  >
                    -
                  </button>
                  <span className="text-lg font-semibold text-surface-100 w-8 text-center">{participants}</span>
                  <button
                    type="button"
                    onClick={() => setParticipants(Math.min(spotsLeft, participants + 1))}
                    className="w-9 h-9 rounded-lg bg-surface-800 border border-surface-700 text-surface-200 hover:bg-surface-700 transition-colors text-lg font-bold"
                  >
                    +
                  </button>
                  <span className="text-sm text-surface-500">{t('event.spotsOf', { count: spotsLeft })}</span>
                </div>
              </div>
            )}
            <div>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value.slice(0, 500))}
                placeholder={t('event.commentPlaceholder')}
                maxLength={500}
                rows={2}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-surface-500"
              />
              <div className="text-xs text-surface-500 text-right mt-1">{comment.length}/500</div>
            </div>
          </>
        )}

        {/* Admin edit & delete */}
        {isAdmin && !isPastEvent && (
          <button
            type="button"
            onClick={() => setShowEditEvent(true)}
            className="flex items-center gap-2 text-sm text-primary-400 hover:text-primary-300 transition-colors"
          >
            <Pencil className="w-4 h-4" />
            {ta('events.editTitle')}
          </button>
        )}
        {isAdmin && (
          <button
            type="button"
            onClick={() => setShowDeleteConfirm(true)}
            className="flex items-center gap-2 text-sm text-rose-400/70 hover:text-rose-400 transition-colors"
          >
            <Trash2 className="w-4 h-4" />
            {ta('events.deleteTitle')}
          </button>
        )}

        {/* Share */}
        <ShareButtons
          title={ev.title}
          url={`${window.location.origin}/events/${ev.id}`}
          description={`${format(new Date(ev.startDate), 'dd.MM.yyyy')}${ev.isMultiDay ? ' - ' + format(new Date(ev.endDate), 'dd.MM.yyyy') : ''}${ev.location ? ' | ' + ev.location : ''}`}
        />

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t border-surface-800">
          {renderActions()}
        </div>

        {/* Error messages */}
        {reservationMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(reservationMutation.error)}
          </p>
        )}
        {cancelMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(cancelMutation.error)}
          </p>
        )}
        {joinWaitlistMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(joinWaitlistMutation.error)}
          </p>
        )}
      </div>
    </Modal>

    {showCompleteProfile && (
      <CompleteProfileModal
        onCompleted={() => {
          setShowCompleteProfile(false)
          pendingAction.current?.()
          pendingAction.current = null
        }}
        onClose={() => {
          setShowCompleteProfile(false)
          pendingAction.current = null
        }}
      />
    )}

    {showEditEvent && (
      <Suspense fallback={null}>
        <EditEventModal event={ev} isOpen={showEditEvent} onClose={() => setShowEditEvent(false)} />
      </Suspense>
    )}

    {showDeleteConfirm && deleteConfirmParticipants && (
      <Modal
        isOpen={showDeleteConfirm}
        onClose={() => setShowDeleteConfirm(false)}
        title={deleteConfirmParticipants.participants.length > 0
          ? ta('events.warningActiveReservations')
          : ta('events.deleteTitle')}
      >
        <div className="space-y-4">
          <div className="text-sm text-surface-400">
            {ta('events.eventLabel')}<span className="text-surface-200">{ev.title}</span>
          </div>

          {deleteConfirmParticipants.participants.length > 0 ? (
            <>
              <div className="flex items-start gap-3 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
                <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
                <div className="text-sm text-rose-300">
                  <p className="font-medium mb-1">{ta('events.hasParticipants')}</p>
                  <p className="text-rose-400/80">{ta('events.deleteWarning')}</p>
                </div>
              </div>
              <div>
                <h3 className="text-sm font-medium text-surface-300 mb-2">
                  {ta('events.registered', { count: deleteConfirmParticipants.participants.length })}
                </h3>
                <ul className="space-y-2 max-h-48 overflow-y-auto">
                  {deleteConfirmParticipants.participants.map((p) => (
                    <li key={p.userId} className="bg-surface-800 rounded-lg p-3">
                      <div className="font-medium text-surface-100">{p.fullName}</div>
                      <div className="text-sm text-surface-400">{p.email}</div>
                    </li>
                  ))}
                </ul>
              </div>
            </>
          ) : (
            <p className="text-surface-400 text-sm">{ta('events.noRegistered')}</p>
          )}

          <div className="flex gap-3 pt-2">
            <Button
              variant="danger"
              className="flex-1"
              loading={deleteEventMutation.isPending}
              onClick={() => deleteEventMutation.mutate(ev.id)}
            >
              {deleteConfirmParticipants.participants.length > 0
                ? ta('events.deleteAndCancel')
                : ta('events.deleteSimple')}
            </Button>
            <Button variant="ghost" onClick={() => setShowDeleteConfirm(false)}>
              {ta('events.cancel')}
            </Button>
          </div>

          {deleteEventMutation.isError && (
            <p className="text-sm text-rose-400/80">
              {getErrorMessage(deleteEventMutation.error)}
            </p>
          )}
        </div>
      </Modal>
    )}
    </>
  )
}
