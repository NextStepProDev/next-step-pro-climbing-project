import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { ChevronDown, ChevronRight, Users, Mail, Phone } from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { getErrorMessage } from '../../utils/errors'
import type { ReservationAdmin } from '../../types'

function isMultiDayEvent(r: ReservationAdmin) {
  return r.eventStartDate && r.eventEndDate && r.eventStartDate !== r.eventEndDate
}

interface EventGroup {
  type: 'event'
  key: string
  title: string
  eventStartDate: string
  eventEndDate: string
  reservations: ReservationAdmin[]
}

interface SlotEntry {
  type: 'slot'
  reservation: ReservationAdmin
}

type GroupItem = EventGroup | SlotEntry

function groupReservations(reservations: ReservationAdmin[]) {
  const eventMap = new Map<string, EventGroup>()
  const slotItems: SlotEntry[] = []

  for (const r of reservations) {
    if (isMultiDayEvent(r)) {
      const eventKey = `${r.title}-${r.eventStartDate}-${r.eventEndDate}`
      const existing = eventMap.get(eventKey)
      if (existing) {
        if (!existing.reservations.some(er => er.userEmail === r.userEmail)) {
          existing.reservations.push(r)
        }
      } else {
        eventMap.set(eventKey, {
          type: 'event',
          key: eventKey,
          title: r.title!,
          eventStartDate: r.eventStartDate!,
          eventEndDate: r.eventEndDate!,
          reservations: [r],
        })
      }
    } else {
      slotItems.push({ type: 'slot', reservation: r })
    }
  }

  const grouped = new Map<string, GroupItem[]>()

  for (const eg of eventMap.values()) {
    const groupKey = `${eg.eventStartDate}:${eg.eventEndDate}`
    if (!grouped.has(groupKey)) grouped.set(groupKey, [])
    grouped.get(groupKey)!.push(eg)
  }

  for (const si of slotItems) {
    const groupDate = si.reservation.date
    if (!grouped.has(groupDate)) grouped.set(groupDate, [])
    grouped.get(groupDate)!.push(si)
  }

  return { grouped, eventMap, slotItems }
}

