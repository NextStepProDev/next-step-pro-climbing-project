import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { Calendar, Clock, MessageSquare, Users, X, Ban } from 'lucide-react'
import { reservationApi } from '../api/client'
import { getErrorMessage } from '../utils/errors'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { QueryError } from '../components/ui/QueryError'
import { Button } from '../components/ui/Button'

const EVENT_TYPE_LABELS: Record<string, string> = {
  COURSE: 'Kurs',
  TRAINING: 'Trening',
  WORKSHOP: 'Warsztat',
}

export function MyReservationsPage() {
  const queryClient = useQueryClient()

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['reservations', 'upcoming'],
    queryFn: reservationApi.getMyUpcoming,
  })

  const cancelMutation = useMutation({
    mutationFn: reservationApi.cancel,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  const cancelEventMutation = useMutation({
    mutationFn: reservationApi.cancelForEvent,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reservations'] })
      queryClient.invalidateQueries({ queryKey: ['calendar'] })
    },
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (isError) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <QueryError error={error} onRetry={() => refetch()} />
      </div>
    )
  }

  const slots = data?.slots ?? []
  const events = data?.events ?? []
  const isEmpty = slots.length === 0 && events.length === 0

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-dark-100 mb-2">
          Moje rezerwacje
        </h1>
        <p className="text-dark-400">
          Nadchodzące zajęcia, na które jesteś zapisany/a.
        </p>
      </div>

      {isEmpty ? (
        <div className="bg-dark-900 rounded-xl border border-dark-800 p-8 text-center">
          <Calendar className="w-12 h-12 text-dark-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-dark-300 mb-2">
            Brak rezerwacji
          </h3>
          <p className="text-dark-500 mb-4">
            Nie masz żadnych nadchodzących zajęć.
          </p>
          <a href="/calendar">
            <Button variant="primary">Przejdź do kalendarza</Button>
          </a>
        </div>
      ) : (
        <div className="space-y-6">
          {events.length > 0 && (
            <div className="space-y-4">
              <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider">
                Wydarzenia
              </h2>
              {events.map((event) => (
                <div
                  key={event.eventId}
                  className="bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-6"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <span className="inline-block px-2 py-0.5 text-xs font-medium rounded bg-primary-500/20 text-primary-400">
                          {EVENT_TYPE_LABELS[event.eventType] || event.eventType}
                        </span>
                        <span className="font-medium text-dark-100">
                          {event.eventTitle}
                        </span>
                      </div>
                      <div className="flex items-center gap-3 text-dark-400">
                        <Calendar className="w-5 h-5" />
                        <span>
                          {format(new Date(event.startDate), 'd MMMM', { locale: pl })}
                          {' - '}
                          {format(new Date(event.endDate), 'd MMMM yyyy', { locale: pl })}
                        </span>
                      </div>
                      {event.participants > 1 && (
                        <div className="flex items-center gap-2 mt-2 text-sm text-dark-400">
                          <Users className="w-4 h-4" />
                          <span>{event.participants} miejsca zarezerwowane</span>
                        </div>
                      )}
                      {event.comment && (
                        <div className="flex items-start gap-2 mt-2 text-sm text-dark-400">
                          <MessageSquare className="w-4 h-4 mt-0.5 shrink-0" />
                          <span>"{event.comment}"</span>
                        </div>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      <Button
                        variant="danger"
                        size="sm"
                        loading={cancelEventMutation.isPending}
                        onClick={() => {
                          if (
                            window.confirm(
                              'Czy na pewno chcesz anulować zapis na całe wydarzenie?'
                            )
                          ) {
                            cancelEventMutation.mutate(event.eventId)
                          }
                        }}
                      >
                        <X className="w-4 h-4 mr-1" />
                        Anuluj wydarzenie
                      </Button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {slots.length > 0 && (
            <div className="space-y-4">
              <h2 className="text-sm font-medium text-dark-400 uppercase tracking-wider">
                Treningi
              </h2>
              {slots.map((reservation) => {
                const dateObj = new Date(reservation.date)
                const isCancelledByAdmin = reservation.status === 'CANCELLED_BY_ADMIN'
                return (
                  <div
                    key={reservation.id}
                    className={
                      isCancelledByAdmin
                        ? 'bg-rose-500/5 rounded-xl border border-rose-500/30 p-4 sm:p-6'
                        : 'bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-6'
                    }
                  >
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                      <div className="flex-1">
                        <div className="flex items-center gap-3 mb-2">
                          {isCancelledByAdmin ? (
                            <Ban className="w-5 h-5 text-rose-400" />
                          ) : (
                            <Calendar className="w-5 h-5 text-primary-400" />
                          )}
                          <span className="font-medium text-dark-100 capitalize">
                            {format(dateObj, 'EEEE, d MMMM yyyy', { locale: pl })}
                          </span>
                          {isCancelledByAdmin && (
                            <span className="inline-block px-2 py-0.5 text-xs font-medium rounded bg-rose-500/20 text-rose-400">
                              Anulowany przez instruktora
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-dark-400">
                          <Clock className="w-5 h-5" />
                          <span>
                            {reservation.startTime.slice(0, 5)} -{' '}
                            {reservation.endTime.slice(0, 5)}
                          </span>
                        </div>
                        {reservation.participants > 1 && (
                          <div className="flex items-center gap-2 mt-2 text-sm text-dark-400">
                            <Users className="w-4 h-4" />
                            <span>{reservation.participants} miejsca zarezerwowane</span>
                          </div>
                        )}
                        {reservation.eventTitle && (
                          <div className="mt-2 inline-block px-2 py-1 bg-primary-500/10 text-primary-400 text-sm rounded">
                            {reservation.eventTitle}
                          </div>
                        )}
                        {reservation.comment && (
                          <div className="flex items-start gap-2 mt-2 text-sm text-dark-400">
                            <MessageSquare className="w-4 h-4 mt-0.5 shrink-0" />
                            <span>"{reservation.comment}"</span>
                          </div>
                        )}
                      </div>

                      {!isCancelledByAdmin && (
                        <div className="flex items-center gap-2">
                          <Button
                            variant="danger"
                            size="sm"
                            loading={cancelMutation.isPending}
                            onClick={() => {
                              if (
                                window.confirm(
                                  'Czy na pewno chcesz anulować tę rezerwację?'
                                )
                              ) {
                                cancelMutation.mutate(reservation.id)
                              }
                            }}
                          >
                            <X className="w-4 h-4 mr-1" />
                            Anuluj
                          </Button>
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      )}

      {cancelMutation.isError && (
        <div className="mt-4 p-4 bg-rose-500/5 border border-rose-500/15 rounded-lg text-rose-400/80">
          {getErrorMessage(cancelMutation.error)}
        </div>
      )}
      {cancelEventMutation.isError && (
        <div className="mt-4 p-4 bg-rose-500/5 border border-rose-500/15 rounded-lg text-rose-400/80">
          {getErrorMessage(cancelEventMutation.error)}
        </div>
      )}
    </div>
  )
}
