import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { pl } from 'date-fns/locale'
import { ArrowLeft, Calendar, MapPin, Users } from 'lucide-react'
import { adminApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { Button } from '../components/ui/Button'

export function EventPage() {
  const { eventId } = useParams<{ eventId: string }>()

  const { data: event, isLoading, error } = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => adminApi.getEventDetails(eventId!),
    enabled: !!eventId,
  })

  if (isLoading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner size="lg" />
      </div>
    )
  }

  if (error || !event) {
    return (
      <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-dark-900 rounded-xl border border-dark-800 p-8 text-center">
          <h2 className="text-xl font-semibold text-dark-100 mb-2">
            Nie znaleziono wydarzenia
          </h2>
          <p className="text-dark-400 mb-4">
            To wydarzenie nie istnieje lub zostało usunięte.
          </p>
          <Link to="/calendar">
            <Button variant="primary">Wróć do kalendarza</Button>
          </Link>
        </div>
      </div>
    )
  }

  const eventTypeLabels = {
    COURSE: 'Kurs',
    TRAINING: 'Trening',
    WORKSHOP: 'Warsztat',
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Link
        to="/calendar"
        className="inline-flex items-center text-dark-400 hover:text-dark-200 mb-6"
      >
        <ArrowLeft className="w-4 h-4 mr-2" />
        Wróć do kalendarza
      </Link>

      <div className="bg-dark-900 rounded-xl border border-dark-800 overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-dark-800">
          <div className="flex items-center gap-2 mb-2">
            <span className="px-2 py-1 text-xs font-medium bg-primary-500/20 text-primary-400 rounded">
              {eventTypeLabels[event.eventType as keyof typeof eventTypeLabels]}
            </span>
          </div>
          <h1 className="text-2xl font-bold text-dark-100">{event.title}</h1>
        </div>

        {/* Details */}
        <div className="p-6 space-y-6">
          {/* Dates */}
          <div className="flex items-center gap-3 text-dark-300">
            <Calendar className="w-5 h-5" />
            <span>
              {format(new Date(event.startDate), 'd MMMM yyyy', { locale: pl })}
              {event.startDate !== event.endDate && (
                <>
                  {' '}-{' '}
                  {format(new Date(event.endDate), 'd MMMM yyyy', { locale: pl })}
                </>
              )}
            </span>
          </div>

          {/* Location */}
          {event.location && (
            <div className="flex items-center gap-3 text-dark-300">
              <MapPin className="w-5 h-5" />
              <span>{event.location}</span>
            </div>
          )}

          {/* Capacity */}
          <div className="flex items-center gap-3 text-dark-300">
            <Users className="w-5 h-5" />
            <span>Maks. {event.maxParticipants} uczestników</span>
          </div>

          {/* Description */}
          {event.description && (
            <div className="pt-4 border-t border-dark-800">
              <h3 className="text-sm font-medium text-dark-400 mb-2">Opis</h3>
              <p className="text-dark-200 whitespace-pre-wrap">
                {event.description}
              </p>
            </div>
          )}

          {/* CTA */}
          <div className="pt-4 border-t border-dark-800">
            <Link to={`/calendar?date=${event.startDate}`}>
              <Button variant="primary">
                Zobacz dostępne terminy
              </Button>
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
