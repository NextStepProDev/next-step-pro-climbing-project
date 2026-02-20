import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Calendar, MapPin, Users } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { useAuth } from '../../context/AuthContext'
import { saveRedirectPath } from '../../utils/redirect'
import { reservationApi } from '../../api/client'
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

  const reservationMutation = useMutation({
    mutationFn: (data: { eventId: string; comment?: string; participants: number }) =>
      reservationApi.createForEvent(data.eventId, data.comment, data.participants),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
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
      onClose()
    },
  })

  if (!event) return null

  const enrollmentClosed = !event.enrollmentOpen
  const spotsLeft = event.maxParticipants - event.currentParticipants
  const isFull = spotsLeft <= 0

  const handleLoginRedirect = () => {
    saveRedirectPath('/calendar')
    navigate('/login')
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('event.title')}>
      <div className="space-y-6">
        {/* Event type badge */}
        <div>
          <span className="inline-block px-2 py-1 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
            {tc(`eventTypes.${event.eventType}`)}
          </span>
        </div>

        {/* Title */}
        <h3 className="text-lg font-semibold text-dark-100">{event.title}</h3>

        {/* Description */}
        {event.description && (
          <p className="text-sm text-dark-300 whitespace-pre-wrap">{event.description}</p>
        )}

        {/* Date */}
        <div className="flex items-center gap-2 text-dark-300">
          <Calendar className="w-5 h-5" />
          <span>
            {format(new Date(event.startDate), 'dd.MM.yyyy')}
            {event.isMultiDay && (
              <> - {format(new Date(event.endDate), 'dd.MM.yyyy')}</>
            )}
          </span>
        </div>

        {/* Location */}
        {event.location && (
          <div className="flex items-center gap-2 text-dark-300">
            <MapPin className="w-5 h-5" />
            <span>{event.location}</span>
          </div>
        )}

        {/* Capacity */}
        <div className="flex items-center gap-2 text-dark-300">
          <Users className="w-5 h-5" />
          <span>
            {t('event.participants', { current: event.currentParticipants, max: event.maxParticipants })}
            {spotsLeft > 0 && (
              <span className="text-primary-400 ml-2">{t('event.spotsFree', { count: spotsLeft })}</span>
            )}
          </span>
        </div>

        {/* User registered info */}
        {event.isUserRegistered && (
          <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
            <span className="text-primary-400 font-medium">
              {t('event.hasReservation')}
            </span>
          </div>
        )}

        {/* Enrollment closed info */}
        {enrollmentClosed && !event.isUserRegistered && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <span className="text-amber-400 text-sm">
              {t('event.bookingClosed')}
            </span>
          </div>
        )}

        {/* Participants & Comment for reservation */}
        {isAuthenticated && !event.isUserRegistered && !isFull && !enrollmentClosed && (
          <>
            {spotsLeft > 1 && (
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
          {enrollmentClosed && !event.isUserRegistered ? (
            <Button variant="ghost" className="flex-1" onClick={onClose}>
              {t('event.close')}
            </Button>
          ) : !isAuthenticated ? (
            <Button
              variant="primary"
              className="flex-1"
              onClick={handleLoginRedirect}
            >
              {t('event.loginToBook')}
            </Button>
          ) : event.isUserRegistered ? (
            <Button
              variant="danger"
              className="flex-1"
              loading={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate(event.id)}
            >
              {t('event.cancelSignup')}
            </Button>
          ) : isFull ? (
            <Button
              variant="secondary"
              className="flex-1"
              disabled
            >
              {t('event.noSpots')}
            </Button>
          ) : (
            <Button
              variant="primary"
              className="flex-1"
              loading={reservationMutation.isPending}
              onClick={() => reservationMutation.mutate({
                eventId: event.id,
                comment: comment || undefined,
                participants,
              })}
            >
              {participants > 1 ? t('event.bookMultiple', { count: participants }) : t('event.bookSingle')}
            </Button>
          )}

          {!(enrollmentClosed && !event.isUserRegistered) && (
            <Button variant="ghost" onClick={onClose}>
              {t('event.close')}
            </Button>
          )}
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
      </div>
    </Modal>
  )
}
