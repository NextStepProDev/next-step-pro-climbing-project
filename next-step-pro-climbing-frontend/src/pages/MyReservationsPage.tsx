import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { Calendar, Clock, MessageSquare, Users, X } from 'lucide-react'
import { reservationApi } from '../api/client'
import { getErrorMessage } from '../utils/errors'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { Button } from '../components/ui/Button'

export function MyReservationsPage() {
  const queryClient = useQueryClient()

  const { data: reservations, isLoading } = useQuery({
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

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

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

      {reservations?.length === 0 ? (
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
        <div className="space-y-4">
          {reservations?.map((reservation) => {
            const dateObj = new Date(reservation.date)
            return (
              <div
                key={reservation.id}
                className="bg-dark-900 rounded-xl border border-dark-800 p-4 sm:p-6"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex-1">
                    <div className="flex items-center gap-3 mb-2">
                      <Calendar className="w-5 h-5 text-primary-400" />
                      <span className="font-medium text-dark-100 capitalize">
                        {format(dateObj, 'EEEE, d MMMM yyyy', { locale: pl })}
                      </span>
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
                </div>
              </div>
            )
          })}
        </div>
      )}

      {cancelMutation.isError && (
        <div className="mt-4 p-4 bg-red-500/10 border border-red-500/20 rounded-lg text-red-400">
          {getErrorMessage(cancelMutation.error)}
        </div>
      )}
    </div>
  )
}
