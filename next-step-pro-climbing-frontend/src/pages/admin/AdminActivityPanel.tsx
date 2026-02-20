import { useState, useCallback } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import {
  CalendarPlus,
  CalendarX,
  CalendarCheck,
  ShieldAlert,
  RefreshCw,
  ChevronDown,
} from 'lucide-react'
import { adminApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { ActivityLog, ActivityActionType } from '../../types'

const PAGE_SIZE = 20

const ACTION_CONFIG: Record<
  ActivityActionType,
  { icon: typeof CalendarPlus; color: string; bgColor: string }
> = {
  RESERVATION_CREATED: {
    icon: CalendarPlus,
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/10',
  },
  RESERVATION_REACTIVATED: {
    icon: CalendarCheck,
    color: 'text-emerald-400',
    bgColor: 'bg-emerald-500/10',
  },
  RESERVATION_CANCELLED: {
    icon: CalendarX,
    color: 'text-rose-400',
    bgColor: 'bg-rose-500/10',
  },
  EVENT_RESERVATION_CREATED: {
    icon: CalendarPlus,
    color: 'text-blue-400',
    bgColor: 'bg-blue-500/10',
  },
  EVENT_RESERVATION_CANCELLED: {
    icon: CalendarX,
    color: 'text-rose-400',
    bgColor: 'bg-rose-500/10',
  },
  RESERVATION_CANCELLED_BY_ADMIN: {
    icon: ShieldAlert,
    color: 'text-amber-400',
    bgColor: 'bg-amber-500/10',
  },
}

export function AdminActivityPanel() {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const [allLogs, setAllLogs] = useState<ActivityLog[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)

  const { isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'activity-logs'],
    queryFn: async () => {
      const logs = await adminApi.getActivityLogs(0, PAGE_SIZE)
      setAllLogs(logs)
      setPage(0)
      setHasMore(logs.length === PAGE_SIZE)
      return logs
    },
    refetchInterval: 30_000,
  })

  const loadMore = useCallback(async () => {
    const nextPage = page + 1
    setLoadingMore(true)
    try {
      const moreLogs = await adminApi.getActivityLogs(nextPage, PAGE_SIZE)
      setAllLogs((prev) => [...prev, ...moreLogs])
      setPage(nextPage)
      setHasMore(moreLogs.length === PAGE_SIZE)
    } finally {
      setLoadingMore(false)
    }
  }, [page])

  const handleRefresh = useCallback(() => {
    setPage(0)
    refetch()
  }, [refetch])

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <p className="text-sm text-dark-400">
          {t('activity.title')}
        </p>
        <button
          onClick={handleRefresh}
          className="flex items-center gap-1.5 text-sm text-dark-400 hover:text-dark-200 transition-colors"
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
        <div className="bg-dark-900 rounded-lg border border-dark-800 p-8 text-center text-dark-400">
          {t('activity.noActivity')}
        </div>
      ) : (
        <>
          <div className="space-y-2">
            {allLogs.map((log) => {
              const config = ACTION_CONFIG[log.actionType]
              const Icon = config.icon

              return (
                <div
                  key={log.id}
                  className="bg-dark-900 rounded-lg border border-dark-800 p-4 flex items-start gap-3"
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
                      <span className="text-dark-100 font-medium text-sm">
                        {log.userFullName}
                      </span>
                      <span className="text-dark-500 text-xs">{log.userEmail}</span>
                    </div>

                    <div className="flex items-center gap-2 mt-1 flex-wrap">
                      <span
                        className={`inline-flex px-2 py-0.5 text-xs rounded font-medium ${config.bgColor} ${config.color}`}
                      >
                        {t(`activity.actions.${log.actionType}`)}
                      </span>

                      {/* Slot info */}
                      {log.slotDate && (
                        <span className="text-dark-300 text-xs">
                          {format(new Date(log.slotDate), 'd MMM yyyy', { locale })}
                          {log.slotStartTime && log.slotEndTime && (
                            <>
                              {' '}
                              {log.slotStartTime.slice(0, 5)}-{log.slotEndTime.slice(0, 5)}
                            </>
                          )}
                          {log.slotTitle && (
                            <span className="text-dark-400"> ({log.slotTitle})</span>
                          )}
                        </span>
                      )}

                      {/* Event info */}
                      {log.eventTitle && (
                        <span className="text-dark-300 text-xs">
                          {log.eventTitle}
                          {log.eventStartDate && log.eventEndDate && (
                            <span className="text-dark-500">
                              {' '}
                              ({format(new Date(log.eventStartDate), 'd MMM', { locale })}
                              {' - '}
                              {format(new Date(log.eventEndDate), 'd MMM yyyy', { locale })})
                            </span>
                          )}
                        </span>
                      )}

                      {/* Participants */}
                      {log.participants != null && log.participants > 1 && (
                        <span className="text-dark-500 text-xs">
                          {t('activity.persons', { count: log.participants })}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Timestamp */}
                  <div className="flex-shrink-0 text-right">
                    <div className="text-dark-500 text-xs">
                      {format(new Date(log.createdAt), 'd MMM', { locale })}
                    </div>
                    <div className="text-dark-500 text-xs">
                      {format(new Date(log.createdAt), 'HH:mm')}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Load more */}
          {hasMore && (
            <div className="mt-4 text-center">
              <button
                onClick={loadMore}
                disabled={loadingMore}
                className="inline-flex items-center gap-2 px-4 py-2 text-sm text-dark-300 hover:text-dark-100 bg-dark-800 hover:bg-dark-700 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loadingMore ? (
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
