import { useState } from 'react'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { Clock, Users, Calendar } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Modal } from '../ui/Modal'
import { Button } from '../ui/Button'
import { useAuth } from '../../context/AuthContext'
import { saveRedirectPath } from '../../utils/redirect'
import { reservationApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import type { TimeSlotDetail } from '../../types'

interface SlotDetailModalProps {
  slot: TimeSlotDetail | null
  isOpen: boolean
  onClose: () => void
}

export function SlotDetailModal({ slot, isOpen, onClose }: SlotDetailModalProps) {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [participants, setParticipants] = useState(1)

  const reservationMutation = useMutation({
    mutationFn: (data: { slotId: string; comment?: string; participants: number }) =>
      reservationApi.create(data.slotId, data.comment, data.participants),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['slot'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      setComment('')
      onClose()
    },
  })

  const waitlistMutation = useMutation({
    mutationFn: (slotId: string) => reservationApi.joinWaitlist(slotId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['slot'] })
      onClose()
    },
  })

  const cancelMutation = useMutation({
    mutationFn: (reservationId: string) => reservationApi.cancel(reservationId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['slot'] })
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      onClose()
    },
  })

  const leaveWaitlistMutation = useMutation({
    mutationFn: (entryId: string) => reservationApi.leaveWaitlist(entryId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['slot'] })
      onClose()
    },
  })

  if (!slot) return null

  const dateObj = new Date(slot.date)
  const spotsLeft = slot.maxParticipants - slot.currentParticipants
  const isPast = slot.status === 'PAST'
  const isAvailable = slot.status === 'AVAILABLE' && spotsLeft > 0
  const isFull = slot.status === 'FULL' || spotsLeft === 0

  const handleLoginRedirect = () => {
    saveRedirectPath(`/calendar?date=${slot.date}`)
    navigate('/login')
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Szczegóły terminu">
      <div className="space-y-6">
        {/* Date and time */}
        <div className="flex items-center gap-4 text-dark-300">
          <div className="flex items-center gap-2">
            <Calendar className="w-5 h-5" />
            <span className="capitalize">
              {format(dateObj, 'EEEE, d MMMM', { locale: pl })}
            </span>
          </div>
          <div className="flex items-center gap-2">
            <Clock className="w-5 h-5" />
            <span>
              {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
            </span>
          </div>
        </div>

        {/* Event info */}
        {slot.eventTitle && (
          <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
            <span className="text-sm text-primary-400">{slot.eventTitle}</span>
          </div>
        )}

        {/* Capacity */}
        <div className="flex items-center gap-2 text-dark-300">
          <Users className="w-5 h-5" />
          <span>
            {slot.currentParticipants} / {slot.maxParticipants} uczestników
            {spotsLeft > 0 && (
              <span className="text-primary-400 ml-2">({spotsLeft} wolne)</span>
            )}
          </span>
        </div>

        {/* Past slot info */}
        {isPast && (
          <div className="p-3 bg-dark-800 border border-dark-700 rounded-lg">
            <span className="text-dark-400 text-sm">Ten termin już się zakończył</span>
          </div>
        )}

        {/* Waitlist info */}
        {slot.waitlistCount > 0 && (
          <div className="text-sm text-dark-400">
            {slot.waitlistCount} osób na liście rezerwowej
          </div>
        )}

        {/* User status */}
        {slot.isUserRegistered && (
          <div className="p-3 bg-primary-500/10 border border-primary-500/20 rounded-lg">
            <span className="text-primary-400 font-medium">
              Masz rezerwację na ten termin
            </span>
          </div>
        )}

        {slot.isUserOnWaitlist && (
          <div className="p-3 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <span className="text-amber-400 font-medium">
              Jesteś na liście rezerwowej (pozycja {slot.waitlistPosition})
            </span>
          </div>
        )}

        {/* Participants & Comment for reservation */}
        {isAuthenticated && isAvailable && !slot.isUserRegistered && !slot.isUserOnWaitlist && (
          <>
            {spotsLeft > 1 && (
              <div>
                <label className="block text-sm text-dark-400 mb-1">Liczba miejsc</label>
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
                  <span className="text-sm text-dark-500">z {spotsLeft} wolnych</span>
                </div>
              </div>
            )}
            <div>
              <textarea
                value={comment}
                onChange={(e) => setComment(e.target.value.slice(0, 500))}
                placeholder="Komentarz dla instruktora (opcjonalny)..."
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
          {!isAuthenticated ? (
            <Button
              variant="primary"
              className="flex-1"
              onClick={handleLoginRedirect}
            >
              Zaloguj się, aby zarezerwować
            </Button>
          ) : slot.isUserRegistered && slot.reservationId ? (
            <Button
              variant="danger"
              className="flex-1"
              loading={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate(slot.reservationId as string)}
            >
              Anuluj rezerwację
            </Button>
          ) : slot.isUserOnWaitlist && slot.waitlistEntryId ? (
            <Button
              variant="danger"
              className="flex-1"
              loading={leaveWaitlistMutation.isPending}
              onClick={() => leaveWaitlistMutation.mutate(slot.waitlistEntryId as string)}
            >
              Opuść listę rezerwową
            </Button>
          ) : isAvailable ? (
            <Button
              variant="primary"
              className="flex-1"
              loading={reservationMutation.isPending}
              onClick={() => reservationMutation.mutate({ slotId: slot.id, comment: comment || undefined, participants })}
            >
              {participants > 1 ? `Zapisz ${participants} osoby` : 'Zapisz się'}
            </Button>
          ) : isFull && !slot.isUserOnWaitlist ? (
            <Button
              variant="secondary"
              className="flex-1"
              loading={waitlistMutation.isPending}
              onClick={() => waitlistMutation.mutate(slot.id)}
            >
              Dołącz do listy rezerwowej
            </Button>
          ) : null}

          <Button variant="ghost" onClick={onClose}>
            Zamknij
          </Button>
        </div>

        {/* Error messages */}
        {reservationMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(reservationMutation.error)}
          </p>
        )}
        {waitlistMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(waitlistMutation.error)}
          </p>
        )}
        {cancelMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(cancelMutation.error)}
          </p>
        )}
        {leaveWaitlistMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(leaveWaitlistMutation.error)}
          </p>
        )}
      </div>
    </Modal>
  )
}
