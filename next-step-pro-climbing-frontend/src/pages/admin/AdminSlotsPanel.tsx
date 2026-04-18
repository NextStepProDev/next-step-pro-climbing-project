import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format, parseISO } from 'date-fns'
import { Plus, Lock, LockOpen, Trash2, Users, Pencil, AlertTriangle, X, UserPlus } from 'lucide-react'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TimeScrollPicker } from '../../components/ui/TimeScrollPicker'
import { UserSearchSelect } from '../../components/ui/UserSearchSelect'
import { CreateSlotModal } from '../../components/calendar/CreateSlotModal'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { SlotParticipants, TimeSlotAdmin, User } from '../../types'

export function AdminSlotsPanel() {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const [filterDate, setFilterDate] = useState<string | null>(null)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showParticipantsModal, setShowParticipantsModal] = useState(false)
  const [editingSlot, setEditingSlot] = useState<TimeSlotAdmin | null>(null)
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<{ slotId: string; action: 'block' | 'delete' } | null>(null)

  const queryClient = useQueryClient()

  const invalidateSlots = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'slots', 'upcoming'] })
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
  }

  const { data: upcomingSlots, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'slots', 'upcoming'],
    queryFn: () => adminApi.getUpcomingSlots(),
  })

  const { data: participants } = useQuery({
    queryKey: ['admin', 'participants', selectedSlotId],
    queryFn: () => adminApi.getSlotParticipants(selectedSlotId!),
    enabled: !!selectedSlotId && showParticipantsModal,
  })

  const { data: confirmParticipants } = useQuery({
    queryKey: ['admin', 'participants', confirmAction?.slotId],
    queryFn: () => adminApi.getSlotParticipants(confirmAction!.slotId),
    enabled: !!confirmAction,
  })

  const blockMutation = useMutation({
    mutationFn: ({ slotId, reason }: { slotId: string; reason?: string }) =>
      adminApi.blockTimeSlot(slotId, reason),
    onSuccess: invalidateSlots,
  })

  const unblockMutation = useMutation({
    mutationFn: adminApi.unblockTimeSlot,
    onSuccess: invalidateSlots,
  })

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteTimeSlot,
    onSuccess: invalidateSlots,
  })

  // Filter and group slots
  const filteredSlots = filterDate
    ? (upcomingSlots ?? []).filter((s) => s.date === filterDate)
    : (upcomingSlots ?? [])

  const groupedByDate = filteredSlots.reduce<Record<string, TimeSlotAdmin[]>>((acc, slot) => {
    acc[slot.date] = [...(acc[slot.date] ?? []), slot]
    return acc
  }, {})
  const sortedDays = Object.keys(groupedByDate).sort()

  return (
    <div>
      {/* Controls */}
      <div className="flex items-center gap-3 mb-6 flex-wrap">
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={filterDate ?? ''}
            onChange={(e) => {
              setFilterDate(e.target.value || null)
              e.target.blur()
            }}
            className="bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          {filterDate && (
            <button
              onClick={() => setFilterDate(null)}
              className="p-2 text-dark-400 hover:text-dark-100 transition-colors"
              aria-label={t('slots.clearFilter')}
              title={t('slots.clearFilter')}
            >
              <X className="w-4 h-4" />
            </button>
          )}
        </div>
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="w-4 h-4 mr-2" />
          {t('slots.addSlot')}
        </Button>
      </div>

      {/* Slots list */}
      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <QueryError error={error} onRetry={() => refetch()} />
      ) : sortedDays.length === 0 ? (
        <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
          {filterDate ? t('slots.noSlotsOnDay') : t('slots.noUpcoming')}
        </div>
      ) : (
        <div className="space-y-6">
          {sortedDays.map((date) => (
            <div key={date}>
              <h3 className="text-sm font-semibold text-dark-300 uppercase tracking-wider mb-2 px-1">
                {format(parseISO(date), 'EEEE, d MMMM', { locale })}
              </h3>
              <div className="space-y-2">
                {groupedByDate[date].map((slot) => (
                  <SlotRow
                    key={slot.id}
                    slot={slot}
                    t={t}
                    onShowParticipants={() => {
                      setSelectedSlotId(slot.id)
                      setShowParticipantsModal(true)
                    }}
                    onEdit={() => setEditingSlot(slot)}
                    onBlock={() => setConfirmAction({ slotId: slot.id, action: 'block' })}
                    onUnblock={() => unblockMutation.mutate(slot.id)}
                    onDelete={() => setConfirmAction({ slotId: slot.id, action: 'delete' })}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Create Slot Modal */}
      <CreateSlotModal
        key={filterDate ?? 'no-date'}
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        defaultDate={filterDate ?? format(new Date(), 'yyyy-MM-dd')}
      />

      {/* Edit Slot Modal */}
      <EditSlotModal
        key={editingSlot?.id}
        isOpen={!!editingSlot}
        onClose={() => setEditingSlot(null)}
        slot={editingSlot}
        onSuccess={invalidateSlots}
      />

      {/* Participants Modal */}
      {participants && (
        <ParticipantsModal
          isOpen={showParticipantsModal}
          onClose={() => {
            setShowParticipantsModal(false)
            setSelectedSlotId(null)
          }}
          data={participants}
          slotId={selectedSlotId!}
        />
      )}

      {/* Confirm Block/Delete Modal */}
      {confirmAction && confirmParticipants && (
        <ConfirmBlockModal
          isOpen={!!confirmAction}
          onClose={() => setConfirmAction(null)}
          action={confirmAction.action}
          data={confirmParticipants}
          onConfirm={() => {
            if (confirmAction.action === 'block') {
              blockMutation.mutate({ slotId: confirmAction.slotId })
            } else {
              deleteMutation.mutate(confirmAction.slotId)
            }
            setConfirmAction(null)
          }}
        />
      )}
    </div>
  )
}

function SlotRow({
  slot,
  t,
  onShowParticipants,
  onEdit,
  onBlock,
  onUnblock,
  onDelete,
}: {
  slot: TimeSlotAdmin
  t: (key: string) => string
  onShowParticipants: () => void
  onEdit: () => void
  onBlock: () => void
  onUnblock: () => void
  onDelete: () => void
}) {
  return (
    <div className="bg-dark-900 rounded-lg border border-dark-800 p-4 flex items-center justify-between">
      <div>
        <div className="font-medium text-dark-100">
          {slot.startTime.slice(0, 5)} – {slot.endTime.slice(0, 5)}
        </div>
        {slot.title && (
          <div className="text-sm text-primary-400">{slot.title}</div>
        )}
        <div className="flex items-center gap-2 mt-1">
          {slot.isAvailabilityWindow ? (
            <span className="text-xs text-violet-400 bg-violet-500/10 px-2 py-0.5 rounded-full">
              {t('slots.availabilityWindow')}
            </span>
          ) : (
            <span className="text-sm text-dark-400">
              {slot.currentParticipants}/{slot.maxParticipants}
            </span>
          )}
          {slot.blocked && (
            <span className="text-xs text-rose-400 bg-rose-500/10 px-2 py-0.5 rounded-full">
              {t('slots.statusBlocked')}
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="sm"
          aria-label={t('slots.showParticipants')}
          onClick={onShowParticipants}
        >
          <Users className="w-4 h-4" />
        </Button>

        <Button
          variant="ghost"
          size="sm"
          aria-label={t('slots.editSlot')}
          onClick={onEdit}
        >
          <Pencil className="w-4 h-4" />
        </Button>

        {slot.blocked ? (
          <Button
            variant="ghost"
            size="sm"
            aria-label={t('slots.unblockSlot')}
            title={t('slots.blockedClickToUnblock')}
            onClick={onUnblock}
            className="text-rose-400 hover:text-rose-300 hover:bg-rose-500/10"
          >
            <Lock className="w-4 h-4" />
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="sm"
            aria-label={t('slots.blockSlot')}
            title={t('slots.availableClickToBlock')}
            onClick={onBlock}
          >
            <LockOpen className="w-4 h-4" />
          </Button>
        )}

        <Button
          variant="ghost"
          size="sm"
          aria-label={t('slots.deleteSlot')}
          onClick={onDelete}
        >
          <Trash2 className="w-4 h-4 text-rose-400/80" />
        </Button>
      </div>
    </div>
  )
}

function EditSlotModal({
  isOpen,
  onClose,
  slot,
  onSuccess,
}: {
  isOpen: boolean
  onClose: () => void
  slot: TimeSlotAdmin | null
  onSuccess: () => void
}) {
  const { t } = useTranslation('admin')
  const [form, setForm] = useState({
    startTime: slot?.startTime.slice(0, 5) ?? '',
    endTime: slot?.endTime.slice(0, 5) ?? '',
    maxParticipants: slot?.maxParticipants ?? 4,
    title: slot?.title ?? '',
    isAvailabilityWindow: slot?.isAvailabilityWindow ?? false,
  })

  const updateMutation = useMutation({
    mutationFn: (data: { startTime?: string; endTime?: string; maxParticipants?: number; title?: string; isAvailabilityWindow?: boolean }) =>
      adminApi.updateTimeSlot(slot!.id, data),
    onSuccess: () => {
      onSuccess()
      onClose()
    },
  })

  if (!slot) return null

  const timeError = form.endTime <= form.startTime
    ? t('slots.endAfterStart')
    : null

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('slots.editTitle')}>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          if (timeError) return
          updateMutation.mutate({
            startTime: form.startTime,
            endTime: form.endTime,
            maxParticipants: form.isAvailabilityWindow ? 1 : form.maxParticipants,
            title: form.title || '',
            isAvailabilityWindow: form.isAvailabilityWindow,
          })
        }}
        className="space-y-4"
      >
        <div>
          <label className="block text-sm text-dark-400 mb-1">{t('slots.titleLabel')}</label>
          <input
            type="text"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            placeholder={t('slots.titlePlaceholder')}
            maxLength={200}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div>
          <div className="grid grid-cols-2 gap-4">
            <TimeScrollPicker
              label={t('slots.from')}
              value={form.startTime}
              onChange={(v) => setForm({ ...form, startTime: v })}
            />
            <TimeScrollPicker
              label={t('slots.to')}
              value={form.endTime}
              onChange={(v) => setForm({ ...form, endTime: v })}
            />
          </div>
          {timeError && (
            <p className="text-sm text-rose-400/80 mt-1">{timeError}</p>
          )}
        </div>

        {/* Availability window toggle */}
        <label className="flex items-start gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={form.isAvailabilityWindow}
            onChange={(e) => setForm({ ...form, isAvailabilityWindow: e.target.checked })}
            className="mt-0.5 accent-violet-500"
          />
          <div>
            <span className="text-sm font-medium text-violet-300">{t('slots.availabilityWindow')}</span>
            <p className="text-xs text-dark-400 mt-0.5">{t('slots.availabilityWindowHint')}</p>
          </div>
        </label>

        {!form.isAvailabilityWindow && (
          <div>
            <label className="block text-sm text-dark-400 mb-1">{t('slots.maxParticipants')}</label>
            <input
              type="number"
              min={1}
              value={form.maxParticipants}
              onChange={(e) => setForm({ ...form, maxParticipants: parseInt(e.target.value) })}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
            />
          </div>
        )}

        <div className="flex gap-3 pt-4">
          <Button type="submit" loading={updateMutation.isPending} className="flex-1">
            {t('slots.saveChanges')}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('slots.cancel')}
          </Button>
        </div>

        {updateMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(updateMutation.error)}
          </p>
        )}
      </form>
    </Modal>
  )
}

