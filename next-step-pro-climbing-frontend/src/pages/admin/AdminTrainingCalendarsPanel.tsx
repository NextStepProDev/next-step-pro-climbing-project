import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ChevronRight, Dumbbell } from 'lucide-react'
import { format } from 'date-fns'
import { adminTrainingCalendarApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { Avatar } from '../../components/ui/Avatar'

/**
 * Coach's roster: flagged athletes with per-athlete unread badges (new trainings,
 * completions, comments, bookings since this admin last opened that athlete's calendar).
 * Sorted server-side: unread first, then most recent activity.
 */
export function AdminTrainingCalendarsPanel() {
  const { t } = useTranslation('admin')

  const athletesQuery = useQuery({
    queryKey: ['admin', 'trainingCalendar', 'athletes'],
    queryFn: adminTrainingCalendarApi.getAthletes,
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    // Per-athlete "N new" badges must be current the moment the roster opens —
    // the global 5-min staleTime would otherwise serve zeros from cache for up
    // to a minute (until the first interval tick)
    refetchOnMount: 'always',
  })

  if (athletesQuery.isLoading) {
    return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  }
  if (athletesQuery.isError) {
    return <QueryError error={athletesQuery.error} onRetry={() => athletesQuery.refetch()} />
  }

  const athletes = athletesQuery.data ?? []

  if (athletes.length === 0) {
    return (
      <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center">
        <Dumbbell className="w-12 h-12 text-surface-600 mx-auto mb-4" />
        <h3 className="text-lg font-medium text-surface-300 mb-2">
          {t('trainingCalendars.emptyTitle')}
        </h3>
        <p className="text-surface-500">{t('trainingCalendars.emptyHint')}</p>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      {athletes.map((athlete) => (
        <Link
          key={athlete.id}
          to={`/admin/training-calendars/${athlete.id}`}
          className="flex items-center gap-3 p-3 bg-surface-900 border border-surface-800 rounded-xl hover:border-surface-600 transition-colors"
        >
          <Avatar src={athlete.avatarUrl} name={athlete.firstName} />
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2">
              <span className="font-medium text-surface-100 truncate">
                {athlete.firstName} {athlete.lastName}
              </span>
              {athlete.newCount > 0 && (
                <span className="min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none">
                  {athlete.newCount}
                </span>
              )}
            </div>
            <div className="text-xs text-surface-500">
              {athlete.lastActivityAt
                ? t('trainingCalendars.lastActivity', {
                    date: format(new Date(athlete.lastActivityAt), 'dd.MM.yyyy HH:mm'),
                  })
                : t('trainingCalendars.noActivity')}
            </div>
          </div>
          <ChevronRight className="w-5 h-5 text-surface-500 shrink-0" />
        </Link>
      ))}
    </div>
  )
}
