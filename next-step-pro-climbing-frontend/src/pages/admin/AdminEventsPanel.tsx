import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Plus, Trash2, Eye, EyeOff, Clock } from 'lucide-react'
import { adminApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { TimeScrollPicker } from '../../components/ui/TimeScrollPicker'
import type { CreateEventRequest, EventType } from '../../types'

export function AdminEventsPanel() {
  const [showCreateModal, setShowCreateModal] = useState(false)
  const queryClient = useQueryClient()

  const { data: events, isLoading } = useQuery({
    queryKey: ['admin', 'events'],
    queryFn: adminApi.getAllEvents,
  })

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteEvent,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'events'] }),
  })

  const updateMutation = useMutation({
    mutationFn: ({ eventId, data }: { eventId: string; data: { active?: boolean } }) =>
      adminApi.updateEvent(eventId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'events'] }),
  })

  const eventTypeLabels = {
    COURSE: 'Kurs',
    TRAINING: 'Trening',
    WORKSHOP: 'Warsztat',
  }

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
      ) : (
        <div className="space-y-4">
          {events?.length === 0 ? (
            <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
              Brak wydarzeń
            </div>
          ) : (
            events?.map((event) => (
              <div
                key={event.id}
                className="bg-dark-900 rounded-lg border border-dark-800 p-4"
              >
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-medium text-dark-100">{event.title}</span>
                      <span className="px-2 py-0.5 text-xs rounded bg-primary-500/20 text-primary-400">
                        {eventTypeLabels[event.eventType as EventType]}
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
                      <div className="text-sm text-dark-400">
                        {event.location}
                      </div>
                    )}
                    <div className="text-sm text-dark-500">
                      Maks. {event.maxParticipants} uczestników
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() =>
                        updateMutation.mutate({
                          eventId: event.id,
                          data: { active: !event.active },
                        })
                      }
                      title={event.active ? 'Dezaktywuj' : 'Aktywuj'}
                    >
                      {event.active ? (
                        <EyeOff className="w-4 h-4" />
                      ) : (
                        <Eye className="w-4 h-4" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => {
                        if (window.confirm('Czy na pewno chcesz usunąć to wydarzenie?')) {
                          deleteMutation.mutate(event.id)
                        }
                      }}
                    >
                      <Trash2 className="w-4 h-4 text-rose-400/80" />
                    </Button>
                  </div>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      <CreateEventModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
      />
    </div>
  )
}

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
      <form
        onSubmit={handleSubmit}
        className="space-y-4"
      >
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

        {/* Time section */}
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