export function AdminReservationsPanel() {
  const [showArchive, setShowArchive] = useState(false)

  const { data: reservations, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'reservations', 'upcoming'],
    queryFn: () => adminApi.getUpcomingReservations(),
  })

  const { data: pastReservations, isLoading: pastLoading, isError: pastError, error: pastErrorObj } = useQuery({
    queryKey: ['admin', 'reservations', 'past'],
    queryFn: () => adminApi.getPastReservations(),
    enabled: showArchive,
  })

  if (isLoading) return <LoadingSpinner />

  if (isError) return <QueryError error={error} onRetry={() => refetch()} />

  const hasUpcoming = reservations && reservations.length > 0
  const { grouped, eventMap, slotItems } = groupReservations(reservations ?? [])
  const sortedDates = [...grouped.keys()].sort()
  const totalCount = slotItems.length + eventMap.size

  return (
    <div className="space-y-6">
      {/* Upcoming */}
      {!hasUpcoming ? (
        <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
          Brak nadchodzących rezerwacji
        </div>
      ) : (
        <>
          <p className="text-sm text-dark-400">
            Wszystkie nadchodzące rezerwacje ({totalCount})
          </p>
          <ReservationList sortedDates={sortedDates} grouped={grouped} />
        </>
      )}

      {/* Archive */}
      <div>
        <button
          onClick={() => setShowArchive(!showArchive)}
          className="flex items-center gap-2 text-sm text-dark-500 hover:text-dark-300 transition-colors mb-3"
        >
          {showArchive ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
          Archiwum
        </button>

        {showArchive && (
          <div>
            {pastLoading && (
              <div className="flex justify-center py-6">
                <LoadingSpinner />
              </div>
            )}
            {pastError && (
              <div className="p-4 bg-rose-500/5 border border-rose-500/15 rounded-lg text-rose-400/80">
                {getErrorMessage(pastErrorObj)}
              </div>
            )}
            {pastReservations && pastReservations.length === 0 && (
              <p className="text-dark-500 text-sm">Brak minionych rezerwacji.</p>
            )}
            {pastReservations && pastReservations.length > 0 && (
              <PastReservationList reservations={pastReservations} />
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function PastReservationList({ reservations }: { reservations: ReservationAdmin[] }) {
  const { grouped, eventMap, slotItems } = groupReservations(reservations)
  const sortedDates = [...grouped.keys()].sort().reverse()
  const totalCount = slotItems.length + eventMap.size

  return (
    <div className="space-y-6 opacity-60">
      <p className="text-sm text-dark-400">
        Minione rezerwacje ({totalCount})
      </p>
      <ReservationList sortedDates={sortedDates} grouped={grouped} />
    </div>
  )
}

function ReservationList({ sortedDates, grouped }: { sortedDates: string[]; grouped: Map<string, GroupItem[]> }) {
  return (
    <>
      {sortedDates.map((date) => {
        const items = grouped.get(date)!
        const hasEvent = items.some(i => i.type === 'event')
        const firstEvent = items.find((i): i is EventGroup => i.type === 'event')

        return (
          <div key={date}>
            <h3 className="text-sm font-semibold text-primary-400 mb-3 capitalize">
              {hasEvent && firstEvent
                ? `${format(new Date(firstEvent.eventStartDate), 'dd.MM')} - ${format(new Date(firstEvent.eventEndDate), 'dd.MM.yyyy')}`
                : format(new Date(date), 'EEEE, d MMMM yyyy', { locale: pl })
              }
            </h3>
            <div className="space-y-2">
              {items.map((item) =>
                item.type === 'event' ? (
                  <EventReservationCard key={item.key} group={item} />
                ) : (
                  <SlotReservationCard key={item.reservation.id} r={item.reservation} />
                )
              )}
            </div>
          </div>
        )
      })}
    </>
  )
}

function EventReservationCard({ group }: { group: EventGroup }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="bg-dark-900 rounded-lg border border-dark-800 p-4">
      <div className="flex items-center gap-3 flex-wrap">
        <span className="font-medium text-dark-100">{group.title}</span>
        <span className="text-xs bg-primary-500/10 text-primary-400 px-2 py-0.5 rounded">
          {format(new Date(group.eventStartDate), 'dd.MM')} - {format(new Date(group.eventEndDate), 'dd.MM.yyyy')}
        </span>
      </div>

      <button
        onClick={() => setExpanded(!expanded)}
        className="flex items-center gap-1.5 mt-2 text-sm text-dark-400 hover:text-dark-200 transition-colors"
      >
        {expanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
        <Users className="w-3.5 h-3.5" />
        Uczestnicy ({group.reservations.length})
      </button>

      {expanded && (
        <div className="mt-2 ml-1 space-y-2">
          {group.reservations.map((r) => (
            <div key={r.id} className="bg-dark-800/50 rounded-lg px-3 py-2 text-sm">
              <div className="font-medium text-dark-200">{r.userFullName}</div>
              <div className="flex flex-wrap gap-x-4 gap-y-0.5 text-dark-400 text-xs mt-0.5">
                <span className="inline-flex items-center gap-1">
                  <Mail className="w-3 h-3" />
                  {r.userEmail}
                </span>
                <span className="inline-flex items-center gap-1">
                  <Phone className="w-3 h-3" />
                  {r.userPhone}
                </span>
                {r.participants > 1 && (
                  <span>{r.participants} miejsca</span>
                )}
              </div>
              {r.comment && (
                <div className="text-dark-500 text-xs mt-1">"{r.comment}"</div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

function SlotReservationCard({ r }: { r: ReservationAdmin }) {
  return (
    <div className="bg-dark-900 rounded-lg border border-dark-800 p-4 flex items-start justify-between gap-4">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-3 flex-wrap">
          <span className="font-medium text-dark-100">{r.userFullName}</span>
          {r.title && (
            <span className="text-xs bg-primary-500/10 text-primary-400 px-2 py-0.5 rounded">
              {r.title}
            </span>
          )}
        </div>
        <div className="text-sm text-dark-400 mt-1">
          {r.userEmail} | {r.userPhone}
        </div>
        {r.participants > 1 && (
          <div className="text-sm text-primary-400 mt-1">
            {r.participants} miejsca
          </div>
        )}
        {r.comment && (
          <div className="text-sm text-amber-400 mt-1">"{r.comment}"</div>
        )}
      </div>
      <div className="text-right shrink-0">
        <div className="text-dark-200 font-medium">
          {r.startTime.slice(0, 5)} - {r.endTime.slice(0, 5)}
        </div>
      </div>
    </div>
  )
}
