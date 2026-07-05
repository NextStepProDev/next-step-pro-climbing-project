import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import {
  AlertTriangle, Calendar, CalendarPlus, ChevronLeft, ChevronRight, Clock, ExternalLink, Mail,
  MessageSquare, Phone, RotateCcw, Users, X,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import clsx from 'clsx'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { CreateSlotModal } from '../../components/calendar/CreateSlotModal'
import { CreateEventModal } from './AdminEventsPanel'
import type { AdminTrainingRequest, TrainingRequestStatus } from '../../types'

type Filter = 'PENDING' | 'ALL'

/**
 * Panel propozycji terminów od użytkowników. Admin odpowiada na propozycję:
 * tworzy slot/wydarzenie (pełny modal z prefill + proponujący zaproszony),
 * oznacza kontakt telefoniczny albo odrzuca (opcjonalny mail z decyzją).
 */
export function AdminRequestsPanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<Filter>('PENDING')
  const [page, setPage] = useState(0)
  const [createSlotFrom, setCreateSlotFrom] = useState<AdminTrainingRequest | null>(null)
  const [createEventFrom, setCreateEventFrom] = useState<AdminTrainingRequest | null>(null)
  const [statusModal, setStatusModal] = useState<{ request: AdminTrainingRequest; action: 'CONTACTED' | 'REJECTED' } | null>(null)

  // Oczekujące: praktycznie wszystkie naraz (size 100 — tyle admin i tak nie obsłuży w zaległości).
  // Wszystkie (archiwum): stronicowane po 20, żeby setki starych propozycji nie ładowały się hurtem.
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'trainingRequests', filter, page],
    queryFn: () => filter === 'PENDING'
      ? adminApi.getTrainingRequests({ status: 'PENDING', size: 100 })
      : adminApi.getTrainingRequests({ page, size: 20 }),
  })

  // Licznik oczekujących do chipa filtra — z tego samego cache co badge'e (navbar/zakładki)
  const { data: notifications } = useQuery({
    queryKey: ['admin', 'notifications'],
    queryFn: adminApi.getNotifications,
  })

  const updateStatusMutation = useMutation({
    mutationFn: ({ id, data: body }: { id: string; data: { status: 'PENDING' | 'CONTACTED' | 'REJECTED'; adminNote?: string; notifyUser?: boolean } }) =>
      adminApi.updateTrainingRequestStatus(id, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'trainingRequests'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
      setStatusModal(null)
    },
  })

  const invalidateAfterCreate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'trainingRequests'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'slots'] })
  }

  const visible = useMemo(() => data?.content ?? [], [data])

  // Nakładające się oczekujące propozycje (ta sama data, wspólny przedział godzin) —
  // ostrzeżenie, żeby admin nie utworzył dwóch slotów na ten sam czas nieświadomie.
  const conflictIds = useMemo(() => {
    const pending = visible.filter((r) => r.status === 'PENDING')
    const ids = new Set<string>()
    for (let i = 0; i < pending.length; i++) {
      for (let j = i + 1; j < pending.length; j++) {
        const a = pending[i]
        const b = pending[j]
        if (a.requestedDate === b.requestedDate && a.startTime < b.endTime && b.startTime < a.endTime) {
          ids.add(a.id)
          ids.add(b.id)
        }
      }
    }
    return ids
  }, [visible])

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (isError) {
    return <QueryError error={error} onRetry={() => refetch()} />
  }

  const pendingCount = notifications?.pendingRequests ?? (filter === 'PENDING' ? (data?.totalElements ?? 0) : 0)
  const totalPages = data?.totalPages ?? 0

  return (
    <div>
      <div className="flex flex-wrap items-center justify-between gap-3 mb-6">
        <h2 className="text-lg font-semibold text-surface-100">
          {t('requests.title')}
        </h2>
        <div className="flex gap-1 bg-surface-800 rounded-lg p-1">
          <button
            onClick={() => { setFilter('PENDING'); setPage(0) }}
            className={clsx(
              'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
              filter === 'PENDING' ? 'bg-primary-600 text-white' : 'text-surface-400 hover:text-surface-200'
            )}
          >
            {t('requests.filterPending')} ({pendingCount})
          </button>
          <button
            onClick={() => { setFilter('ALL'); setPage(0) }}
            className={clsx(
              'px-3 py-1.5 text-sm font-medium rounded-md transition-colors',
              filter === 'ALL' ? 'bg-primary-600 text-white' : 'text-surface-400 hover:text-surface-200'
            )}
          >
            {t('requests.filterAll')}{filter === 'ALL' && data ? ` (${data.totalElements})` : ''}
          </button>
        </div>
      </div>

      {visible.length === 0 ? (
        <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center">
          <CalendarPlus className="w-12 h-12 text-surface-600 mx-auto mb-4" />
          <p className="text-surface-400">
            {filter === 'PENDING' ? t('requests.emptyPending') : t('requests.emptyAll')}
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {visible.map((req) => (
            <RequestCard
              key={req.id}
              request={req}
              hasConflict={conflictIds.has(req.id)}
              onCreateSlot={() => setCreateSlotFrom(req)}
              onCreateEvent={() => setCreateEventFrom(req)}
              onContacted={() => setStatusModal({ request: req, action: 'CONTACTED' })}
              onReject={() => setStatusModal({ request: req, action: 'REJECTED' })}
              onReopen={() => updateStatusMutation.mutate({ id: req.id, data: { status: 'PENDING' } })}
            />
          ))}
        </div>
      )}

      {/* Pager (archiwum stronicowane po 20) */}
      {totalPages > 1 && (
        <div className="mt-6 flex items-center justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm font-medium text-surface-300 hover:text-surface-100 hover:bg-surface-800 disabled:opacity-40 disabled:pointer-events-none transition-colors"
          >
            <ChevronLeft className="w-4 h-4" />
            {t('requests.pagePrev')}
          </button>
          <span className="text-sm text-surface-400">
            {t('requests.pageInfo', { page: page + 1, total: totalPages })}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page >= totalPages - 1}
            className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-sm font-medium text-surface-300 hover:text-surface-100 hover:bg-surface-800 disabled:opacity-40 disabled:pointer-events-none transition-colors"
          >
            {t('requests.pageNext')}
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      )}

      {updateStatusMutation.isError && !statusModal && (
        <div className="mt-4 p-3 bg-rose-500/5 border border-rose-500/15 rounded-lg text-sm text-rose-400/80">
          {getErrorMessage(updateStatusMutation.error)}
        </div>
      )}

      {createSlotFrom && (
        <CreateSlotModal
          key={createSlotFrom.id}
          isOpen={!!createSlotFrom}
          onClose={() => setCreateSlotFrom(null)}
          defaultDate={createSlotFrom.requestedDate}
          onSuccess={invalidateAfterCreate}
          initial={{
            startTime: createSlotFrom.startTime.slice(0, 5),
            endTime: createSlotFrom.endTime.slice(0, 5),
            maxParticipants: createSlotFrom.participants,
            invited: [{
              userId: createSlotFrom.userId,
              fullName: createSlotFrom.userFullName,
              email: createSlotFrom.userEmail,
            }],
            trainingRequestId: createSlotFrom.id,
          }}
        />
      )}

      {createEventFrom && (
        <CreateEventModal
          key={createEventFrom.id}
          isOpen={!!createEventFrom}
          onClose={() => setCreateEventFrom(null)}
          initial={{
            startDate: createEventFrom.requestedDate,
            endDate: createEventFrom.requestedDate,
            startTime: createEventFrom.startTime.slice(0, 5),
            endTime: createEventFrom.endTime.slice(0, 5),
            maxParticipants: Math.max(createEventFrom.participants, 4),
            courseId: createEventFrom.courseId ?? undefined,
            courseTitle: createEventFrom.courseTitle ?? undefined,
            invited: [{
              userId: createEventFrom.userId,
              fullName: createEventFrom.userFullName,
              email: createEventFrom.userEmail,
            }],
            trainingRequestId: createEventFrom.id,
          }}
        />
      )}

      {statusModal && (
        <StatusModal
          key={`${statusModal.request.id}-${statusModal.action}`}
          request={statusModal.request}
          action={statusModal.action}
          onClose={() => setStatusModal(null)}
          onSubmit={(note, notifyUser) => updateStatusMutation.mutate({
            id: statusModal.request.id,
            data: { status: statusModal.action, adminNote: note || undefined, notifyUser },
          })}
          isPending={updateStatusMutation.isPending}
          error={updateStatusMutation.isError ? getErrorMessage(updateStatusMutation.error) : null}
        />
      )}
    </div>
  )
}

