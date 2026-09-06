import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Trash2, UserPlus, Users } from 'lucide-react'
import { Button } from '../ui/Button'
import { UserSearchSelect } from '../ui/UserSearchSelect'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import type { EventParticipants, Participant, SlotParticipants } from '../../types'

const MAX_SPOTS = 20

interface ParticipantsSectionProps {
  target: 'slot' | 'event'
  targetId: string
  /**
   * Whether new people can still be written down. A blocked slot is refused by the server
   * (`admin.slot.blocked`), so the form is hidden rather than offered and made to fail — but the
   * roster stays, because who WAS on it is exactly what the admin came to read.
   */
  canAdd?: boolean
}

/**
 * Who is booked on one session, and the two ways an admin adds somebody: an account, or a guest
 * with no account at all.
 *
 * ONE component for both call sites (slot and event), the same reason `AdminPrivateNote` and
 * `SettlementSection` are one each: a change to how this behaves is one edit, not two. The whole
 * slot/event difference lives in the `ops` adapter below and nowhere else. It owns its query and
 * its mutations, so the host modal passes nothing but an address.
 *
 * Callers must still gate on the admin role — this component would happily render for anybody,
 * and the 403 would arrive too late to be good UX.
 *
 * ⚠️ Deliberately no waiting list here, however useful it would read. `WaitlistEntryList` is
 * exported from `AdminReservationsPanel`, and this section is imported eagerly by the calendar
 * modals — pulling a panel into the `/kalendarz` chunk, which EVERY visitor downloads. The panels
 * keep rendering their own queue below their own copy of this list.
 */
