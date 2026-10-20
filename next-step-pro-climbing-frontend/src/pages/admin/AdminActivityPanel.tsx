import { useMemo } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { RefreshCw, ChevronDown } from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { parseCalendarDate } from '../../utils/calendarDate'
import type { ActivityLog } from '../../types'
import { ACTION_CONFIG, UNKNOWN_ACTION_CONFIG } from '../../components/admin/activityActionConfig'

const PAGE_SIZE = 20

export function AdminActivityPanel() {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  // Pages owned by React Query, not copied into local state. The list used to live in useState and
  // the 30-second poll rewrote it with page 0 alone, so whoever had loaded older entries to read
  // them watched the list fold back to twenty under their eyes. An infinite query refetches every
  // page it holds.
  const { data, isLoading, isError, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
    queryKey: ['admin', 'activity-logs'],
    queryFn: ({ pageParam }) => adminApi.getActivityLogs(pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (last, all) => (last.length === PAGE_SIZE ? all.length : undefined),
    refetchInterval: 30_000,
  })

  // Pages are offsets, so a new entry on top pushes the last row of one page onto the next —
  // shown twice, with a duplicate React key. Once per id, newest first as served.
  const allLogs = useMemo(() => {
    const seen = new Set<string>()
    const out: ActivityLog[] = []
    for (const entry of data?.pages.flat() ?? []) {
      if (seen.has(entry.id)) continue
      seen.add(entry.id)
      out.push(entry)
    }
    return out
  }, [data])

  const handleRefresh = () => { refetch() }

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-surface-400">
          {t('activity.title')}
        </p>
        <button
          onClick={handleRefresh}
          className="flex items-center gap-1.5 text-sm text-surface-400 hover:text-surface-200 transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t('activity.refresh')}
        </button>
      </div>

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <QueryError error={error} onRetry={handleRefresh} />
      ) : allLogs.length === 0 ? (
        <div className="bg-surface-900 rounded-lg border border-surface-800 p-8 text-center text-surface-400">
          {t('activity.noActivity')}
        </div>
      ) : (
        <>
          <div className="space-y-2">
            {allLogs.map((log) => {
              // Fallback, not `config.icon` straight off the lookup: action_type is a plain
              // VARCHAR with no CHECK, so the backend can ship a new ActivityActionType without a
              // migration. An unmapped value used to throw here and white-screen the whole tab —
              // a log line nobody styled yet is not worth losing the Activity view over.
              const config = ACTION_CONFIG[log.actionType] ?? UNKNOWN_ACTION_CONFIG
              const Icon = config.icon

              return (
                <div
                  key={log.id}
                  className="bg-surface-900 rounded-lg border border-surface-800 p-4 flex items-start gap-3"
                >
                  {/* Icon */}
                  <div
                    className={`flex-shrink-0 w-9 h-9 rounded-lg flex items-center justify-center ${config.bgColor}`}
                  >
                    <Icon className={`w-4.5 h-4.5 ${config.color}`} />
                  </div>

                  {/* Content */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-surface-100 font-medium text-sm">
                        {log.userFullName}
                      </span>
                      <span className="text-surface-500 text-xs">{log.userEmail}</span>
                    </div>

                    <div className="flex items-center gap-2 mt-1 flex-wrap">
                      <span
                        className={`inline-flex px-2 py-0.5 text-xs rounded font-medium ${config.bgColor} ${config.color}`}
                      >
                        {t(`activity.actions.${log.actionType}`)}
                      </span>

                      {/* The person the admin acted on — gone once their account is deleted */}
                      {log.subjectFullName && (
                        <span className="text-surface-300 text-xs">
                          {log.subjectFullName}
                          {log.subjectEmail && <span className="text-surface-500"> ({log.subjectEmail})</span>}
                        </span>
                      )}
                      {log.subjectErased && (
                        <span className="text-surface-500 text-xs italic">{t('activity.subjectErased')}</span>
                      )}

                      {/* Slot info */}
                      {log.slotDate && (
                        <span className="text-surface-300 text-xs">
                          {format(parseCalendarDate(log.slotDate), 'd MMM yyyy', { locale })}
                          {log.slotStartTime && log.slotEndTime && (
                            <>
                              {' '}
                              {log.slotStartTime.slice(0, 5)}-{log.slotEndTime.slice(0, 5)}
                            </>
                          )}
                          {log.slotTitle && (
                            <span className="text-surface-400"> ({log.slotTitle})</span>
                          )}
                        </span>
                      )}

                      {/* Event info */}
                      {log.eventTitle && (
                        <span className="text-surface-300 text-xs">
                          {log.eventTitle}
                          {log.eventStartDate && log.eventEndDate && (
                            <span className="text-surface-500">
                              {' '}
                              ({format(parseCalendarDate(log.eventStartDate), 'd MMM', { locale })}
                              {' - '}
                              {format(parseCalendarDate(log.eventEndDate), 'd MMM yyyy', { locale })})
                            </span>
                          )}
                        </span>
                      )}

                      {/* Participants */}
                      {log.participants != null && log.participants > 1 && (
                        <span className="text-surface-500 text-xs">
                          {t('activity.persons', { count: log.participants })}
                        </span>
                      )}

                      {/* Description (admin actions context) */}
                      {log.description && (
                        <span className="text-surface-400 text-xs">
                          {log.description}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Timestamp */}
                  <div className="flex-shrink-0 text-right">
                    <div className="text-surface-500 text-xs">
                      {format(new Date(log.createdAt), 'd MMM', { locale })}
                    </div>
                    <div className="text-surface-500 text-xs">
                      {format(new Date(log.createdAt), 'HH:mm')}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Load more */}
          {hasNextPage && (
            <div className="mt-4 text-center">
              <button
                onClick={() => fetchNextPage()}
                disabled={isFetchingNextPage}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm text-surface-300 hover:text-surface-100 bg-surface-800 hover:bg-surface-700 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isFetchingNextPage ? (
                  <LoadingSpinner />
                ) : (
                  <>
                    <ChevronDown className="w-4 h-4" />
                    {t('activity.loadMore')}
                  </>
                )}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