function statusChipClass(status: TrainingRequestStatus): string {
  switch (status) {
    case 'PENDING': return 'bg-amber-500/15 text-amber-400'
    case 'ACCEPTED': return 'bg-primary-500/15 text-primary-400'
    case 'CONTACTED': return 'bg-sky-500/15 text-sky-400'
    case 'REJECTED': return 'bg-rose-500/15 text-rose-400'
    case 'EXPIRED': return 'bg-surface-700 text-surface-400'
  }
}

function RequestCard({
  request: req,
  hasConflict,
  onCreateSlot,
  onCreateEvent,
  onContacted,
  onReject,
  onReopen,
}: {
  request: AdminTrainingRequest
  hasConflict: boolean
  onCreateSlot: () => void
  onCreateEvent: () => void
  onContacted: () => void
  onReject: () => void
  onReopen: () => void
}) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const isPending = req.status === 'PENDING'
  const linkDate = req.createdSlotDate ?? req.createdEventStartDate
  // Propozycja dla kursu → domyślnie wydarzenie; trening indywidualny → slot
  const preferEvent = !!req.courseId

  return (
    <div
      className={clsx(
        'bg-surface-900 rounded-xl border p-4',
        isPending ? 'border-amber-500/30' : 'border-surface-800'
      )}
    >
      {/* Nagłówek: termin + status */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-3 text-surface-100">
          <span className="flex items-center gap-2 font-semibold capitalize">
            <Calendar className="w-4 h-4 text-surface-400" />
            {format(new Date(req.requestedDate), 'EEEE, d MMMM yyyy', { locale })}
          </span>
          <span className="flex items-center gap-1 text-surface-300">
            <Clock className="w-4 h-4 text-surface-400" />
            {req.startTime.slice(0, 5)} - {req.endTime.slice(0, 5)}
          </span>
          <span className="flex items-center gap-1 text-surface-300">
            <Users className="w-4 h-4 text-surface-400" />
            {req.participants}
          </span>
        </div>
        <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusChipClass(req.status)}`}>
          {t(`requests.status.${req.status}`)}
        </span>
      </div>

      {/* Badges: okno dostępności, kurs, konflikt */}
      <div className="mt-2 flex flex-wrap gap-2">
        {req.inWindow && (
          <span className="px-2 py-0.5 rounded text-xs font-medium bg-teal-500/15 text-teal-300">
            ✓ {t('requests.inWindow')}
            {req.windowStartTime && req.windowEndTime && (
              <> ({req.windowStartTime.slice(0, 5)} - {req.windowEndTime.slice(0, 5)})</>
            )}
          </span>
        )}
        {req.courseTitle && (
          <span className="px-2 py-0.5 rounded text-xs font-medium bg-primary-500/15 text-primary-300">
            {t('requests.course')}: {req.courseTitle}
          </span>
        )}
        {hasConflict && (
          <span className="flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-amber-500/15 text-amber-300">
            <AlertTriangle className="w-3 h-3" />
            {t('requests.conflict')}
          </span>
        )}
      </div>

      {/* Dane klienta */}
      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-surface-300">
        <span className="font-medium text-surface-200">{req.userFullName}</span>
        <a href={`mailto:${req.userEmail}`} className="flex items-center gap-1 hover:text-surface-100 transition-colors">
          <Mail className="w-3.5 h-3.5" />
          {req.userEmail}
        </a>
        {req.userPhone && (
          <a href={`tel:${req.userPhone}`} className="flex items-center gap-1 hover:text-surface-100 transition-colors">
            <Phone className="w-3.5 h-3.5" />
            {req.userPhone}
          </a>
        )}
      </div>

      {req.comment && (
        <p className="mt-2 text-sm text-surface-400 flex items-start gap-1.5">
          <MessageSquare className="w-4 h-4 mt-0.5 shrink-0" />
          {req.comment}
        </p>
      )}

      {req.adminNote && (
        <div className="mt-2 p-2.5 bg-surface-800/60 rounded-lg text-sm text-surface-300">
          <span className="text-surface-500">{t('requests.adminNote')}:</span> {req.adminNote}
        </div>
      )}

      <p className="mt-2 text-xs text-surface-500">
        {t('requests.submittedAt')}: {format(new Date(req.createdAt), 'dd.MM.yyyy HH:mm')}
      </p>

      {/* Akcje */}
      {isPending ? (
        <div className="mt-3 flex flex-wrap gap-2">
          {preferEvent ? (
            <>
              <Button size="sm" onClick={onCreateEvent}>{t('requests.createEvent')}</Button>
              <Button size="sm" variant="secondary" onClick={onCreateSlot}>{t('requests.createSlot')}</Button>
            </>
          ) : (
            <>
              <Button size="sm" onClick={onCreateSlot}>{t('requests.createSlot')}</Button>
              <Button size="sm" variant="secondary" onClick={onCreateEvent}>{t('requests.createEvent')}</Button>
            </>
          )}
          <Button size="sm" variant="ghost" onClick={onContacted}>{t('requests.markContacted')}</Button>
          <Button size="sm" variant="ghost" onClick={onReject} className="text-rose-400 hover:text-rose-300">
            <X className="w-4 h-4 mr-1" />
            {t('requests.reject')}
          </Button>
        </div>
      ) : (
        <div className="mt-3 flex flex-wrap items-center gap-4">
          {req.status === 'ACCEPTED' && linkDate && (
            <Link
              to={`/calendar?date=${linkDate}${req.createdSlotId ? `&slot=${req.createdSlotId}` : ''}`}
              className="flex items-center gap-1 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors"
            >
              <ExternalLink className="w-4 h-4" />
              {t('requests.viewInCalendar')}
            </Link>
          )}
          {req.status !== 'ACCEPTED' && (
            <button
              onClick={onReopen}
              className="flex items-center gap-1 text-sm text-surface-400 hover:text-surface-200 transition-colors"
            >
              <RotateCcw className="w-4 h-4" />
              {t('requests.reopen')}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

function StatusModal({
  request,
  action,
  onClose,
  onSubmit,
  isPending,
  error,
}: {
  request: AdminTrainingRequest
  action: 'CONTACTED' | 'REJECTED'
  onClose: () => void
  onSubmit: (note: string, notifyUser: boolean) => void
  isPending: boolean
  error: string | null
}) {
  const { t } = useTranslation('admin')
  const [note, setNote] = useState('')
  const [notifyUser, setNotifyUser] = useState(action === 'REJECTED')

  return (
    <Modal
      isOpen
      onClose={onClose}
      title={action === 'CONTACTED' ? t('requests.contactedTitle') : t('requests.rejectTitle')}
    >
      <form onSubmit={(e) => { e.preventDefault(); onSubmit(note.trim(), notifyUser) }} className="space-y-4">
        <p className="text-sm text-surface-400">
          {t('requests.statusModalFor', {
            name: request.userFullName,
            date: format(new Date(request.requestedDate), 'dd.MM.yyyy'),
          })}
        </p>

        <div>
          <label className="block text-sm text-surface-400 mb-1">
            {action === 'CONTACTED' ? t('requests.noteLabel') : t('requests.rejectNoteLabel')}
          </label>
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder={t('requests.notePlaceholder')}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 resize-none"
          />
        </div>

        {action === 'REJECTED' && (
          <label className="flex items-start gap-3 cursor-pointer">
            <input
              type="checkbox"
              checked={notifyUser}
              onChange={(e) => setNotifyUser(e.target.checked)}
              className="mt-0.5 accent-primary-500"
            />
            <div>
              <span className="text-sm font-medium text-surface-200">{t('requests.notifyUser')}</span>
              <p className="text-xs text-surface-400 mt-0.5">{t('requests.notifyUserHint')}</p>
            </div>
          </label>
        )}

        <div className="flex gap-3 pt-2">
          <Button
            type="submit"
            variant={action === 'REJECTED' ? 'danger' : 'primary'}
            loading={isPending}
            className="flex-1"
          >
            {action === 'CONTACTED' ? t('requests.confirmContacted') : t('requests.confirmReject')}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('requests.cancel')}
          </Button>
        </div>

        {error && <p className="text-sm text-rose-400/80">{error}</p>}
      </form>
    </Modal>
  )
}