export function ParticipantsSection({ target, targetId, canAdd = true }: ParticipantsSectionProps) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [showAddForm, setShowAddForm] = useState(false)
  const [addMode, setAddMode] = useState<'registered' | 'guest'>('registered')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [addParticipants, setAddParticipants] = useState(1)
  const [addComment, setAddComment] = useState('')
  const [guestNote, setGuestNote] = useState('')
  const [confirmCancelFor, setConfirmCancelFor] = useState<string | null>(null)
  const [confirmDeleteGuestId, setConfirmDeleteGuestId] = useState<string | null>(null)

  /**
   * The only place the two shapes differ.
   *
   * The query keys are the ones the host modals already use for their delete-confirmation lists
   * (`SlotDetailModal`, `EventSignupModal`), so the confirmation reads this section's cache
   * instead of asking the server a second time.
   *
   * Removing a registered person is not one operation with two ids: a slot cancels ONE
   * reservation row, while an event cancels the person off every day it spans.
   */
  const ops = useMemo(() => {
    if (target === 'slot') {
      return {
        queryKey: ['admin', 'participants', targetId],
        hostKey: ['slot'],
        adminListKey: ['admin', 'slots'],
        confirmRemoveKey: 'slots.confirmCancelReservation',
        removeActionKey: 'slots.cancelReservation',
        list: (): Promise<SlotParticipants | EventParticipants> =>
          adminApi.getSlotParticipants(targetId),
        addRegistered: (userId: string, participants: number, comment?: string) =>
          adminApi.addRegisteredParticipantToSlot(targetId, userId, participants, comment),
        addGuest: (note: string, participants: number) =>
          adminApi.addGuestParticipantToSlot(targetId, note, participants),
        removeGuest: (guestId: string) => adminApi.deleteGuestParticipantFromSlot(targetId, guestId),
        removeRegistered: (p: Participant) => adminApi.cancelReservationByAdmin(p.reservationId),
      }
    }
    return {
      queryKey: ['admin', 'events', targetId, 'participants'],
      hostKey: ['eventSummary'],
      adminListKey: ['admin', 'events'],
      confirmRemoveKey: 'events.confirmCancelParticipant',
      removeActionKey: 'events.cancelParticipant',
      list: (): Promise<SlotParticipants | EventParticipants> =>
        adminApi.getEventParticipants(targetId),
      addRegistered: (userId: string, participants: number, comment?: string) =>
        adminApi.addRegisteredParticipantToEvent(targetId, userId, participants, comment),
      addGuest: (note: string, participants: number) =>
        adminApi.addGuestParticipantToEvent(targetId, note, participants),
      removeGuest: (guestId: string) => adminApi.deleteGuestParticipantFromEvent(targetId, guestId),
      removeRegistered: (p: Participant) => adminApi.cancelEventParticipant(targetId, p.userId),
    }
  }, [target, targetId])

  const { data, isLoading } = useQuery({
    queryKey: ops.queryKey,
    queryFn: () => ops.list(),
  })

  // Only once the form is open and it is an account being looked for. This section loads on every
  // session an admin opens, and the full user list is of no use to reading a roster.
  const { data: allUsers } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.getAllUsers(),
    enabled: showAddForm && addMode === 'registered',
  })

  /**
   * `['admin','settlements']` is in here for a reason that is easy to miss: the settlement section
   * sits directly below this one, and a person written down here is a new row in it. Without this
   * the admin adds somebody and the price list under their thumb still says they are not there.
   */
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ops.queryKey })
    queryClient.invalidateQueries({ queryKey: ops.hostKey })
    queryClient.invalidateQueries({ queryKey: ops.adminListKey })
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
    // The admin writing themselves down (or off) is an ordinary thing to do from here, and their
    // own "my reservations" list is the one screen that would otherwise still say otherwise.
    queryClient.invalidateQueries({ queryKey: ['reservations'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })
  }

  const closeAddForm = () => {
    setShowAddForm(false)
    setSelectedUserId('')
    setAddParticipants(1)
    setAddComment('')
    setGuestNote('')
  }

  const addRegisteredMutation = useMutation({
    mutationFn: () => ops.addRegistered(selectedUserId, addParticipants, addComment || undefined),
    onSuccess: () => { refresh(); closeAddForm() },
  })

  const addGuestMutation = useMutation({
    mutationFn: () => ops.addGuest(guestNote.trim(), addParticipants),
    onSuccess: () => { refresh(); closeAddForm() },
  })

  const removeRegisteredMutation = useMutation({
    mutationFn: (p: Participant) => ops.removeRegistered(p),
    onSuccess: () => { refresh(); setConfirmCancelFor(null) },
  })

  const removeGuestMutation = useMutation({
    mutationFn: (guestId: string) => ops.removeGuest(guestId),
    onSuccess: () => { refresh(); setConfirmDeleteGuestId(null) },
  })

  if (isLoading || !data) return null

  const participants = data.participants
  const guests = data.guestParticipants
  const totalSpots = participants.reduce((s, p) => s + p.participants, 0)
    + guests.reduce((s, g) => s + g.participants, 0)
  // An event adds the requested seats to an existing reservation instead of refusing it (a slot
  // refuses). Saying so beforehand is the difference between "2" meaning two and meaning four.
  const alreadyBooked = target === 'event' && selectedUserId
    ? participants.find((p) => p.userId === selectedUserId) ?? null
    : null

  return (
    <div className="mt-4 rounded-lg border border-surface-700 bg-surface-800/40 p-3 space-y-3">
      <h3 className="flex items-center gap-2 text-sm font-medium text-surface-200">
        <Users className="w-4 h-4 text-surface-400" />
        {t('slots.registeredOf', { count: totalSpots, max: data.maxParticipants })}
      </h3>

      {participants.length === 0 && guests.length === 0 ? (
        <p className="text-sm text-surface-500">{t('slots.noRegisteredShort')}</p>
      ) : (
        <ul className="space-y-2">
          {participants.map((p) => (
            <li key={p.userId} className="bg-surface-800 rounded-lg px-3 py-2 text-sm">
              {confirmCancelFor === p.reservationId ? (
                <div className="space-y-2">
                  <p className="text-xs text-rose-400">{t(ops.confirmRemoveKey)}</p>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="danger"
                      loading={removeRegisteredMutation.isPending}
                      onClick={() => removeRegisteredMutation.mutate(p)}
                    >
                      {t(ops.removeActionKey)}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setConfirmCancelFor(null)}>
                      {t('slots.cancelEdit')}
                    </Button>
                  </div>
                  {removeRegisteredMutation.isError && (
                    <p className="text-xs text-rose-400">{getErrorMessage(removeRegisteredMutation.error)}</p>
                  )}
                </div>
              ) : (
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="font-medium text-surface-100">{p.fullName}</div>
                    <div className="text-xs text-surface-400">{p.email}</div>
                    <div className="text-xs text-surface-400">{p.phone}</div>
                    {p.comment && (
                      <div className="text-xs text-amber-400 mt-1">"{p.comment}"</div>
                    )}
                    {p.participants > 1 && (
                      <span className="inline-block mt-1 text-xs text-primary-400 bg-primary-500/10 px-2 py-0.5 rounded-full">
                        {t('slots.spotsLabel', { count: p.participants })}
                      </span>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => setConfirmCancelFor(p.reservationId)}
                    title={t(ops.removeActionKey)}
                    aria-label={`${t(ops.removeActionKey)} — ${p.fullName}`}
                    className="p-1 shrink-0 text-surface-400 hover:text-rose-400 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </li>
          ))}

          {guests.map((g) => (
            <li key={g.id} className="bg-amber-500/5 border border-amber-500/20 rounded-lg px-3 py-2 text-sm">
              {confirmDeleteGuestId === g.id ? (
                <div className="space-y-2">
                  <p className="text-xs text-rose-400">{t(ops.confirmRemoveKey)}</p>
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="danger"
                      loading={removeGuestMutation.isPending}
                      onClick={() => removeGuestMutation.mutate(g.id)}
                    >
                      {t(ops.removeActionKey)}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={() => setConfirmDeleteGuestId(null)}>
                      {t('slots.cancelEdit')}
                    </Button>
                  </div>
                  {removeGuestMutation.isError && (
                    <p className="text-xs text-rose-400">{getErrorMessage(removeGuestMutation.error)}</p>
                  )}
                </div>
              ) : (
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-xs text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded">
                        {t('slots.guest')}
                      </span>
                      <span className="font-medium text-surface-100">{g.note}</span>
                    </div>
                    {g.participants > 1 && (
                      <span className="inline-block mt-1 text-xs text-amber-400/80 bg-amber-500/10 px-2 py-0.5 rounded-full">
                        {t('slots.spotsLabel', { count: g.participants })}
                      </span>
                    )}
                  </div>
                  <button
                    type="button"
                    onClick={() => setConfirmDeleteGuestId(g.id)}
                    title={t(ops.removeActionKey)}
                    aria-label={`${t(ops.removeActionKey)} — ${g.note}`}
                    className="p-1 shrink-0 text-surface-400 hover:text-rose-400 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {canAdd && (!showAddForm ? (
        <button
          type="button"
          onClick={() => setShowAddForm(true)}
          className="flex items-center gap-2 text-sm text-primary-400 hover:text-primary-300 transition-colors"
        >
          <UserPlus className="w-4 h-4" />
          {t('slots.addParticipant')}
        </button>
      ) : (
        <div className="border border-surface-700 rounded-lg p-3 space-y-3">
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setAddMode('registered')}
              className={`text-xs px-3 py-1 rounded-full transition-colors ${addMode === 'registered' ? 'bg-primary-500/20 text-primary-300' : 'text-surface-400 hover:text-surface-200'}`}
            >
              {t('slots.addRegistered')}
            </button>
            <button
              type="button"
              onClick={() => setAddMode('guest')}
              className={`text-xs px-3 py-1 rounded-full transition-colors ${addMode === 'guest' ? 'bg-amber-500/20 text-amber-300' : 'text-surface-400 hover:text-surface-200'}`}
            >
              {t('slots.addGuest')}
            </button>
          </div>

          {addMode === 'registered' ? (
            <form
              className="space-y-2"
              onSubmit={(e) => { e.preventDefault(); addRegisteredMutation.mutate() }}
            >
              <UserSearchSelect
                users={allUsers ?? []}
                value={selectedUserId}
                onChange={(id) => { setSelectedUserId(id); addRegisteredMutation.reset() }}
              />
              {alreadyBooked && (
                <p className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 rounded px-2 py-1.5">
                  {t('events.alreadyRegistered', { count: alreadyBooked.participants })}
                </p>
              )}
              <div className="flex items-center gap-2">
                <label className="text-xs text-surface-400 shrink-0" htmlFor="participants-spots">
                  {t('slots.spots')}:
                </label>
                <input
                  id="participants-spots"
                  type="number"
                  min={1}
                  max={MAX_SPOTS}
                  value={addParticipants}
                  onChange={(e) => setAddParticipants(Number(e.target.value))}
                  className="w-16 bg-surface-800 border border-surface-700 rounded px-2 py-1 text-surface-100 text-sm"
                />
              </div>
              <input
                type="text"
                value={addComment}
                onChange={(e) => setAddComment(e.target.value)}
                placeholder={t('slots.commentOptional')}
                maxLength={500}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-surface-100 text-sm"
              />
              {addRegisteredMutation.isError && (
                <p className="text-xs text-rose-400">{getErrorMessage(addRegisteredMutation.error)}</p>
              )}
              <div className="flex gap-2">
                <Button
                  type="submit"
                  size="sm"
                  loading={addRegisteredMutation.isPending}
                  disabled={!selectedUserId}
                >
                  {t('slots.addParticipantConfirm')}
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={closeAddForm}>
                  {t('slots.cancelEdit')}
                </Button>
              </div>
            </form>
          ) : (
            <form
              className="space-y-2"
              onSubmit={(e) => { e.preventDefault(); addGuestMutation.mutate() }}
            >
              <input
                type="text"
                value={guestNote}
                onChange={(e) => setGuestNote(e.target.value)}
                placeholder={t('slots.guestNotePlaceholder')}
                maxLength={500}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-surface-100 text-sm"
              />
              <div className="flex items-center gap-2">
                <label className="text-xs text-surface-400 shrink-0" htmlFor="participants-guest-spots">
                  {t('slots.spots')}:
                </label>
                <input
                  id="participants-guest-spots"
                  type="number"
                  min={1}
                  max={MAX_SPOTS}
                  value={addParticipants}
                  onChange={(e) => setAddParticipants(Number(e.target.value))}
                  className="w-16 bg-surface-800 border border-surface-700 rounded px-2 py-1 text-surface-100 text-sm"
                />
              </div>
              {addGuestMutation.isError && (
                <p className="text-xs text-rose-400">{getErrorMessage(addGuestMutation.error)}</p>
              )}
              <div className="flex gap-2">
                <Button
                  type="submit"
                  size="sm"
                  loading={addGuestMutation.isPending}
                  disabled={!guestNote.trim()}
                >
                  {t('slots.addParticipantConfirm')}
                </Button>
                <Button type="button" size="sm" variant="ghost" onClick={closeAddForm}>
                  {t('slots.cancelEdit')}
                </Button>
              </div>
            </form>
          )}
        </div>
      ))}
    </div>
  )
}
