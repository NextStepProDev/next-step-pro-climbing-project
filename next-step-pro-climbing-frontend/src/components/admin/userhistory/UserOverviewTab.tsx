import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { CalendarCheck, CalendarX, Dumbbell, Mountain } from 'lucide-react'
import { UserMoneyCard } from './UserMoneyCard'
import { adminUserHistoryApi } from '../../../api/client'
import { ACTION_CONFIG, UNKNOWN_ACTION_CONFIG } from '../activityActionConfig'
import { LoadingSpinner } from '../../ui/LoadingSpinner'
import { QueryError } from '../../ui/QueryError'
import { Button } from '../../ui/Button'
import { useDateLocale } from '../../../utils/dateFnsLocale'
import { parseCalendarDate } from '../../../utils/calendarDate'
import type { UserDetail } from '../../../types'

const PAGE_SIZE = 20

interface UserOverviewTabProps {
  userId: string
  user: UserDetail
}

/**
 * Headline tiles plus the person's own timeline.
 *
 * <p>The timeline shows actions filed UNDER this user: everything they did themselves, plus
 * admin cancellations of their bookings — those are recorded against the affected user rather
 * than the admin who clicked. Things an admin did TO the account (role, athlete flag, forced
 * logout) stay filed under that admin, so they are not here; the Account tab shows the resulting
 * state instead.
 */
export function UserOverviewTab({ userId, user }: UserOverviewTabProps) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const [page, setPage] = useState(0)

  const activityQuery = useQuery({
    queryKey: ['admin', 'userHistory', userId, 'activity', page],
    queryFn: () => adminUserHistoryApi.getActivity(userId, page, PAGE_SIZE),
  })

  const logs = activityQuery.data ?? []
  const hasMore = logs.length === PAGE_SIZE

  const counts = user.counts
  // Null (not zero) means the admin may not read that data at all — the tile is dropped rather
  // than shown as "0", which would state something false about a private calendar or logbook.
  const tiles = [
    { key: 'reservations', value: counts.reservationsConfirmed, icon: CalendarCheck, color: 'text-green-400', bg: 'bg-green-500/10' },
    { key: 'cancelled', value: counts.reservationsCancelled, icon: CalendarX, color: 'text-rose-400', bg: 'bg-rose-500/10' },
    ...(counts.trainingsCompleted !== null
      ? [{ key: 'trainings', value: counts.trainingsCompleted, icon: Dumbbell, color: 'text-indigo-300', bg: 'bg-indigo-500/10' }]
      : []),
    ...(counts.ascents !== null
      ? [{ key: 'ascents', value: counts.ascents, icon: Mountain, color: 'text-amber-300', bg: 'bg-amber-500/10' }]
      : []),
  ]

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {tiles.map((tile) => {
          const Icon = tile.icon
          return (
            <div key={tile.key} className="bg-surface-900 border border-surface-800 rounded-xl p-4">
              <div className={`w-8 h-8 rounded-lg flex items-center justify-center mb-2 ${tile.bg}`}>
                <Icon className={`w-4 h-4 ${tile.color}`} />
              </div>
              <div className="text-2xl font-bold text-surface-100">{tile.value}</div>
              <div className="text-xs text-surface-400">{t(`users.detail.tiles.${tile.key}`)}</div>
            </div>
          )
        })}
      </div>

      <UserMoneyCard userId={userId} />

      <div>
        <h3 className="text-sm font-semibold text-surface-300 mb-2">
          {t('users.detail.timeline')}
        </h3>

        {activityQuery.isLoading ? (
          <div className="py-12 flex justify-center"><LoadingSpinner /></div>
        ) : activityQuery.isError ? (
          <QueryError error={activityQuery.error} onRetry={() => activityQuery.refetch()} />
        ) : logs.length === 0 ? (
          <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center text-surface-400">
            {t('users.detail.noActivity')}
          </div>
        ) : (
          <div className="space-y-2">
            {logs.map((log) => {
              // Fallback rather than a direct lookup, for the same reason as the Activity panel:
              // action_type is a plain VARCHAR with no CHECK, so a new backend value must not
              // white-screen this tab.
              const config = ACTION_CONFIG[log.actionType] ?? UNKNOWN_ACTION_CONFIG
              const Icon = config.icon

              return (
                <div
                  key={log.id}
                  className="bg-surface-900 rounded-lg border border-surface-800 p-3 flex items-start gap-3"
                >
                  <div className={`shrink-0 w-8 h-8 rounded-lg flex items-center justify-center ${config.bgColor}`}>
                    <Icon className={`w-4 h-4 ${config.color}`} />
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      {/* No name/email here, unlike the Activity panel: on a single-person card
                          it is the same person on every row. */}
                      <span className={`inline-flex px-2 py-0.5 text-xs rounded font-medium ${config.bgColor} ${config.color}`}>
                        {t(`activity.actions.${log.actionType}`)}
                      </span>
                      {log.slotDate && (
                        <span className="text-surface-300 text-xs">
                          {format(parseCalendarDate(log.slotDate), 'd MMM yyyy', { locale })}
                          {log.slotStartTime && log.slotEndTime && (
                            <> {log.slotStartTime.slice(0, 5)}–{log.slotEndTime.slice(0, 5)}</>
                          )}
                        </span>
                      )}
                      {log.slotTitle && (
                        <span className="text-surface-400 text-xs truncate">{log.slotTitle}</span>
                      )}
                      {log.eventTitle && (
                        <span className="text-surface-400 text-xs truncate">{log.eventTitle}</span>
                      )}
                    </div>
                    {log.description && (
                      <p className="text-surface-400 text-xs mt-1 break-words">{log.description}</p>
                    )}
                  </div>

                  {/* createdAt is an instant, so it renders in the viewer's own zone — unlike the
                      slot date above, which is a Warsaw wall-clock label. */}
                  <div className="text-right shrink-0 text-xs text-surface-500">
                    {format(new Date(log.createdAt), 'dd.MM.yyyy')}
                    <div>{format(new Date(log.createdAt), 'HH:mm')}</div>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {(page > 0 || hasMore) && (
          <div className="flex justify-center gap-2 mt-3">
            <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              {t('users.detail.newer')}
            </Button>
            <Button variant="ghost" size="sm" disabled={!hasMore} onClick={() => setPage((p) => p + 1)}>
              {t('users.detail.older')}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
