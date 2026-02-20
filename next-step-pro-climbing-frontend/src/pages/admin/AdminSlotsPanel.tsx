import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Plus, Lock, LockOpen, Trash2, Users, Pencil, AlertTriangle } from 'lucide-react'
import { calendarApi, adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TimeScrollPicker } from '../../components/ui/TimeScrollPicker'
import { CreateSlotModal } from '../../components/calendar/CreateSlotModal'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { SlotParticipants, TimeSlot } from '../../types'

export function AdminSlotsPanel() {
  const { t } = useTranslation('admin')
  const [selectedDate, setSelectedDate] = useState(format(new Date(), 'yyyy-MM-dd'))
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [showParticipantsModal, setShowParticipantsModal] = useState(false)
  const [editingSlot, setEditingSlot] = useState<TimeSlot | null>(null)
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null)
  const [confirmAction, setConfirmAction] = useState<{ slotId: string; action: 'block' | 'delete' } | null>(null)

  const queryClient = useQueryClient()

  const { data: dayData, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['calendar', 'day', selectedDate],
    queryFn: () => calendarApi.getDayView(selectedDate),
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
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['calendar'] }),
  })

  const unblockMutation = useMutation({
    mutationFn: adminApi.unblockTimeSlot,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['calendar'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteTimeSlot,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['calendar'] }),
  })

  return (
    <div>
      {/* Date selector */}
      <div className="flex items-center gap-4 mb-6">
        <input
          type="date"
          value={selectedDate}
          onChange={(e) => {
            setSelectedDate(e.target.value)
            e.target.blur()
          }}
          className="bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
        />
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
      ) : (
        <div className="space-y-3">
          {dayData?.slots.length === 0 ? (
            <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
              {t('slots.noSlots')}
            </div>
          ) : (
            dayData?.slots.map((slot) => (
              <div
                key={slot.id}
                className="bg-dark-900 rounded-lg border border-dark-800 p-4 flex items-center justify-between"
              >
                <div>
                  <div className="font-medium text-dark-100">
                    {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}
                  </div>
                  {slot.eventTitle && (
                    <div className="text-sm text-primary-400">{slot.eventTitle}</div>
                  )}
                  <div className="text-sm text-dark-400">
                    Status: {slot.status}
                    {slot.isUserRegistered && ` ${t('slots.yourReservation')}`}
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('slots.showParticipants')}
                    onClick={() => {
                      setSelectedSlotId(slot.id)
                      setShowParticipantsModal(true)
                    }}
                  >
                    <Users className="w-4 h-4" />
                  </Button>

                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('slots.editSlot')}
                    onClick={() => setEditingSlot(slot)}
                  >
                    <Pencil className="w-4 h-4" />
                  </Button>

                  {slot.status === 'BLOCKED' ? (
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={t('slots.unblockSlot')}
                      title={t('slots.blockedClickToUnblock')}
                      onClick={() => unblockMutation.mutate(slot.id)}
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
                      onClick={() => setConfirmAction({ slotId: slot.id, action: 'block' })}
                    >
                      <LockOpen className="w-4 h-4" />
                    </Button>
                  )}

                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label={t('slots.deleteSlot')}
                    onClick={() => setConfirmAction({ slotId: slot.id, action: 'delete' })}
                  >
                    <Trash2 className="w-4 h-4 text-rose-400/80" />
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* Create Slot Modal */}
      <CreateSlotModal
        key={selectedDate}
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        defaultDate={selectedDate}
      />

      {/* Edit Slot Modal */}
      <EditSlotModal
        key={editingSlot?.id}
        isOpen={!!editingSlot}
        onClose={() => setEditingSlot(null)}
        slot={editingSlot}
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

function EditSlotModal({
  isOpen,
  onClose,
  slot,
}: {
  isOpen: boolean
  onClose: () => void
  slot: TimeSlot | null
}) {
  const { t } = useTranslation('admin')
  const [form, setForm] = useState({
    startTime: slot?.startTime.slice(0, 5) ?? '',
    endTime: slot?.endTime.slice(0, 5) ?? '',
    maxParticipants: slot?.maxParticipants ?? 4,
    title: slot?.eventTitle ?? '',
  })

  const queryClient = useQueryClient()

  const updateMutation = useMutation({
    mutationFn: (data: { startTime?: string; endTime?: string; maxParticipants?: number; title?: string }) =>
      adminApi.updateTimeSlot(slot!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
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
            maxParticipants: form.maxParticipants,
            title: form.title || '',
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
          {format(new Date(data.date), 'EEEE, d MMMM', { locale })} |{' '}
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
}: {
  isOpen: boolean
  onClose: () => void
  data: SlotParticipants
}) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('slots.participantsTitle')}>
      <div className="space-y-4">
        <div className="text-sm text-dark-400">
          {format(new Date(data.date), 'EEEE, d MMMM', { locale })} |{' '}
          {data.startTime.slice(0, 5)} - {data.endTime.slice(0, 5)}
        </div>

        {/* Confirmed participants */}
        <div>
          <h3 className="text-sm font-medium text-dark-300 mb-2">
            {t('slots.registeredOf', { count: data.participants.length, max: data.maxParticipants })}
          </h3>
          {data.participants.length === 0 ? (
            <p className="text-dark-500 text-sm">{t('slots.noRegisteredShort')}</p>
          ) : (
            <ul className="space-y-2">
              {data.participants.map((p) => (
                <li key={p.userId} className="bg-dark-800 rounded-lg p-3">
                  <div className="font-medium text-dark-100">{p.fullName}</div>
                  <div className="text-sm text-dark-400">{p.email}</div>
                  <div className="text-sm text-dark-400">{p.phone}</div>
                  {p.comment && (
                    <div className="text-sm text-amber-400 mt-1">"{p.comment}"</div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>

      </div>
    </Modal>
  )
}