function ConfirmBlockModal({
  isOpen,
  onClose,
  action,
  data,
  onConfirm,
}: {
  isOpen: boolean
  onClose: () => void
  action: 'block' | 'delete'
  data: SlotParticipants
  onConfirm: () => void
}) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const hasParticipants = data.participants.length > 0

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={hasParticipants
        ? t('slots.warningActiveReservations')
        : action === 'block' ? t('slots.blockTitle') : t('slots.deleteTitle')}
    >
      <div className="space-y-4">
        <div className="text-sm text-dark-400">
          {format(parseISO(data.date), 'EEEE, d MMMM', { locale })} |{' '}
          {data.startTime.slice(0, 5)} - {data.endTime.slice(0, 5)}
        </div>

        {hasParticipants ? (
          <>
            <div className="flex items-start gap-3 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
              <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
              <div className="text-sm text-rose-300">
                <p className="font-medium mb-1">{t('slots.hasActiveReservations')}</p>
                <p className="text-rose-400/80">
                  {action === 'block' ? t('slots.blockWarning') : t('slots.deleteWarning')}
                </p>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-medium text-dark-300 mb-2">
                {t('slots.registered', { count: data.participants.length })}
              </h3>
              <ul className="space-y-2 max-h-48 overflow-y-auto">
                {data.participants.map((p) => (
                  <li key={p.userId} className="bg-dark-800 rounded-lg p-3">
                    <div className="font-medium text-dark-100">{p.fullName}</div>
                    <div className="text-sm text-dark-400">{p.email}</div>
                  </li>
                ))}
              </ul>
            </div>
          </>
        ) : (
          <p className="text-dark-400 text-sm">
            {t('slots.noRegistered')}
          </p>
        )}

        <div className="flex gap-3 pt-2">
          <Button
            variant="danger"
            className="flex-1"
            onClick={onConfirm}
          >
            {hasParticipants
              ? action === 'block' ? t('slots.blockAndCancel') : t('slots.deleteAndCancel')
              : action === 'block' ? t('slots.blockSimple') : t('slots.deleteSimple')}
          </Button>
          <Button variant="ghost" onClick={onClose}>
            {t('slots.cancel')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

function ParticipantsModal({
  isOpen,
  onClose,
  data,
  slotId,
}: {
  isOpen: boolean
  onClose: () => void
  data: SlotParticipants
  slotId: string
}) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const [confirmCancelFor, setConfirmCancelFor] = useState<string | null>(null)
  const [editingSpotsFor, setEditingSpotsFor] = useState<string | null>(null)
  const [editedSpots, setEditedSpots] = useState<number>(1)
  const [showAddForm, setShowAddForm] = useState(false)
  const [addMode, setAddMode] = useState<'registered' | 'guest'>('registered')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [addParticipants, setAddParticipants] = useState(1)
  const [addComment, setAddComment] = useState('')
  const [guestNote, setGuestNote] = useState('')
  const [confirmDeleteGuestId, setConfirmDeleteGuestId] = useState<string | null>(null)

  const totalSpots = data.participants.reduce((s, p) => s + p.participants, 0)
    + data.guestParticipants.reduce((s, g) => s + g.participants, 0)

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'participants', slotId] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'slots', 'upcoming'] })
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
  }

  const { data: allUsers } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.getAllUsers(),
    enabled: showAddForm && addMode === 'registered',
  })

  const cancelReservationMutation = useMutation({
    mutationFn: (reservationId: string) => adminApi.cancelReservationByAdmin(reservationId),
    onSuccess: () => { invalidate(); setConfirmCancelFor(null) },
  })

  const updateParticipantsMutation = useMutation({
    mutationFn: ({ reservationId, participants }: { reservationId: string; participants: number }) =>
      adminApi.updateReservationParticipants(reservationId, participants),
    onSuccess: () => { invalidate(); setEditingSpotsFor(null) },
  })

  const addRegisteredMutation = useMutation({
    mutationFn: () => adminApi.addRegisteredParticipantToSlot(slotId, selectedUserId, addParticipants, addComment || undefined),
    onSuccess: () => {
      invalidate()
      setShowAddForm(false)
      setSelectedUserId('')
      setAddParticipants(1)
      setAddComment('')
    },
  })

  const addGuestMutation = useMutation({
    mutationFn: () => adminApi.addGuestParticipantToSlot(slotId, guestNote, addParticipants),
    onSuccess: () => {
      invalidate()
      setShowAddForm(false)
      setGuestNote('')
      setAddParticipants(1)
    },
  })

  const deleteGuestMutation = useMutation({
    mutationFn: (guestId: string) => adminApi.deleteGuestParticipantFromSlot(slotId, guestId),
    onSuccess: () => { invalidate(); setConfirmDeleteGuestId(null) },
  })

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('slots.participantsTitle')}>
      <div className="space-y-4">
        <div className="text-sm text-dark-400">
          {format(parseISO(data.date), 'EEEE, d MMMM', { locale })} |{' '}
          {data.startTime.slice(0, 5)} - {data.endTime.slice(0, 5)}
        </div>

        {/* Confirmed participants */}
        <div>
          <h3 className="text-sm font-medium text-dark-300 mb-2">
            {t('slots.registeredOf', { count: totalSpots, max: data.maxParticipants })}
          </h3>
          {data.participants.length === 0 && data.guestParticipants.length === 0 ? (
            <p className="text-dark-500 text-sm">{t('slots.noRegisteredShort')}</p>
          ) : (
            <ul className="space-y-2">
              {data.participants.map((p) => (
                <li key={p.userId} className="bg-dark-800 rounded-lg p-3">
                  {confirmCancelFor === p.reservationId ? (
                    <div className="space-y-2">
                      <p className="text-sm text-rose-400">{t('slots.confirmCancelReservation')}</p>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="danger"
                          loading={cancelReservationMutation.isPending}
                          onClick={() => cancelReservationMutation.mutate(p.reservationId)}
                        >
                          {t('slots.cancelReservation')}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setConfirmCancelFor(null)}>
                          {t('slots.cancelEdit')}
                        </Button>
                      </div>
                    </div>
                  ) : editingSpotsFor === p.reservationId ? (
                    <div className="space-y-2">
                      <p className="text-sm text-dark-300">{p.fullName} — {t('slots.editSpots')}</p>
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min={1}
                          max={data.maxParticipants}
                          value={editedSpots}
                          onChange={(e) => setEditedSpots(Number(e.target.value))}
                          className="w-20 bg-dark-700 border border-dark-600 rounded px-2 py-1 text-dark-100 text-sm"
                        />
                        <Button
                          size="sm"
                          variant="primary"
                          loading={updateParticipantsMutation.isPending}
                          onClick={() => updateParticipantsMutation.mutate({ reservationId: p.reservationId, participants: editedSpots })}
                        >
                          {t('slots.saveSpots')}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setEditingSpotsFor(null)}>
                          {t('slots.cancelEdit')}
                        </Button>
                      </div>
                      {updateParticipantsMutation.isError && (
                        <p className="text-xs text-rose-400">{getErrorMessage(updateParticipantsMutation.error)}</p>
                      )}
                    </div>
                  ) : (
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="font-medium text-dark-100">{p.fullName}</div>
                        <div className="text-sm text-dark-400">{p.email}</div>
                        <div className="text-sm text-dark-400">{p.phone}</div>
                        {p.comment && (
                          <div className="text-sm text-amber-400 mt-1">"{p.comment}"</div>
                        )}
                        {p.participants > 1 && (
                          <span className="inline-block mt-1 text-xs text-primary-400 bg-primary-500/10 px-2 py-0.5 rounded-full">
                            {t('slots.spotsLabel', { count: p.participants })}
                          </span>
                        )}
                      </div>
                      <div className="flex gap-1 shrink-0">
                        <button
                          onClick={() => { setEditingSpotsFor(p.reservationId); setEditedSpots(p.participants) }}
                          title={t('slots.editSpots')}
                          className="p-1 text-dark-400 hover:text-primary-400 transition-colors"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setConfirmCancelFor(p.reservationId)}
                          title={t('slots.cancelReservation')}
                          className="p-1 text-dark-400 hover:text-rose-400 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              ))}

              {/* Guest reservations */}
              {data.guestParticipants.map((g) => (
                <li key={g.id} className="bg-amber-500/5 border border-amber-500/20 rounded-lg p-3">
                  {confirmDeleteGuestId === g.id ? (
                    <div className="space-y-2">
                      <p className="text-sm text-rose-400">{t('slots.confirmCancelReservation')}</p>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="danger"
                          loading={deleteGuestMutation.isPending}
                          onClick={() => deleteGuestMutation.mutate(g.id)}
                        >
                          {t('slots.cancelReservation')}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setConfirmDeleteGuestId(null)}>
                          {t('slots.cancelEdit')}
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded">
                            {t('slots.guest')}
                          </span>
                          <span className="font-medium text-dark-100">{g.note}</span>
                        </div>
                        {g.participants > 1 && (
                          <span className="inline-block mt-1 text-xs text-amber-400/80 bg-amber-500/10 px-2 py-0.5 rounded-full">
                            {t('slots.spotsLabel', { count: g.participants })}
                          </span>
                        )}
                      </div>
                      <button
                        onClick={() => setConfirmDeleteGuestId(g.id)}
                        title={t('slots.cancelReservation')}
                        className="p-1 text-dark-400 hover:text-rose-400 transition-colors shrink-0"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Add participant */}
        {!showAddForm ? (
          <button
            onClick={() => setShowAddForm(true)}
            className="flex items-center gap-2 text-sm text-primary-400 hover:text-primary-300 transition-colors"
          >
            <UserPlus className="w-4 h-4" />
            {t('slots.addParticipant')}
          </button>
        ) : (
          <div className="border border-dark-700 rounded-lg p-3 space-y-3">
            {/* Mode tabs */}
            <div className="flex gap-2">
              <button
                onClick={() => setAddMode('registered')}
                className={`text-xs px-3 py-1 rounded-full transition-colors ${addMode === 'registered' ? 'bg-primary-500/20 text-primary-300' : 'text-dark-400 hover:text-dark-200'}`}
              >
                {t('slots.addRegistered')}
              </button>
              <button
                onClick={() => setAddMode('guest')}
                className={`text-xs px-3 py-1 rounded-full transition-colors ${addMode === 'guest' ? 'bg-amber-500/20 text-amber-300' : 'text-dark-400 hover:text-dark-200'}`}
              >
                {t('slots.addGuest')}
              </button>
            </div>

            {addMode === 'registered' ? (
              <div className="space-y-2">
                <UserSearchSelect
                  users={(allUsers as User[] | undefined) ?? []}
                  value={selectedUserId}
                  onChange={setSelectedUserId}
                />
                <div className="flex items-center gap-2">
                  <label className="text-xs text-dark-400 shrink-0">{t('slots.spots')}:</label>
                  <input
                    type="number"
                    min={1}
                    max={20}
                    value={addParticipants}
                    onChange={(e) => setAddParticipants(Number(e.target.value))}
                    className="w-16 bg-dark-800 border border-dark-700 rounded px-2 py-1 text-dark-100 text-sm"
                  />
                </div>
                <input
                  type="text"
                  value={addComment}
                  onChange={(e) => setAddComment(e.target.value)}
                  placeholder={t('slots.commentOptional')}
                  maxLength={500}
                  className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 text-sm"
                />
                {addRegisteredMutation.isError && (
                  <p className="text-xs text-rose-400">{getErrorMessage(addRegisteredMutation.error)}</p>
                )}
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    loading={addRegisteredMutation.isPending}
                    disabled={!selectedUserId}
                    onClick={() => addRegisteredMutation.mutate()}
                  >
                    {t('slots.addParticipantConfirm')}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setShowAddForm(false)}>
                    {t('slots.cancelEdit')}
                  </Button>
                </div>
              </div>
            ) : (
              <div className="space-y-2">
                <input
                  type="text"
                  value={guestNote}
                  onChange={(e) => setGuestNote(e.target.value)}
                  placeholder={t('slots.guestNotePlaceholder')}
                  maxLength={500}
                  className="w-full bg-dark-800 border border-dark-700 rounded-lg px-3 py-2 text-dark-100 text-sm"
                />
                <div className="flex items-center gap-2">
                  <label className="text-xs text-dark-400 shrink-0">{t('slots.spots')}:</label>
                  <input
                    type="number"
                    min={1}
                    max={20}
                    value={addParticipants}
                    onChange={(e) => setAddParticipants(Number(e.target.value))}
                    className="w-16 bg-dark-800 border border-dark-700 rounded px-2 py-1 text-dark-100 text-sm"
                  />
                </div>
                {addGuestMutation.isError && (
                  <p className="text-xs text-rose-400">{getErrorMessage(addGuestMutation.error)}</p>
                )}
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    loading={addGuestMutation.isPending}
                    disabled={!guestNote.trim()}
                    onClick={() => addGuestMutation.mutate()}
                  >
                    {t('slots.addParticipantConfirm')}
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setShowAddForm(false)}>
                    {t('slots.cancelEdit')}
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}

      </div>
    </Modal>
  )
}
