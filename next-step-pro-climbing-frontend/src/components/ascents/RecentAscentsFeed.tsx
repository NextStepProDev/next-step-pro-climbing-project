import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { Mountain } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { ascentApi } from '../../api/client'
import { CardSkeleton } from '../ui/CardSkeleton'
import { QueryError } from '../ui/QueryError'
import { parseCalendarDate } from '../../utils/calendarDate'

/**
 * The club's ten newest ascents, on the public news page.
 *
 * <p>Public in the strict sense: no login, no tokens, and the API returns only what belongs on a
 * noticeboard — who, what, where, when. Comments, attempt counts and route ratings stay in the
 * climber's own logbook.
 */
export function RecentAscentsFeed() {
  const { t } = useTranslation('ascents')

  const feedQuery = useQuery({
    queryKey: ['ascents', 'public', 'recent'],
    queryFn: ascentApi.getRecentPublic,
    // The server caches this for everyone; asking again on every focus would only add noise
    staleTime: 60_000,
  })

  if (feedQuery.isLoading) return <CardSkeleton count={3} columns={2} />
  if (feedQuery.isError) {
    return <QueryError error={feedQuery.error} onRetry={() => feedQuery.refetch()} />
  }

  const entries = feedQuery.data ?? []

  if (entries.length === 0) {
    return (
      <div className="text-center py-12">
        <Mountain className="w-10 h-10 mx-auto mb-3 text-surface-600" aria-hidden="true" />
        <p className="text-surface-400">{t('feed.empty')}</p>
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <p className="text-sm text-surface-500 mb-4">{t('feed.intro')}</p>
      <ul className="space-y-2">
        {entries.map(entry => (
          <li
            key={entry.id}
            className="card-glass rounded-lg border border-surface-700/50 px-4 py-3 flex items-center gap-x-3 gap-y-1"
          >
            <span className="shrink-0 inline-flex items-center justify-center min-w-12 px-2 py-1 rounded bg-surface-800 text-surface-100 font-bold tabular-nums">
              {entry.gradeLabel}
            </span>
            <div className="flex-1 min-w-0">
              {/* Nothing here truncates. A route name is the point of the entry, and a name cut
                  to "Arête des Cosm…" cannot be recovered on a phone — there is no hover, no
                  tooltip, nowhere to look. Long names wrap onto a second line instead. */}
              <p className="text-surface-100 font-medium break-words">
                {entry.routeName}
                <span className="ml-2 text-xs font-normal text-surface-400 whitespace-nowrap">
                  {t(`style.${entry.style}`)}
                </span>
              </p>
              {/* Wraps rather than truncates: on a narrow screen a second line is better than
                  losing the crag entirely */}
              <p className="text-sm text-surface-400">
                {entry.climberName}
                <span className="text-surface-600"> · </span>
                {entry.crag}
                <span className="text-surface-600"> · </span>
                {/* Mountains have no discipline, so the terrain labels them instead */}
                {entry.discipline
                  ? t(`discipline.${entry.discipline}`)
                  : t('terrain.MOUNTAIN')}
              </p>
            </div>
            <time
              className="shrink-0 self-start sm:self-center text-xs sm:text-sm text-surface-500 tabular-nums"
              dateTime={entry.climbedOn}
            >
              {/* A 'yyyy-MM-dd' is a label, not an instant — parse it as one */}
              {format(parseCalendarDate(entry.climbedOn), 'dd.MM.yyyy')}
            </time>
          </li>
        ))}
      </ul>
    </div>
  )
}
