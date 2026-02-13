import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Plus, Trash2, Eye, EyeOff, Clock, Pencil, ChevronDown, ChevronRight, MapPin, Users, Mail, Phone, AlertTriangle } from 'lucide-react'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TimeScrollPicker } from '../../components/ui/TimeScrollPicker'
import type { CreateEventRequest, EventDetail, EventType } from '../../types'

const EVENT_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Kurs',
  TRAINING: 'Trening',
  WORKSHOP: 'Warsztat',
}

export function AdminEventsPanel() {
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [editingEvent, setEditingEvent] = useState<EventDetail | null>(null)
  const [showArchive, setShowArchive] = useState(false)
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
          Dodaj wydarzenie
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
            <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
              Brak nadchodzących wydarzeń
            </div>
          ) : (
            <div className="space-y-3">
              <p className="text-sm text-dark-400">Nadchodzące ({upcoming.length})</p>
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
          {archive.length > 0 && (
            <div>
              <button
                onClick={() => setShowArchive(!showArchive)}
                className="flex items-center gap-2 text-sm text-dark-500 hover:text-dark-300 transition-colors mb-3"
              >
                {showArchive ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                Archiwum ({archive.length})
              </button>

              {showArchive && (
                <div className="space-y-3 max-h-96 overflow-y-auto pr-1">
                  {archive.map((event) => (
                    <EventCard
                      key={event.id}
                      event={event}
                      archived
                      onEdit={() => setEditingEvent(event)}
                      onToggleActive={() =>
                        updateMutation.mutate({ eventId: event.id, data: { active: !event.active } })
                      }
                      onDelete={() => {
                        if (window.confirm('Czy na pewno chcesz usunąć to wydarzenie?')) {
                          deleteMutation.mutate(event.id)
                        }
                      }}
                    />
                  ))}
                </div>
              )}
            </div>
          )}
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
  const [showParticipants, setShowParticipants] = useState(false)
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const { data: participantsData, isLoading: participantsLoading } = useQuery({
    queryKey: ['admin', 'events', event.id, 'participants'],
    queryFn: () => adminApi.getEventParticipants(event.id),
    enabled: showParticipants || showDeleteConfirm,
  })

  return (
    <div className={`bg-dark-900 rounded-lg border p-4 ${archived ? 'border-dark-800/50 opacity-60' : 'border-dark-800'}`}>
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1 flex-wrap">
            <span className="font-medium text-dark-100">{event.title}</span>
            <span className="px-2 py-0.5 text-xs rounded bg-primary-500/20 text-primary-400">
              {EVENT_TYPE_LABELS[event.eventType] || event.eventType}
            </span>
            {!event.active && (
              <span className="px-2 py-0.5 text-xs rounded bg-dark-700 text-dark-400">
                Nieaktywne
              </span>
            )}
          </div>
          <div className="text-sm text-dark-400">
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
              <span className="ml-2 text-dark-500">Cały dzień</span>
            )}
          </div>
          {event.location && (
            <div className="text-sm text-dark-400 flex items-center gap-1 mt-0.5">
              <MapPin className="w-3 h-3" />
              {event.location}
            </div>
          )}
          {event.description && (
            <div className="text-sm text-dark-500 mt-1 line-clamp-2">
              {event.description}
            </div>
          )}
          <div className="text-sm text-dark-500 mt-0.5">
            Maks. {event.maxParticipants} uczestników
          </div>
        </div>

        <div className="flex items-center gap-1 shrink-0 ml-2">
          <Button variant="ghost" size="sm" onClick={onEdit} title="Edytuj">
            <Pencil className="w-4 h-4" />
          </Button>
          <Button variant="ghost" size="sm" onClick={onToggleActive} title={event.active ? 'Dezaktywuj' : 'Aktywuj'}>
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
        className="flex items-center gap-1.5 mt-3 text-sm text-dark-400 hover:text-dark-200 transition-colors"
      >
        {showParticipants ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
        <Users className="w-3.5 h-3.5" />
        Uczestnicy
        {participantsData && (
          <span className="text-dark-500">({participantsData.participants.length})</span>
        )}
      </button>

      {showParticipants && (
        <div className="mt-2 ml-1">
          {participantsLoading ? (
            <div className="text-sm text-dark-500 py-2">Ładowanie...</div>
          ) : participantsData && participantsData.participants.length > 0 ? (
            <div className="space-y-2">
              {participantsData.participants.map((p) => (
                <div key={p.userId} className="bg-dark-800/50 rounded-lg px-3 py-2 text-sm">
                  <div className="font-medium text-dark-200">{p.fullName}</div>
                  <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-dark-400 text-xs mt-0.5">
                    <span className="inline-flex items-center gap-1">
                      <Mail className="w-3 h-3" />
                      {p.email}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <Phone className="w-3 h-3" />
                      {p.phone}
                    </span>
                    {p.participants > 1 && (
                      <span>{p.participants} miejsca</span>
                    )}
                  </div>
                  {p.comment && (
                    <div className="text-dark-500 text-xs mt-1">"{p.comment}"</div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-dark-500 py-2">Brak zapisanych uczestników</div>
          )}
        </div>
      )}

      {/* Delete confirmation modal */}
      {showDeleteConfirm && participantsData && (
        <ConfirmDeleteEventModal
          isOpen={showDeleteConfirm}
          onClose={() => setShowDeleteConfirm(false)}
          eventTitle={event.title}
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
  participants,
  onConfirm,
}: {
  isOpen: boolean
  onClose: () => void
  eventTitle: string
  participants: { userId: string; fullName: string; email: string }[]
  onConfirm: () => void
}) {
  const hasParticipants = participants.length > 0

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={hasParticipants ? 'Uwaga - aktywne rezerwacje!' : 'Usuń wydarzenie'}>
      <div className="space-y-4">
        <div className="text-sm text-dark-400">
          Wydarzenie: <span className="text-dark-200">{eventTitle}</span>
        </div>

        {hasParticipants ? (
          <>
            <div className="flex items-start gap-3 p-3 bg-rose-500/10 border border-rose-500/20 rounded-lg">
              <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
              <div className="text-sm text-rose-300">
                <p className="font-medium mb-1">To wydarzenie ma zapisanych uczestników!</p>
                <p className="text-rose-400/80">
                  Usunięcie wydarzenia spowoduje anulowanie wszystkich rezerwacji i powiadomienie użytkowników e-mailem.
                </p>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-medium text-dark-300 mb-2">
                Zapisani ({participants.length})
              </h3>
              <ul className="space-y-2 max-h-48 overflow-y-auto">
                {participants.map((p) => (
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
            Brak zapisanych osób na to wydarzenie.
          </p>
        )}

        <div className="flex gap-3 pt-2">
          <Button variant="danger" className="flex-1" onClick={onConfirm}>
            {hasParticipants ? 'Usuń i anuluj rezerwacje' : 'Usuń wydarzenie'}
          </Button>
          <Button variant="ghost" onClick={onClose}>
            Anuluj
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/* =============================== Edit Event Modal =============================== */

function EditEventModal({
  event,
  isOpen,
  onClose,
}: {
  event: EventDetail | null
  isOpen: boolean
  onClose: () => void
}) {
  const [allDay, setAllDay] = useState(!event?.startTime)
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

  const queryClient = useQueryClient()

  const updateMutation = useMutation({
    mutationFn: (data: CreateEventRequest) =>
      adminApi.updateEvent(event!.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      onClose()
    },
  })

  if (!event) return null

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const payload: CreateEventRequest = { ...form }
    if (allDay) {
      delete payload.startTime
      delete payload.endTime
    }
    updateMutation.mutate(payload)
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Edytuj wydarzenie">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-dark-400 mb-1">Tytuł</label>
          <input
            type="text"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
            required
          />
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Typ</label>
          <select
            value={form.eventType}
            onChange={(e) => setForm({ ...form, eventType: e.target.value as EventType })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          >
            <option value="COURSE">Kurs</option>
            <option value="TRAINING">Trening</option>
            <option value="WORKSHOP">Warsztat</option>
          </select>
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Opis</label>
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 h-24"
          />
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Miejsce</label>
          <input
            type="text"
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            placeholder="np. Ścianka wspinaczkowa XYZ"
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-dark-400 mb-1">Data od</label>
            <input
              type="date"
              value={form.startDate}
              onChange={(e) => {
                setForm({ ...form, startDate: e.target.value })
                e.target.blur()
              }}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-400 mb-1">Data do</label>
            <input
              type="date"
              value={form.endDate}
              onChange={(e) => {
                setForm({ ...form, endDate: e.target.value })
                e.target.blur()
              }}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
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
              className="w-4 h-4 rounded border-dark-700 bg-dark-800 text-primary-500 focus:ring-primary-500"
            />
            <span className="text-sm text-dark-300">Cały dzień</span>
          </label>

          {!allDay && (
            <div className="grid grid-cols-2 gap-4">
              <TimeScrollPicker
                label="Od"
                value={form.startTime || '10:00'}
                onChange={(v) => setForm({ ...form, startTime: v })}
              />
              <TimeScrollPicker
                label="Do"
                value={form.endTime || '17:00'}
                onChange={(v) => setForm({ ...form, endTime: v })}
              />
            </div>
          )}
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Maks. uczestników</label>
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
            Zapisz zmiany
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            Anuluj
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

function CreateEventModal({
  isOpen,
  onClose,
}: {
  isOpen: boolean
  onClose: () => void
}) {
  const [allDay, setAllDay] = useState(true)
  const [form, setForm] = useState<CreateEventRequest>({
    title: '',
    description: '',
    location: '',
    eventType: 'TRAINING',
    startDate: format(new Date(), 'yyyy-MM-dd'),
    endDate: format(new Date(), 'yyyy-MM-dd'),
    maxParticipants: 4,
  })

  const queryClient = useQueryClient()

  const createMutation = useMutation({
    mutationFn: adminApi.createEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'events'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
      onClose()
      setAllDay(true)
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
    createMutation.mutate(payload)
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Dodaj nowe wydarzenie">
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-sm text-dark-400 mb-1">Tytuł</label>
          <input
            type="text"
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
            required
          />
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Typ</label>
          <select
            value={form.eventType}
            onChange={(e) => setForm({ ...form, eventType: e.target.value as EventType })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          >
            <option value="COURSE">Kurs</option>
            <option value="TRAINING">Trening</option>
            <option value="WORKSHOP">Warsztat</option>
          </select>
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Opis</label>
          <textarea
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100 h-24"
          />
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Miejsce</label>
          <input
            type="text"
            value={form.location}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            placeholder="np. Ścianka wspinaczkowa XYZ"
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm text-dark-400 mb-1">Data od</label>
            <input
              type="date"
              value={form.startDate}
              onChange={(e) => {
                setForm({ ...form, startDate: e.target.value })
                e.target.blur()
              }}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-400 mb-1">Data do</label>
            <input
              type="date"
              value={form.endDate}
              onChange={(e) => {
                setForm({ ...form, endDate: e.target.value })
                e.target.blur()
              }}
              className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
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
              className="w-4 h-4 rounded border-dark-700 bg-dark-800 text-primary-500 focus:ring-primary-500"
            />
            <span className="text-sm text-dark-300">Cały dzień</span>
          </label>

          {!allDay && (
            <div className="grid grid-cols-2 gap-4">
              <TimeScrollPicker
                label="Od"
                value={form.startTime || '10:00'}
                onChange={(v) => setForm({ ...form, startTime: v })}
              />
              <TimeScrollPicker
                label="Do"
                value={form.endTime || '17:00'}
                onChange={(v) => setForm({ ...form, endTime: v })}
              />
            </div>
          )}
        </div>

        <div>
          <label className="block text-sm text-dark-400 mb-1">Maks. uczestników</label>
          <input
            type="number"
            min={1}
            value={form.maxParticipants}
            onChange={(e) => setForm({ ...form, maxParticipants: parseInt(e.target.value) })}
            className="w-full bg-dark-800 border border-dark-700 rounded-lg px-4 py-2 text-dark-100"
          />
        </div>

        <div className="flex gap-3 pt-4">
          <Button type="submit" loading={createMutation.isPending} className="flex-1">
            Utwórz wydarzenie
          </Button>
          <Button type="button" variant="ghost" onClick={onClose}>
            Anuluj
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
