import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { AlertTriangle, ArrowRight, CalendarClock, Check, Mail, MessageSquare, Phone, Users, X } from 'lucide-react'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { describeTerm } from '../../utils/rescheduleTerm'
import { decodeHtmlEntities } from '../../utils/htmlEntities'
import { Button } from '../ui/Button'
import { Modal } from '../ui/Modal'
import { ModalCancelButton } from '../ui/ModalCancelButton'
import { QueryError } from '../ui/QueryError'
import { DayAgendaPreview } from '../calendar/DayAgendaPreview'
import type { AdminRescheduleRequest, RescheduleStatus } from '../../types'

/**
 * Clients asking to move a term they are booked on. Accepting moves the WHOLE slot or event —
 * everyone on it comes along (owner's decision 2026-10-21) — so the card names those people
 * before the button can be pressed, and the confirmation names them again.
 */
export function RescheduleRequestsSection() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [accepting, setAccepting] = useState<AdminRescheduleRequest | null>(null)
  const [rejecting, setRejecting] = useState<AdminRescheduleRequest | null>(null)
  const [done, setDone] = useState<string | null>(null)
  const [showRecent, setShowRecent] = useState(false)

  const { data, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'reschedule'],
    queryFn: adminApi.getRescheduleRequests,
  })

  const afterAnswer = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'reschedule'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
  }

  const acceptMutation = useMutation({
    mutationFn: (id: string) => adminApi.acceptRescheduleRequest(id),
    onSuccess: (result) => {
      afterAnswer()
      // The term moved: everything that shows it is stale.
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'slots'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'reservations'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
      setAccepting(null)
      setDone(result.notifiedOthers > 0
        ? t('reschedule.accepted', { name: result.request.userFullName, count: result.notifiedOthers })
        : t('reschedule.acceptedAlone', { name: result.request.userFullName }))
    },
  })

  const rejectMutation = useMutation({
    mutationFn: ({ id, adminNote, notifyUser }: { id: string; adminNote: string; notifyUser: boolean }) =>
      adminApi.rejectRescheduleRequest(id, { adminNote: adminNote || undefined, notifyUser }),
    onSuccess: (request) => {
      afterAnswer()
      setRejecting(null)
      setDone(t('reschedule.rejected', { name: request.userFullName }))
    },
  })

  if (isError) return <div className="mb-8"><QueryError error={error} onRetry={() => refetch()} /></div>
  if (!data || (data.pending.length === 0 && data.recent.length === 0)) return null

  return (
    <section className="mb-8">
      <h2 className="text-lg font-semibold text-surface-100 mb-1 flex items-center gap-2">
        <CalendarClock className="w-5 h-5 text-sky-400" aria-hidden="true" />
        {t('reschedule.title')}
        {data.pending.length > 0 && (
          <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-amber-500/15 text-amber-400">
            {data.pending.length}
          </span>
        )}
      </h2>
      <p className="text-sm text-surface-400 mb-4">{t('reschedule.intro')}</p>

      {done && (
        <div className="mb-4 p-3 rounded-lg border border-green-500/30 bg-green-500/10 text-sm text-green-300 flex items-start gap-2">
          <Check className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
          <span className="flex-1">{done}</span>
          <button type="button" onClick={() => setDone(null)} aria-label={t('reschedule.dismiss')} className="text-green-300/70 hover:text-green-200">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {data.pending.length === 0 ? (
        <p className="text-sm text-surface-400 mb-2">{t('reschedule.emptyPending')}</p>
      ) : (
        <div className="space-y-4">
          {data.pending.map((req) => (
            <RescheduleCard
              key={req.id}
              request={req}
              onAccept={() => { setDone(null); acceptMutation.reset(); setAccepting(req) }}
              onReject={() => { setDone(null); rejectMutation.reset(); setRejecting(req) }}
            />
          ))}
        </div>
      )}

      {data.recent.length > 0 && (
        <div className="mt-4">
          <button
            type="button"
            onClick={() => setShowRecent((v) => !v)}
            className="text-sm text-surface-400 hover:text-surface-200 transition-colors"
          >
            {showRecent ? t('reschedule.hideRecent') : t('reschedule.showRecent', { count: data.recent.length })}
          </button>
          {showRecent && (
            <ul className="mt-2 space-y-2">
              {data.recent.map((req) => <RecentRow key={req.id} request={req} />)}
            </ul>
          )}
        </div>
      )}

      {accepting && (
        <AcceptModal
          request={accepting}
          onClose={() => setAccepting(null)}
          onConfirm={() => acceptMutation.mutate(accepting.id)}
          isPending={acceptMutation.isPending}
          error={acceptMutation.isError ? getErrorMessage(acceptMutation.error) : null}
        />
      )}
      {rejecting && (
        <RejectModal
          request={rejecting}
          onClose={() => setRejecting(null)}
          onSubmit={(adminNote, notifyUser) => rejectMutation.mutate({ id: rejecting.id, adminNote, notifyUser })}
          isPending={rejectMutation.isPending}
          error={rejectMutation.isError ? getErrorMessage(rejectMutation.error) : null}
        />
      )}
    </section>
  )
}

function useTerms(req: AdminRescheduleRequest) {
  const locale = useDateLocale()
  return {
    oldTerm: describeTerm(locale, req.oldDate, req.oldEndDate, req.oldStartTime, req.oldEndTime),
    newTerm: describeTerm(locale, req.newDate, req.newEndDate, req.newStartTime, req.newEndTime),
  }
}

/** Everybody the move takes along besides the requester; empty when they are alone on the term. */
function othersLabel(req: AdminRescheduleRequest, guestsLabel: (count: number) => string): string | null {
  const parts = [...req.otherParticipants]
  if (req.otherGuests > 0) parts.push(guestsLabel(req.otherGuests))
  return parts.length > 0 ? parts.join(', ') : null
}

function RescheduleCard({ request: req, onAccept, onReject }: {
  request: AdminRescheduleRequest
  onAccept: () => void
  onReject: () => void
}) {
  const { t } = useTranslation('admin')
  const { oldTerm, newTerm } = useTerms(req)
  const others = othersLabel(req, (count) => t('reschedule.guests', { count }))
  const hours = req.newStartTime && req.newEndTime
    ? { start: req.newStartTime.slice(0, 5), end: req.newEndTime.slice(0, 5) }
    : null

  return (
    <div className="bg-surface-900 rounded-xl border border-amber-500/30 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-surface-100">
          <span className="font-semibold">{req.userFullName}</span>{' '}
          <span className="text-surface-400">{t('reschedule.asks')}</span>
        </p>
        <span className="px-2 py-1 rounded-full text-xs font-medium bg-sky-500/15 text-sky-300">
          {req.targetType === 'EVENT' ? t('reschedule.kindEvent') : t('reschedule.kindSlot')}: {req.title}
        </span>
      </div>

      <div className="mt-3 flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-3 text-sm">
        <span className="text-surface-400 line-through">{oldTerm}</span>
        <ArrowRight className="hidden sm:block w-4 h-4 text-surface-500 shrink-0" aria-hidden="true" />
        <span className="font-semibold text-green-400">{newTerm}</span>
      </div>

      {req.comment && (
        <p className="mt-2 text-sm text-surface-300 flex items-start gap-1.5">
          <MessageSquare className="w-4 h-4 mt-0.5 shrink-0 text-surface-400" aria-hidden="true" />
          {decodeHtmlEntities(req.comment)}
        </p>
      )}

      {others ? (
        <div className="mt-3 p-3 rounded-lg border border-amber-500/40 bg-amber-500/10 text-sm text-amber-200 flex items-start gap-2">
          <Users className="w-4 h-4 mt-0.5 shrink-0 text-amber-400" aria-hidden="true" />
          <div>
            <p><span className="font-semibold">{t('reschedule.othersTitle')}</span> {others}</p>
            <p className="text-xs text-amber-200/80 mt-0.5">{t('reschedule.othersHint')}</p>
          </div>
        </div>
      ) : (
        <p className="mt-3 text-xs text-surface-400">{t('reschedule.alone')}</p>
      )}

      {req.stale && (
        <div className="mt-3 p-3 rounded-lg border border-rose-500/30 bg-rose-500/10 text-sm text-rose-300 flex items-start gap-2">
          <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
          {t('reschedule.stale')}
        </div>
      )}

      {/* The new day as it is now, so a clash is seen before the button, not after. */}
      <div className="mt-3">
        <DayAgendaPreview
          date={req.newDate}
          proposed={hours}
          excludeId={req.timeSlotId ? `slot-${req.timeSlotId}` : `event-${req.eventId}`}
          proposedLabel={t('reschedule.newTermRow')}
        />
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-surface-300">
        <a href={`mailto:${req.userEmail}`} className="flex items-center gap-1 hover:text-surface-100 transition-colors">
          <Mail className="w-3.5 h-3.5" aria-hidden="true" />
          {req.userEmail}
        </a>
        {req.userPhone && (
          <a href={`tel:${req.userPhone}`} className="flex items-center gap-1 hover:text-surface-100 transition-colors">
            <Phone className="w-3.5 h-3.5" aria-hidden="true" />
            {req.userPhone}
          </a>
        )}
        <span className="text-xs text-surface-500">
          {t('requests.submittedAt')}: {format(new Date(req.createdAt), 'dd.MM.yyyy HH:mm')}
        </span>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <Button size="sm" onClick={onAccept} disabled={req.stale}>
          <Check className="w-4 h-4 mr-1" aria-hidden="true" />
          {t('reschedule.accept')}
        </Button>
        <Button size="sm" variant="ghost" onClick={onReject} className="text-rose-400 hover:text-rose-300">
          <X className="w-4 h-4 mr-1" aria-hidden="true" />
          {t('reschedule.reject')}
        </Button>
      </div>
    </div>
  )
}

function AcceptModal({ request: req, onClose, onConfirm, isPending, error }: {
  request: AdminRescheduleRequest
  onClose: () => void
  onConfirm: () => void
  isPending: boolean
  error: string | null
}) {
  const { t } = useTranslation('admin')
  const { oldTerm, newTerm } = useTerms(req)
  const others = othersLabel(req, (count) => t('reschedule.guests', { count }))
  const people = 1 + req.otherParticipants.length + req.otherGuests

  return (
    <Modal isOpen onClose={onClose} title={t('reschedule.acceptTitle')}>
      <div className="space-y-4">
        <p className="text-sm text-surface-300">
          {t('reschedule.acceptBody', { name: req.userFullName, title: req.title })}
        </p>
        <div className="text-sm">
          <p className="text-surface-400 line-through">{oldTerm}</p>
          <p className="font-semibold text-green-400">{newTerm}</p>
        </div>
        {others && (
          <div className="p-3 rounded-lg border border-amber-500/40 bg-amber-500/10 text-sm text-amber-200">
            <p className="font-semibold">{t('reschedule.acceptOthers', { count: people - 1 })}</p>
            <p className="mt-1">{others}</p>
            <p className="text-xs text-amber-200/80 mt-1">{t('reschedule.othersHint')}</p>
          </div>
        )}
        <p className="text-xs text-surface-400">{t('reschedule.acceptMailHint')}</p>
        <div className="flex gap-3 pt-1">
          <Button onClick={onConfirm} loading={isPending} className="flex-1">
            {others ? t('reschedule.acceptMany', { count: people }) : t('reschedule.acceptOne')}
          </Button>
          <ModalCancelButton onClose={onClose}>{t('requests.cancel')}</ModalCancelButton>
        </div>
        {error && <p className="text-sm text-rose-400/80">{error}</p>}
      </div>
    </Modal>
  )
}

function RejectModal({ request: req, onClose, onSubmit, isPending, error }: {
  request: AdminRescheduleRequest
  onClose: () => void
  onSubmit: (note: string, notifyUser: boolean) => void
  isPending: boolean
  error: string | null
}) {
  const { t } = useTranslation('admin')
  const [note, setNote] = useState('')
  const [notifyUser, setNotifyUser] = useState(true)

  return (
    <Modal isOpen onClose={onClose} title={t('reschedule.rejectTitle')} confirmClose={() => note.trim() !== ''}>
      <form onSubmit={(e) => { e.preventDefault(); onSubmit(note.trim(), notifyUser) }} className="space-y-4">
        <p className="text-sm text-surface-400">{t('reschedule.rejectBody', { name: req.userFullName })}</p>
        <div>
          <label htmlFor="reschedule-reject-note" className="block text-sm text-surface-400 mb-1">
            {t('requests.rejectNoteLabel')}
          </label>
          <textarea
            id="reschedule-reject-note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder={t('requests.notePlaceholder')}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
          />
        </div>
        <label className="flex items-start gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={notifyUser}
            onChange={(e) => setNotifyUser(e.target.checked)}
            className="mt-0.5 accent-primary-500"
          />
          <div>
            <span className="text-sm font-medium text-surface-200">{t('requests.notifyUser')}</span>
            <p className="text-xs text-surface-400 mt-0.5">{t('reschedule.notifyHint')}</p>
          </div>
        </label>
        <div className="flex gap-3 pt-2">
          <Button type="submit" variant="danger" loading={isPending} className="flex-1">
            {t('reschedule.reject')}
          </Button>
          <ModalCancelButton onClose={onClose}>{t('requests.cancel')}</ModalCancelButton>
        </div>
        {error && <p className="text-sm text-rose-400/80">{error}</p>}
      </form>
    </Modal>
  )
}

function statusChip(status: RescheduleStatus): string {
  switch (status) {
    case 'PENDING': return 'bg-amber-500/15 text-amber-400'
    case 'ACCEPTED': return 'bg-green-500/15 text-green-400'
    case 'REJECTED': return 'bg-rose-500/15 text-rose-400'
    case 'EXPIRED':
    case 'WITHDRAWN': return 'bg-surface-700 text-surface-400'
  }
}

function RecentRow({ request: req }: { request: AdminRescheduleRequest }) {
  const { t } = useTranslation('admin')
  const { oldTerm, newTerm } = useTerms(req)
  return (
    <li className="p-3 rounded-lg bg-surface-900 border border-surface-800 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-surface-200">{req.userFullName} · {req.title}</span>
        <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${statusChip(req.status)}`}>
          {t(`reschedule.status.${req.status}`)}
        </span>
      </div>
      <p className="mt-1 text-surface-400">
        {oldTerm} → {newTerm}
      </p>
      {req.adminNote && <p className="mt-1 text-xs text-surface-400">{t('requests.adminNote')}: {req.adminNote}</p>}
    </li>
  )
}
