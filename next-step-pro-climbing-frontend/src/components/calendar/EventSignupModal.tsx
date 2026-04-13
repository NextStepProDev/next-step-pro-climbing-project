import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Calendar, ExternalLink, MapPin, Users } from 'lucide-react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { useAuth } from '../../context/AuthContext'
import { saveRedirectPath } from '../../utils/redirect'
import { calendarApi, reservationApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import type { EventSummary } from '../../types'

interface EventSignupModalProps {
  event: EventSummary | null
  isOpen: boolean
  onClose: () => void
}

export function EventSignupModal({ event, isOpen, onClose }: EventSignupModalProps) {
  const { t } = useTranslation('calendar')
  const { t: tc } = useTranslation('common')
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [participants, setParticipants] = useState(1)
  const [showParticipants, setShowParticipants] = useState(false)
  // null = user hasn't touched yet → falls back to freshEvent.userParticipants
  const [userEditParticipants, setUserEditParticipants] = useState<number | null>(null)

  useEffect(() => {
    setParticipants(1)
    setShowParticipants(false)
    setComment('')
  }, [event?.id])

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
      onClose()
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

  if (!ev) return null

  const enrollmentClosed = !ev.enrollmentOpen
  const spotsLeft = ev.maxParticipants - ev.currentParticipants
  const isFull = spotsLeft <= 0
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

    // Authenticated, full, not yet on waitlist — hint shown above the button
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
      const participantsChanged = editParticipants !== ev.userParticipants && ev.userParticipants > 0
      return (
        <>
          {ev.enrollmentOpen && participantsChanged && (
            <Button
              variant="primary"
              className="flex-1"
              loading={updateParticipantsMutation.isPending}
              onClick={() => updateParticipantsMutation.mutate(editParticipants)}
            >
              {t('event.saveParticipants')}
            </Button>
          )}
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
              onClick={() => confirmOfferMutation.mutate(waitlistEntryId)}
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
            onClick={() => joinWaitlistMutation.mutate(ev.id)}
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
          onClick={() => reservationMutation.mutate({
            eventId: ev.id,
            comment: comment || undefined,
            participants,
          })}
        >
          {participants > 1 ? t('event.bookMultiple', { count: participants }) : t('event.bookSingle')}
        </Button>
        <Button variant="ghost" onClick={onClose}>{t('event.close')}</Button>
      </>
    )
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('event.title')}>
      <div className="space-y-6">
        {/* Event type badge */}
        <div>
          <span className="inline-block px-2 py-1 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
            {tc(`eventTypes.${ev.eventType}`)}
          </span>
        </div>

        {/* Title + course link */}
        <div className="flex items-start justify-between gap-3">
          <h3 className="text-lg font-semibold text-dark-100">{ev.title}</h3>
          {ev.courseId && (
            <Link
              to={`/kursy#course-${ev.courseId}`}
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
          <p className="text-sm text-dark-300 whitespace-pre-wrap">{ev.description}</p>
        )}

        {/* Date */}
        <div className="flex items-center gap-2 text-dark-300">
          <Calendar className="w-5 h-5" />
          <span>
            {format(new Date(ev.startDate), 'dd.MM.yyyy')}
            {ev.isMultiDay && (
              <> - {format(new Date(ev.endDate), 'dd.MM.yyyy')}</>
            )}
          </span>
        </div>

        {/* Location */}
        {ev.location && (
          <div className="flex items-center gap-2 text-dark-300">
            <MapPin className="w-5 h-5" />
            <span>{ev.location}</span>
          </div>
        )}

        {/* Capacity */}
        <div className="flex items-center gap-2 text-dark-300">
          <Users className="w-5 h-5" />
          <span>
            {t('event.participants', { current: ev.currentParticipants, max: ev.maxParticipants })}
            {spotsLeft > 0 && (
              <span className="text-primary-400 ml-2">{t('event.spotsFree', { count: spotsLeft })}</span>
            )}
            {isFull && (
              <span className="text-amber-400 ml-2">{t('event.full')}</span>
            )}
          </span>
        </div>

        {/* User registered info */}
        {ev.isUserRegistered && (
          <div className="space-y-4">
            <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
              <span className="text-primary-400 font-medium">
                {t('event.hasReservation')}
              </span>
            </div>

            {ev.enrollmentOpen && ev.userParticipants > 0 && (
              <div>
                <label className="block text-sm text-dark-400 mb-2">{t('event.editParticipantsLabel')}</label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setUserEditParticipants(Math.max(1, editParticipants - 1))}
                    className="w-9 h-9 rounded-lg bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-lg font-bold"
                  >
                    -
                  </button>
                  <span className="text-lg font-semibold text-dark-100 w-8 text-center">{editParticipants}</span>
                  <button
                    type="button"
                    onClick={() => setUserEditParticipants(editParticipants + 1)}
                    disabled={editParticipants >= spotsLeft + ev.userParticipants}
                    className="w-9 h-9 rounded-lg bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-lg font-bold disabled:opacity-40"
                  >
                    +
                  </button>
                  <span className="text-sm text-dark-500">{t('event.spotsOf', { count: spotsLeft + ev.userParticipants })}</span>
                </div>
                {updateParticipantsMutation.isError && (
                  <p className="text-sm text-rose-400/80 mt-2">{getErrorMessage(updateParticipantsMutation.error)}</p>
                )}
              </div>
            )}
          </div>
        )}

        {/* Enrollment closed info */}
        {enrollmentClosed && !ev.isUserRegistered && (
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
                <label className="block text-sm text-dark-400 mb-1">{t('event.spotsLabel')}</label>
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={() => setParticipants(Math.max(1, participants - 1))}
                    className="w-9 h-9 rounded-lg bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-lg font-bold"
                  >
                    -
                  </button>
                  <span className="text-lg font-semibold text-dark-100 w-8 text-center">{participants}</span>
                  <button
                    type="button"
                    onClick={() => setParticipants(Math.min(spotsLeft, participants + 1))}
                    className="w-9 h-9 rounded-lg bg-dark-800 border border-dark-700 text-dark-200 hover:bg-dark-700 transition-colors text-lg font-bold"
                  >
                    +
                  </button>
                  <span className="text-sm text-dark-500">{t('event.spotsOf', { count: spotsLeft })}</span>
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
                className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary-500 placeholder:text-dark-500"
              />
              <div className="text-xs text-dark-500 text-right mt-1">{comment.length}/500</div>
            </div>
          </>
        )}

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t border-dark-800">
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
  )
}
