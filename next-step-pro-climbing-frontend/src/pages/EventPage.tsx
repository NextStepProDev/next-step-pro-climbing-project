import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Calendar, MapPin, Users } from 'lucide-react'
import { adminApi } from '../api/client'
import { LoadingSpinner } from '../components/ui/LoadingSpinner'
import { Button } from '../components/ui/Button'
import { useDateLocale } from '../utils/dateFnsLocale'

export function EventPage() {
  const { t } = useTranslation('calendar')
  const { t: tc } = useTranslation('common')
  const locale = useDateLocale()
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
            {t('eventPage.notFound')}
          </h2>
          <p className="text-dark-400 mb-4">
            {t('eventPage.notFoundDesc')}
          </p>
          <Link to="/calendar">
            <Button variant="primary">{t('eventPage.backToCalendar')}</Button>
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <Link
        to="/calendar"
        className="inline-flex items-center text-dark-400 hover:text-dark-200 mb-6"
      >
        <ArrowLeft className="w-4 h-4 mr-2" />
        {t('eventPage.backToCalendar')}
      </Link>

      <div className="bg-dark-900 rounded-xl border border-dark-800 overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-dark-800">
          <div className="flex items-center gap-2 mb-2">
            <span className="px-2 py-1 text-xs font-medium bg-primary-500/20 text-primary-400 rounded">
              {tc(`eventTypes.${event.eventType}`)}
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
              {format(new Date(event.startDate), 'd MMMM yyyy', { locale })}
              {event.startDate !== event.endDate && (
                <>
                  {' '}-{' '}
                  {format(new Date(event.endDate), 'd MMMM yyyy', { locale })}
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
            <span>{t('eventPage.maxParticipants', { count: event.maxParticipants })}</span>
          </div>

          {/* Description */}
          {event.description && (
            <div className="pt-4 border-t border-dark-800">
              <h3 className="text-sm font-medium text-dark-400 mb-2">{t('eventPage.description')}</h3>
              <p className="text-dark-200 whitespace-pre-wrap">
                {event.description}
              </p>
            </div>
          )}

          {/* CTA */}
          <div className="pt-4 border-t border-dark-800">
            <Link to={`/calendar?date=${event.startDate}`}>
              <Button variant="primary">
                {t('eventPage.viewSlots')}
              </Button>
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}
