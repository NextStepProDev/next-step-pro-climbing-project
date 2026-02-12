import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import type { ReservationAdmin } from '../../types'

function isMultiDayEvent(r: ReservationAdmin) {
  return r.eventStartDate && r.eventEndDate && r.eventStartDate !== r.eventEndDate
}

export function AdminReservationsPanel() {
  const { data: reservations, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'reservations', 'upcoming'],
    queryFn: () => adminApi.getUpcomingReservations(),
  })

  if (isLoading) return <LoadingSpinner />

  if (isError) return <QueryError error={error} onRetry={() => refetch()} />

  if (!reservations || reservations.length === 0) {
    return (
      <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
        Brak nadchodzących rezerwacji
      </div>
    )
  }

  // Deduplicate multi-day event reservations (one per user per event)
  const seen = new Set<string>()
  const deduped = reservations.filter(r => {
    if (isMultiDayEvent(r)) {
      const key = `${r.userEmail}-${r.title}`
      if (seen.has(key)) return false
      seen.add(key)
    }
    return true
  })

  // Group: multi-day events by eventStartDate, standalone slots by date
  const grouped = deduped.reduce<Record<string, ReservationAdmin[]>>((acc, r) => {
    const groupDate = isMultiDayEvent(r) ? r.eventStartDate! : r.date
    if (!acc[groupDate]) acc[groupDate] = []
    acc[groupDate].push(r)
    return acc
  }, {})

  return (
    <div className="space-y-6">
      <p className="text-sm text-dark-400">
        Wszystkie nadchodzące rezerwacje ({deduped.length})
      </p>

      {Object.entries(grouped).sort(([a], [b]) => a.localeCompare(b)).map(([date, dateReservations]) => {
        const firstMultiDay = dateReservations.find(isMultiDayEvent)
        const isEventGroup = !!firstMultiDay

        return (
          <div key={date}>
            <h3 className="text-sm font-semibold text-primary-400 mb-3 capitalize">
              {isEventGroup
                ? `${format(new Date(firstMultiDay.eventStartDate!), 'dd.MM')} - ${format(new Date(firstMultiDay.eventEndDate!), 'dd.MM.yyyy')}`
                : format(new Date(date), 'EEEE, d MMMM yyyy', { locale: pl })
              }
            </h3>
            <div className="space-y-2">
              {dateReservations.map((r) => (
                <div
                  key={r.id}
                  className="bg-dark-900 rounded-lg border border-dark-800 p-4 flex items-start justify-between gap-4"
                >
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
                  {!isMultiDayEvent(r) && (
                    <div className="text-right shrink-0">
                      <div className="text-dark-200 font-medium">
                        {r.startTime.slice(0, 5)} - {r.endTime.slice(0, 5)}
                      </div>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        )
      })}
    </div>
  )
}
