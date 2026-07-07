import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { format } from 'date-fns'
import { Plus, Trash2, Eye, EyeOff, Clock, Pencil, ChevronDown, ChevronRight, ChevronLeft, MapPin, Users, Mail, Phone, AlertTriangle, BookOpen, UserPlus } from 'lucide-react'
import { adminApi, adminCoursesApi } from '../../api/client'
import { UserSearchSelect } from '../../components/ui/UserSearchSelect'
import { InvitedUsersPicker } from '../../components/ui/InvitedUsersPicker'
import { InviteNotifySection } from '../../components/ui/InviteNotifySection'
import { getErrorMessage } from '../../utils/errors'
import { useDirty } from '../../hooks/useDirty'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TimeScrollPicker } from '../../components/ui/TimeScrollPicker'
import type { CreateEventRequest, EventDetail, EventType, InvitedUser, User } from '../../types'
import { getEventColorByType } from '../../utils/events'

export function AdminEventsPanel() {
  const { t } = useTranslation('admin')
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingEvent, setEditingEvent] = useState<EventDetail | null>(null)
  const [showArchive, setShowArchive] = useState(false)
  const [archivePage, setArchivePage] = useState(1)
  const queryClient = useQueryClient()

  const { data: events, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'events'],
    queryFn: adminApi.getAllEvents,
  })

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ eventId, data }: { eventId: string; data: Partial<CreateEventRequest> & { active?: boolean } }) =>
      adminApi.updateEvent(eventId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const now = new Date()
  const todayStr = format(now, 'yyyy-MM-dd')
  const currentTime = format(now, 'HH:mm')
  const isEventPast = (e: EventDetail) => {
    if (e.endDate < todayStr) return true
    if (e.endDate === todayStr && e.endTime && e.endTime.slice(0, 5) <= currentTime) return true
    return false
  }
  const upcoming = events?.filter(e => !isEventPast(e)) ?? []
  const archive = events?.filter(e => isEventPast(e)) ?? []

  return (
    <div>
      <div className="flex justify-end mb-6">
        <Button onClick={() => setShowCreateModal(true)}>
          <Plus className="w-4 h-4 mr-2" />
          {t('events.addEvent')}
        </Button>
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <QueryError error={error} onRetry={() => refetch()} />
      ) : (
        <div className="space-y-6">
          {/* Upcoming events */}
          {upcoming.length === 0 ? (
            <div className="bg-surface-900 rounded-lg border border-surface-800 p-8 text-center text-surface-400">
              {t('events.noUpcoming')}
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-surface-400">{t('events.upcoming', { count: upcoming.length })}</p>
              {upcoming.map((event) => (
                <EventCard
                  key={event.id}
                  event={event}
                  onEdit={() => setEditingEvent(event)}
                  onToggleActive={() =>
                    updateMutation.mutate({ eventId: event.id, data: { active: !event.active } })
                  }
                  onDelete={() => deleteMutation.mutate(event.id)}
                />
              ))}
            </div>
          )}

          {/* Archive */}
          {archive.length > 0 && (() => {
            const pageSize = 15
            const totalPages = Math.max(1, Math.ceil(archive.length / pageSize))
            const safePage = Math.min(archivePage, totalPages)
            const paged = archive.slice((safePage - 1) * pageSize, safePage * pageSize)

            return (
              <div>
                <button
                  onClick={() => setShowArchive(!showArchive)}
                  className="flex items-center gap-2 text-sm text-surface-500 hover:text-surface-300 transition-colors mb-3"
                >
                  {showArchive ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                  {t('events.archive', { count: archive.length })}
                </button>

                {showArchive && (
                  <div className="space-y-3">
                    {paged.map((event) => (
                      <EventCard
                        key={event.id}
                        event={event}
                        archived
                        onEdit={() => setEditingEvent(event)}
                        onToggleActive={() =>
                          updateMutation.mutate({ eventId: event.id, data: { active: !event.active } })
                        }
                        onDelete={() => deleteMutation.mutate(event.id)}
                      />
                    ))}

                    {totalPages > 1 && (
                      <div className="flex items-center justify-between pt-2">
                        <span className="text-sm text-surface-500">
                          {t('events.totalEvents', { count: archive.length })}
                        </span>
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setArchivePage((p) => Math.max(1, p - 1))}
                            disabled={safePage <= 1}
                            className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            <ChevronLeft className="w-4 h-4" />
                          </button>
                          <span className="text-sm text-surface-300 min-w-[80px] text-center">
                            {safePage} / {totalPages}
                          </span>
                          <button
                            onClick={() => setArchivePage((p) => Math.min(totalPages, p + 1))}
                            disabled={safePage >= totalPages}
                            className="p-2 text-surface-400 hover:text-surface-100 hover:bg-surface-800 rounded-lg transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
                          >
                            <ChevronRight className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })()}
        </div>
      )}

      <CreateEventModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
      />

      <EditEventModal
        key={editingEvent?.id}
        event={editingEvent}
        isOpen={!!editingEvent}
        onClose={() => setEditingEvent(null)}
      />
    </div>
  )
}

/* =============================== Event Card =============================== */

function EventCard({
  event,
  archived,
  onEdit,
  onToggleActive,
  onDelete,
}: {
  event: EventDetail
  archived?: boolean
  onEdit: () => void
  onToggleActive: () => void
  onDelete: () => void
}) {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const [showParticipants, setShowParticipants] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const queryClient = useQueryClient()
  const [confirmCancelParticipant, setConfirmCancelParticipant] = useState<string | null>(null)
  const [editingSpotsFor, setEditingSpotsFor] = useState<string | null>(null)
  const [editedSpots, setEditedSpots] = useState(1)
  const [showAddForm, setShowAddForm] = useState(false)
  const [addMode, setAddMode] = useState<'registered' | 'guest'>('registered')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [addParticipants, setAddParticipants] = useState(1)
  const [addComment, setAddComment] = useState('')
  const [guestNote, setGuestNote] = useState('')
  const [confirmDeleteGuestId, setConfirmDeleteGuestId] = useState<string | null>(null)

  const { data: participantsData, isLoading: participantsLoading } = useQuery({
    queryKey: ['admin', 'events', event.id, 'participants'],
    queryFn: () => adminApi.getEventParticipants(event.id),
    enabled: showParticipants || showDeleteConfirm,
  })

  const { data: allUsers } = useQuery({
    queryKey: ['admin', 'users'],
    queryFn: () => adminApi.getAllUsers(),
    enabled: showParticipants && showAddForm && addMode === 'registered',
  })

  const invalidateParticipants = () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'events', event.id, 'participants'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
    queryClient.invalidateQueries({ queryKey: ['calendar'] })
  }

  const cancelEventParticipantMutation = useMutation({
    mutationFn: (userId: string) => adminApi.cancelEventParticipant(event.id, userId),
    onSuccess: () => { invalidateParticipants(); setConfirmCancelParticipant(null) },
  })

  const updateEventParticipantsMutation = useMutation({
    mutationFn: ({ userId, participants }: { userId: string; participants: number }) =>
      adminApi.updateEventReservationParticipants(event.id, userId, participants),
    onSuccess: () => { invalidateParticipants(); setEditingSpotsFor(null) },
  })

  const addRegisteredMutation = useMutation({
    mutationFn: () => adminApi.addRegisteredParticipantToEvent(event.id, selectedUserId, addParticipants, addComment || undefined),
    onSuccess: () => {
      invalidateParticipants()
      setShowAddForm(false)
      setSelectedUserId('')
      setAddParticipants(1)
      setAddComment('')
    },
  })

  const addGuestMutation = useMutation({
    mutationFn: () => adminApi.addGuestParticipantToEvent(event.id, guestNote, addParticipants),
    onSuccess: () => {
      invalidateParticipants()
      setShowAddForm(false)
      setGuestNote('')
      setAddParticipants(1)
    },
  })

  const deleteGuestMutation = useMutation({
    mutationFn: (guestId: string) => adminApi.deleteGuestParticipantFromEvent(event.id, guestId),
    onSuccess: () => { invalidateParticipants(); setConfirmDeleteGuestId(null) },
  })

  return (
    <div className={`bg-surface-900 rounded-lg border p-4 ${archived ? 'border-surface-800/50 opacity-60' : 'border-surface-800'}`}>
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1 flex-wrap">
            <span className="font-medium text-surface-100">{event.title}</span>
            <span className={`px-2 py-0.5 text-xs rounded ${getEventColorByType(event.eventType).bg} ${getEventColorByType(event.eventType).text}`}>
              {tc(`eventTypes.${event.eventType}`)}
            </span>
            {!event.active && (
              <span className="px-2 py-0.5 text-xs rounded bg-surface-700 text-surface-400">
                {t('events.inactive')}
              </span>
            )}
          </div>
          <div className="text-sm text-surface-400">
            {format(new Date(event.startDate), 'dd.MM.yyyy')}
            {event.startDate !== event.endDate && (
              <> - {format(new Date(event.endDate), 'dd.MM.yyyy')}</>
            )}
            {event.startTime && event.endTime ? (
              <span className="ml-2 inline-flex items-center gap-1">
                <Clock className="w-3 h-3" />
                {event.startTime.slice(0, 5)} - {event.endTime.slice(0, 5)}
              </span>
            ) : (
              <span className="ml-2 text-surface-500">{t('events.allDay')}</span>
            )}
          </div>
          {event.location && (
            <div className="text-sm text-surface-400 flex items-center gap-1 mt-0.5">
              <MapPin className="w-3 h-3" />
              {event.location}
            </div>
          )}
          {event.description && (
            <div className="text-sm text-surface-500 mt-1 line-clamp-2">
              {event.description}
            </div>
          )}
          <div className="flex items-center gap-3 mt-0.5 flex-wrap">
            {event.eventType === 'UNAVAILABLE' ? (
              <span className="text-sm text-slate-400">{tc('eventTypes.UNAVAILABLE')}</span>
            ) : (
              <>
                <span className="text-sm text-surface-500">
                  {t('events.participantsCount', { current: event.currentParticipants, max: event.maxParticipants })}
                </span>
                {event.maxParticipants - event.currentParticipants > 0 ? (
                  <span className="text-sm text-green-400">
                    {t('events.freeSpots', { count: event.maxParticipants - event.currentParticipants })}
                  </span>
                ) : (
                  <span className="text-sm text-rose-400">{t('events.noFreeSpots')}</span>
                )}
              </>
            )}
            {event.courseId && (
              <Link
                to={`/kursy/${event.courseId}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-xs text-primary-400 hover:text-primary-300 transition-colors"
                onClick={(e) => e.stopPropagation()}
              >
                <BookOpen className="w-3 h-3" />
                {t('events.viewCourse')}
              </Link>
            )}
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0 ml-2">
          <Button variant="ghost" size="sm" onClick={onEdit} title={t('events.edit')}>
            <Pencil className="w-4 h-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={onToggleActive} title={event.active ? t('events.deactivate') : t('events.activate')}>
            {event.active ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setShowDeleteConfirm(true)}>
            <Trash2 className="w-4 h-4 text-rose-400/80" />
          </Button>
        </div>
      </div>

      {/* Expandable participants section */}
      <button
        onClick={() => setShowParticipants(!showParticipants)}
        className="flex items-center gap-1.5 mt-3 text-sm text-surface-400 hover:text-surface-200 transition-colors"
      >
        {showParticipants ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
        <Users className="w-3.5 h-3.5" />
        {t('events.participantsLabel')}
        {participantsData && (
          <span className="text-surface-500">
            ({participantsData.participants.reduce((s, p) => s + p.participants, 0)
              + participantsData.guestParticipants.reduce((s, g) => s + g.participants, 0)})
          </span>
        )}
      </button>

      {showParticipants && (
        <div className="mt-2 ml-1 space-y-2">
          {participantsLoading ? (
            <div className="text-sm text-surface-500 py-2">{t('events.loading')}</div>
          ) : participantsData && (participantsData.participants.length > 0 || participantsData.guestParticipants.length > 0) ? (
            <div className="space-y-2">
              {participantsData.participants.map((p) => (
                <div key={p.userId} className="bg-surface-800/50 rounded-lg px-3 py-2 text-sm">
                  {confirmCancelParticipant === p.userId ? (
                    <div className="space-y-2">
                      <p className="text-xs text-rose-400">{t('events.confirmCancelParticipant')}</p>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="danger"
                          loading={cancelEventParticipantMutation.isPending}
                          onClick={() => cancelEventParticipantMutation.mutate(p.userId)}
                        >
                          {t('events.cancelParticipant')}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setConfirmCancelParticipant(null)}>
                          {t('events.cancel')}
                        </Button>
                      </div>
                    </div>
                  ) : editingSpotsFor === p.userId ? (
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <input
                          type="number"
                          min={1}
                          max={20}
                          value={editedSpots}
                          onChange={(e) => setEditedSpots(Number(e.target.value))}
                          className="w-16 text-sm bg-surface-700 border border-surface-600 rounded px-2 py-1 text-surface-100"
                        />
                        <Button
                          size="sm"
                          variant="primary"
                          loading={updateEventParticipantsMutation.isPending}
                          onClick={() => updateEventParticipantsMutation.mutate({ userId: p.userId, participants: editedSpots })}
                        >
                          {t('events.saveSpots')}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setEditingSpotsFor(null)}>
                          {t('events.cancelEdit')}
                        </Button>
                      </div>
                      {updateEventParticipantsMutation.isError && (
                        <p className="text-xs text-rose-400">{getErrorMessage(updateEventParticipantsMutation.error)}</p>
                      )}
                    </div>
                  ) : (
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="font-medium text-surface-200">{p.fullName}</div>
                        <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-surface-400 text-xs mt-0.5">
                          <span className="inline-flex items-center gap-1">
                            <Mail className="w-3 h-3" />
                            {p.email}
                          </span>
                          <span className="inline-flex items-center gap-1">
                            <Phone className="w-3 h-3" />
                            {p.phone}
                          </span>
                          {p.participants > 1 && (
                            <span>{t('events.spots', { count: p.participants })}</span>
                          )}
                        </div>
                        {p.comment && (
                          <div className="text-surface-500 text-xs mt-1">"{p.comment}"</div>
                        )}
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          onClick={() => { setEditingSpotsFor(p.userId); setEditedSpots(p.participants) }}
                          title={t('events.editSpots')}
                          className="p-1 text-surface-500 hover:text-primary-400 transition-colors"
                        >
                          <Pencil className="w-3.5 h-3.5" />
                        </button>
                        <button
                          onClick={() => setConfirmCancelParticipant(p.userId)}
                          title={t('events.cancelParticipant')}
                          className="p-1 text-surface-500 hover:text-rose-400 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              ))}

              {/* Guest reservations */}
              {participantsData.guestParticipants.map((g) => (
                <div key={g.id} className="bg-amber-500/5 border border-amber-500/20 rounded-lg px-3 py-2 text-sm">
                  {confirmDeleteGuestId === g.id ? (
                    <div className="space-y-2">
                      <p className="text-xs text-rose-400">{t('events.confirmCancelParticipant')}</p>
                      <div className="flex gap-2">
                        <Button
                          size="sm"
                          variant="danger"
                          loading={deleteGuestMutation.isPending}
                          onClick={() => deleteGuestMutation.mutate(g.id)}
                        >
                          {t('events.cancelParticipant')}
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => setConfirmDeleteGuestId(null)}>
                          {t('events.cancel')}
                        </Button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded">{t('slots.guest')}</span>
                          <span className="font-medium text-surface-200">{g.note}</span>
                        </div>
                        {g.participants > 1 && (
                          <span className="text-xs text-amber-400/80">{t('events.spots', { count: g.participants })}</span>
                        )}
                      </div>
                      <button
                        onClick={() => setConfirmDeleteGuestId(g.id)}
                        title={t('events.cancelParticipant')}
                        className="p-1 text-surface-500 hover:text-rose-400 transition-colors shrink-0"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-surface-500 py-2">{t('events.noParticipants')}</div>
          )}

          {/* Add participant */}
          {!showAddForm ? (
            <button
              onClick={() => setShowAddForm(true)}
              className="flex items-center gap-2 text-sm text-primary-400 hover:text-primary-300 transition-colors py-1"
            >
              <UserPlus className="w-4 h-4" />
              {t('slots.addParticipant')}
            </button>
          ) : (
            <div className="border border-surface-700 rounded-lg p-3 space-y-3">
              <div className="flex gap-2">
                <button
                  onClick={() => setAddMode('registered')}
                  className={`text-xs px-3 py-1 rounded-full transition-colors ${addMode === 'registered' ? 'bg-primary-500/20 text-primary-300' : 'text-surface-400 hover:text-surface-200'}`}
                >
                  {t('slots.addRegistered')}
                </button>
                <button
                  onClick={() => setAddMode('guest')}
                  className={`text-xs px-3 py-1 rounded-full transition-colors ${addMode === 'guest' ? 'bg-amber-500/20 text-amber-300' : 'text-surface-400 hover:text-surface-200'}`}
                >
                  {t('slots.addGuest')}
                </button>
              </div>

              {addMode === 'registered' ? (
                <div className="space-y-2">
                  <UserSearchSelect
                    users={(allUsers as User[] | undefined) ?? []}
                    value={selectedUserId}
                    onChange={(id) => { setSelectedUserId(id); addRegisteredMutation.reset() }}
                  />
                  {selectedUserId && participantsData?.participants.find(p => p.userId === selectedUserId) && (() => {
                    const existing = participantsData.participants.find(p => p.userId === selectedUserId)!
                    return (
                      <p className="text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 rounded px-2 py-1.5">
                        {t('events.alreadyRegistered', { count: existing.participants })}
                      </p>
                    )
                  })()}
                  <div className="flex items-center gap-2">
                    <label className="text-xs text-surface-400 shrink-0">{t('slots.spots')}:</label>
                    <input
                      type="number"
                      min={1}
                      max={20}
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
                    className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-2 text-surface-100 text-sm"
                  />
                  <div className="flex items-center gap-2">
                    <label className="text-xs text-surface-400 shrink-0">{t('slots.spots')}:</label>
                    <input
                      type="number"
                      min={1}
                      max={20}
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
      )}

      {/* Delete confirmation modal */}
      {showDeleteConfirm && participantsData && (
        <ConfirmDeleteEventModal
          isOpen={showDeleteConfirm}
          onClose={() => setShowDeleteConfirm(false)}
          eventTitle={event.title}
          archived={archived}
          participants={participantsData.participants}
          onConfirm={() => {
            onDelete()
            setShowDeleteConfirm(false)
          }}
        />
      )}
    </div>
  )
}

function ConfirmDeleteEventModal({
  isOpen,
  onClose,
  eventTitle,
  archived,
  participants,
  onConfirm,
}: {
  isOpen: boolean
  onClose: () => void
  eventTitle: string
  archived?: boolean
  participants: { userId: string; fullName: string; email: string }[]
  onConfirm: () => void
}) {
  const { t } = useTranslation('admin')
  const hasParticipants = participants.length > 0
  // Archived (past) events: deletion is a permanent archive cleanup — backend sends NO emails,
  // so don't show the "reservations will be cancelled / users notified" warning.
  const showActiveWarning = hasParticipants && !archived

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={showActiveWarning ? t('events.warningActiveReservations') : t('events.deleteTitle')}>
      <div className="space-y-4">
        <div className="text-sm text-surface-400">
          {t('events.eventLabel')}<span className="text-surface-200">{eventTitle}</span>
        </div>

        {hasParticipants ? (
          <>
            {showActiveWarning ? (
              <div className="flex items-start gap-3 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
                <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
                <div className="text-sm text-rose-300">
                  <p className="font-medium mb-1">{t('events.hasParticipants')}</p>
                  <p className="text-rose-400/80">
                    {t('events.deleteWarning')}
                  </p>
                </div>
              </div>
            ) : (
              <p className="text-surface-400 text-sm">
                {t('events.archivedDeleteWarning')}
              </p>
            )}

            <div>
              <h3 className="text-sm font-medium text-surface-300 mb-2">
                {t('events.registered', { count: participants.length })}
              </h3>
              <ul className="space-y-2 max-h-48 overflow-y-auto">
                {participants.map((p) => (
                  <li key={p.userId} className="bg-surface-800 rounded-lg p-3">
                    <div className="font-medium text-surface-100">{p.fullName}</div>
                    <div className="text-sm text-surface-400">{p.email}</div>
                  </li>
                ))}
              </ul>
            </div>
          </>
        ) : (
          <p className="text-surface-400 text-sm">
            {t('events.noRegistered')}
          </p>
        )}

        <div className="flex gap-3 pt-2">
          <Button variant="danger" className="flex-1" onClick={onConfirm}>
            {showActiveWarning ? t('events.deleteAndCancel') : t('events.deleteSimple')}
          </Button>
          <Button variant="ghost" onClick={onClose}>
            {t('events.cancel')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/* =============================== Edit Event Modal =============================== */

// Minimalny kształt wydarzenia potrzebny do edycji — spełnia go zarówno EventDetail (panel
// admina), jak i EventSummary (publiczny kalendarz, EventSignupModal).
export type EditableEvent = Pick<
  EventDetail,
  | 'id' | 'title' | 'description' | 'location' | 'eventType'
  | 'startDate' | 'endDate' | 'startTime' | 'endTime' | 'maxParticipants' | 'courseId'
>

export function EditEventModal({
  event,
  isOpen,
  onClose,
}: {
  event: EditableEvent | null
  isOpen: boolean
  onClose: () => void
}) {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const [allDay, setAllDay] = useState(!event?.startTime)
  const [courseId, setCourseId] = useState<string | null>(event?.courseId ?? null)
  const [form, setForm] = useState<CreateEventRequest>({
    title: event?.title ?? '',
    description: event?.description ?? '',
    location: event?.location ?? '',
    eventType: (event?.eventType as EventType) ?? 'TRAINING',
    startDate: event?.startDate ?? '',
    endDate: event?.endDate ?? '',
    maxParticipants: event?.maxParticipants ?? 4,
    startTime: event?.startTime?.slice(0, 5),
    endTime: event?.endTime?.slice(0, 5),
  })

  const { data: courses } = useQuery({
    queryKey: ['admin', 'courses'],
    queryFn: () => adminCoursesApi.getAll(),
    staleTime: 5 * 60 * 1000,
  })

  // Zaproszenia: baseline z serwera, lokalny override dopiero gdy admin coś zmieni
  const [editedInvited, setEditedInvited] = useState<InvitedUser[] | null>(null)
  const { data: invitesData } = useQuery({
    queryKey: ['admin', 'eventInvites', event?.id],
    queryFn: () => adminApi.getEventInvites(event!.id),
    enabled: !!event && isOpen,
  })
  const baselineInvited = invitesData ?? []
  const invited = editedInvited ?? baselineInvited
  const invitedKey = (list: InvitedUser[]) => list.map((u) => u.userId).sort().join(',')
  const invitedDirty = invitedKey(invited) !== invitedKey(baselineInvited)

  const queryClient = useQueryClient()

  const updateMutation = useMutation({
    mutationFn: (data: CreateEventRequest & { courseId?: string | null; removeCourse?: boolean; invitedUserIds?: string[] }) =>
      adminApi.updateEvent(event!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['courseEvents'] })
      queryClient.invalidateQueries({ queryKey: ['eventSummary', event?.id] })
      onClose()
    },
  })

  const isDirty = useDirty({ form, courseId, allDay }) || invitedDirty

  if (!event) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const payload: CreateEventRequest & { courseId?: string | null; removeCourse?: boolean; invitedUserIds?: string[] } = { ...form }
    if (allDay) {
      delete payload.startTime
      delete payload.endTime
    }
    if (courseId) {
      payload.courseId = courseId
    } else if (event.courseId) {
      payload.removeCourse = true
    }
    payload.invitedUserIds = form.eventType === 'UNAVAILABLE' ? [] : invited.map((u) => u.userId)
    updateMutation.mutate(payload)
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('events.editTitle')}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.selectCourse')}</label>
          <select
            value={courseId ?? ''}
            onChange={(e) => {
              const val = e.target.value || null
              setCourseId(val)
              if (val) {
                const selected = courses?.find(c => c.id === val)
                if (selected) setForm(f => ({ ...f, title: selected.title }))
              }
            }}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          >
            <option value="">{t('events.noCourse')}</option>
            {courses?.map((course) => (
              <option key={course.id} value={course.id}>{course.title}</option>
            ))}
          </select>
        </div>

        {!courseId && (
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('events.titleLabel')}</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
              required
            />
          </div>
        )}

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.typeLabel')}</label>
          <select
            value={form.eventType}
            onChange={(e) => {
              const eventType = e.target.value as EventType
              setForm({ ...form, eventType, maxParticipants: eventType === 'UNAVAILABLE' ? 0 : form.maxParticipants })
            }}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          >
            <option value="COURSE">{tc('eventTypes.COURSE')}</option>
            <option value="TRAINING">{tc('eventTypes.TRAINING')}</option>
            <option value="WORKSHOP">{tc('eventTypes.WORKSHOP')}</option>
            <option value="CONTACT_DAY">{tc('eventTypes.CONTACT_DAY')}</option>
            <option value="UNAVAILABLE">{tc('eventTypes.UNAVAILABLE')}</option>
          </select>
        </div>

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.descriptionLabel')}</label>
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 h-64"
          />
        </div>

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.locationLabel')}</label>
          <input
            type="text"
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            placeholder={t('events.locationPlaceholder')}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('events.dateFrom')}</label>
            <input
              type="date"
              value={form.startDate}
              onChange={(e) => {
                const newStart = e.target.value
                setForm({ ...form, startDate: newStart, endDate: form.endDate < newStart ? newStart : form.endDate })
                e.target.blur()
              }}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
            />
          </div>
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('events.dateTo')}</label>
            <input
              type="date"
              min={form.startDate}
              value={form.endDate}
              onChange={(e) => {
                setForm({ ...form, endDate: e.target.value })
                e.target.blur()
              }}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
            />
          </div>
        </div>

        <div>
          <label className="flex items-center gap-2 cursor-pointer mb-2">
            <input
              type="checkbox"
              checked={allDay}
              onChange={(e) => {
                setAllDay(e.target.checked)
                if (e.target.checked) {
                  setForm({ ...form, startTime: undefined, endTime: undefined })
                } else {
                  setForm({ ...form, startTime: '10:00', endTime: '17:00' })
                }
              }}
              className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-primary-500 focus:ring-primary-500"
            />
            <span className="text-sm text-surface-300">{t('events.allDayCheckbox')}</span>
          </label>

          {!allDay && (
            <div className="grid grid-cols-2 gap-4">
              <TimeScrollPicker
                label={t('events.from')}
                value={form.startTime || '10:00'}
                onChange={(v) => setForm({ ...form, startTime: v })}
              />
              <TimeScrollPicker
                label={t('events.to')}
                value={form.endTime || '17:00'}
                onChange={(v) => setForm({ ...form, endTime: v })}
              />
            </div>
          )}
        </div>

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.maxParticipantsLabel')}</label>
          <input
            type="number"
            min={0}
            disabled={form.eventType === 'UNAVAILABLE'}
            value={form.eventType === 'UNAVAILABLE' ? 0 : form.maxParticipants}
            onChange={(e) => setForm({ ...form, maxParticipants: parseInt(e.target.value) })}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 disabled:opacity-50"
          />
        </div>

        {form.eventType !== 'UNAVAILABLE' && (
          <InvitedUsersPicker value={invited} onChange={setEditedInvited} maxSeats={form.maxParticipants} />
        )}

        {form.eventType !== 'UNAVAILABLE' && !invitedDirty && event && (
          <InviteNotifySection target={{ type: 'event', eventId: event.id }} invites={baselineInvited} />
        )}

        <div className="flex gap-3 pt-4">
          <Button type="submit" loading={updateMutation.isPending} disabled={!isDirty} className="flex-1">
            {t('events.saveChanges')}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('events.cancel')}
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

/* =============================== Create Event Modal =============================== */

export function CreateEventModal({
  isOpen,
  onClose,
  initial,
}: {
  isOpen: boolean
  onClose: () => void
  /** Prefill z propozycji terminu: daty, godziny, miejsca, kurs, zaproszony proponujący + link do propozycji. */
  initial?: {
    startDate?: string
    endDate?: string
    startTime?: string
    endTime?: string
    maxParticipants?: number
    courseId?: string
    courseTitle?: string
    invited?: InvitedUser[]
    trainingRequestId?: string
  }
}) {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const [allDay, setAllDay] = useState(!(initial?.startTime && initial?.endTime))
  const [courseId, setCourseId] = useState<string | undefined>(initial?.courseId)
  const [form, setForm] = useState<CreateEventRequest>({
    title: initial?.courseTitle ?? '',
    description: '',
    location: '',
    eventType: 'TRAINING',
    startDate: initial?.startDate ?? format(new Date(), 'yyyy-MM-dd'),
    endDate: initial?.endDate ?? initial?.startDate ?? format(new Date(), 'yyyy-MM-dd'),
    maxParticipants: initial?.maxParticipants ?? 4,
    startTime: initial?.startTime,
    endTime: initial?.endTime,
  })

  const [invited, setInvited] = useState<InvitedUser[]>(initial?.invited ?? [])

  const { data: courses } = useQuery({
    queryKey: ['admin', 'courses'],
    queryFn: () => adminCoursesApi.getAll(),
    staleTime: 5 * 60 * 1000,
  })

  const queryClient = useQueryClient()

  const createMutation = useMutation({
    mutationFn: adminApi.createEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'trainingRequests'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'notifications'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      queryClient.invalidateQueries({ queryKey: ['courseEvents'] })
      onClose()
      setAllDay(true)
      setCourseId(undefined)
      setInvited([])
      setForm({
        title: '',
        description: '',
        location: '',
        eventType: 'TRAINING',
        startDate: format(new Date(), 'yyyy-MM-dd'),
        endDate: format(new Date(), 'yyyy-MM-dd'),
        maxParticipants: 4,
      })
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const payload: CreateEventRequest = { ...form }
    if (allDay) {
      delete payload.startTime
      delete payload.endTime
    }
    if (courseId) payload.courseId = courseId
    payload.invitedUserIds = form.eventType === 'UNAVAILABLE' ? [] : invited.map((u) => u.userId)
    payload.trainingRequestId = initial?.trainingRequestId
    createMutation.mutate(payload)
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('events.addTitle')}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.selectCourse')}</label>
          <select
            value={courseId ?? ''}
            onChange={(e) => {
              const val = e.target.value || undefined
              setCourseId(val)
              if (val) {
                const selected = courses?.find(c => c.id === val)
                if (selected) setForm(f => ({ ...f, title: selected.title }))
              } else {
                setForm(f => ({ ...f, title: '' }))
              }
            }}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          >
            <option value="">{t('events.noCourse')}</option>
            {courses?.map((course) => (
              <option key={course.id} value={course.id}>{course.title}</option>
            ))}
          </select>
        </div>

        {!courseId && (
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('events.titleLabel')}</label>
            <input
              type="text"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
              required
            />
          </div>
        )}

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.typeLabel')}</label>
          <select
            value={form.eventType}
            onChange={(e) => {
              const eventType = e.target.value as EventType
              setForm({ ...form, eventType, maxParticipants: eventType === 'UNAVAILABLE' ? 0 : form.maxParticipants })
            }}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          >
            <option value="COURSE">{tc('eventTypes.COURSE')}</option>
            <option value="TRAINING">{tc('eventTypes.TRAINING')}</option>
            <option value="WORKSHOP">{tc('eventTypes.WORKSHOP')}</option>
            <option value="CONTACT_DAY">{tc('eventTypes.CONTACT_DAY')}</option>
            <option value="UNAVAILABLE">{tc('eventTypes.UNAVAILABLE')}</option>
          </select>
        </div>

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.descriptionLabel')}</label>
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 h-64"
          />
        </div>

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.locationLabel')}</label>
          <input
            type="text"
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            placeholder={t('events.locationPlaceholder')}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('events.dateFrom')}</label>
            <input
              type="date"
              value={form.startDate}
              onChange={(e) => {
                const newStart = e.target.value
                setForm({ ...form, startDate: newStart, endDate: form.endDate < newStart ? newStart : form.endDate })
                e.target.blur()
              }}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
            />
          </div>
          <div>
            <label className="block text-sm text-surface-400 mb-1">{t('events.dateTo')}</label>
            <input
              type="date"
              min={form.startDate}
              value={form.endDate}
              onChange={(e) => {
                setForm({ ...form, endDate: e.target.value })
                e.target.blur()
              }}
              className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100"
            />
          </div>
        </div>

        <div>
          <label className="flex items-center gap-2 cursor-pointer mb-2">
            <input
              type="checkbox"
              checked={allDay}
              onChange={(e) => {
                setAllDay(e.target.checked)
                if (e.target.checked) {
                  setForm({ ...form, startTime: undefined, endTime: undefined })
                } else {
                  setForm({ ...form, startTime: '10:00', endTime: '17:00' })
                }
              }}
              className="w-4 h-4 rounded border-surface-700 bg-surface-800 text-primary-500 focus:ring-primary-500"
            />
            <span className="text-sm text-surface-300">{t('events.allDayCheckbox')}</span>
          </label>

          {!allDay && (
            <div className="grid grid-cols-2 gap-4">
              <TimeScrollPicker
                label={t('events.from')}
                value={form.startTime || '10:00'}
                onChange={(v) => setForm({ ...form, startTime: v })}
              />
              <TimeScrollPicker
                label={t('events.to')}
                value={form.endTime || '17:00'}
                onChange={(v) => setForm({ ...form, endTime: v })}
              />
            </div>
          )}
        </div>

        <div>
          <label className="block text-sm text-surface-400 mb-1">{t('events.maxParticipantsLabel')}</label>
          <input
            type="number"
            min={0}
            disabled={form.eventType === 'UNAVAILABLE'}
            value={form.eventType === 'UNAVAILABLE' ? 0 : form.maxParticipants}
            onChange={(e) => setForm({ ...form, maxParticipants: parseInt(e.target.value) })}
            className="w-full bg-surface-800 border border-surface-700 rounded-lg px-4 py-2 text-surface-100 disabled:opacity-50"
          />
        </div>

        {form.eventType !== 'UNAVAILABLE' && (
          <InvitedUsersPicker value={invited} onChange={setInvited} maxSeats={form.maxParticipants} />
        )}

        <div className="flex gap-3 pt-4">
          <Button type="submit" loading={createMutation.isPending} className="flex-1">
            {t('events.createEvent')}
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('events.cancel')}
          </Button>
        </div>

        {createMutation.isError && (
          <p className="text-sm text-rose-400/80">
            {getErrorMessage(createMutation.error)}
          </p>
        )}
      </form>
    </Modal>
  )
}
