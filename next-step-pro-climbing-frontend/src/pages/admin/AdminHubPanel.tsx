import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import { adminTabGroups, adminTabBadges } from './adminTabs'

/**
 * The panel's landing screen.
 *
 * `/admin` used to be the Slots panel, so nothing in the app answered "what needs me today" —
 * the four counters that could answer it were already fetched and spent entirely on red dots.
 *
 * Reads only `['admin','notifications']`, the same key the nav row uses, so TanStack serves it
 * from cache and this screen costs zero extra requests. Deliberately does NOT reach for
 * `/admin/settlements/overview` ("to price", "to recover"): that is a separate, expensive read
 * with its own query budget, and every visit to the panel would pay for it.
 */
export function AdminHubPanel() {
  const { t } = useTranslation('admin')

  const { data: notifications } = useQuery({
    queryKey: ['admin', 'notifications'],
    queryFn: adminApi.getNotifications,
    staleTime: 0,
  })

  const badges = adminTabBadges(notifications)
  const attention = adminTabGroups
    .flatMap((group) => group.tabs)
    .filter((tab) => (badges[tab.path] ?? 0) > 0)

  return (
    <div className="space-y-8">
      <section>
        <h2 className="text-[11px] font-semibold uppercase tracking-wider text-surface-500 mb-3">
          {t('hub.attention')}
        </h2>
        {attention.length === 0 ? (
          // A quiet line rather than an absent section: a block that appears and disappears
          // between visits shifts everything below it.
          <p className="text-sm text-surface-500">{t('hub.allClear')}</p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {attention.map((tab) => {
              const Icon = tab.icon
              return (
                <Link
                  key={tab.path}
                  to={tab.path}
                  className="flex items-center gap-3 rounded-xl border border-rose-500/30 bg-rose-500/5 p-4 transition-colors hover:border-rose-500/60 hover:bg-rose-500/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                >
                  <span className="w-9 h-9 shrink-0 rounded-lg bg-rose-500/15 flex items-center justify-center">
                    <Icon className="w-4 h-4 text-rose-400" />
                  </span>
                  <span className="min-w-0">
                    <span className="block text-xl font-bold text-surface-100 tabular-nums leading-none">
                      {badges[tab.path]}
                    </span>
                    <span className="block text-xs text-surface-400 truncate mt-1">
                      {t(tab.labelKey)}
                    </span>
                  </span>
                </Link>
              )
            })}
          </div>
        )}
      </section>

      {adminTabGroups.map((group) => (
        <section key={group.groupKey}>
          <h2 className="text-[11px] font-semibold uppercase tracking-wider text-surface-500 mb-3">
            {t(group.groupKey)}
          </h2>
          {/* Tailwind's `grid-cols-*` expands to `minmax(0, 1fr)` on its own — a hand-written
              `gridTemplateColumns` here would let one long title stretch its own track. */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {group.tabs.map((tab) => {
              const Icon = tab.icon
              const count = badges[tab.path] ?? 0
              return (
                <Link
                  key={tab.path}
                  to={tab.path}
                  className="flex items-start gap-3 rounded-xl border border-surface-800 bg-surface-900 p-4 transition-colors hover:border-primary-500/50 hover:bg-primary-500/5 focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
                >
                  <span className="w-9 h-9 shrink-0 rounded-lg bg-surface-800 flex items-center justify-center">
                    <Icon className="w-4 h-4 text-surface-300" />
                  </span>
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5">
                      <span className="text-sm font-medium text-surface-100 truncate">
                        {t(tab.labelKey)}
                      </span>
                      {count > 0 && (
                        <span className="min-w-5 h-5 px-1.5 inline-flex items-center justify-center rounded-full bg-rose-500 text-white text-[11px] font-bold leading-none">
                          {count}
                        </span>
                      )}
                    </span>
                    <span className="block text-xs text-surface-400 leading-snug mt-1">
                      {t(tab.hintKey)}
                    </span>
                  </span>
                </Link>
              )
            })}
          </div>
        </section>
      ))}
    </div>
  )
}
