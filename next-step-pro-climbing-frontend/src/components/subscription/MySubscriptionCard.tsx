import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Receipt } from 'lucide-react'
import { mySubscriptionApi } from '../../api/client'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { formatPln } from '../../utils/money'

/**
 * The client's own coaching subscription: what it costs, which months are settled, what is owed.
 *
 * Renders nothing for somebody who never had one — that is nearly everybody, and an empty card
 * about a product they do not use would be noise on their bookings page. Read-only: payments are
 * recorded by the coach, so there is nothing here to press.
 */
export function MySubscriptionCard() {
  const { t, i18n } = useTranslation('reservations')
  const locale = useDateLocale()
  const money = (amount: number) => formatPln(amount, i18n.language)

  const { data } = useQuery({
    queryKey: ['mySubscription'],
    queryFn: mySubscriptionApi.get,
  })

  if (!data?.current) return null
  const { current } = data
  const monthLabel = (iso: string) => format(parseCalendarDate(iso), 'LLLL yyyy', { locale })

  return (
    <section
      aria-labelledby="my-subscription-title"
      className="mb-6 bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="my-subscription-title" className="flex items-center gap-2 text-sm font-medium text-surface-200">
          <Receipt className="w-4 h-4 text-surface-400" />
          {t('subscription.title')}
        </h2>
        <span className="text-xs text-surface-400">
          {money(current.amount)} {t('subscription.perMonth')} ·{' '}
          {current.active
            ? t('subscription.since', { month: monthLabel(current.startedOn) })
            : t('subscription.ended', {
                from: monthLabel(current.startedOn),
                to: current.endedOn ? monthLabel(current.endedOn) : '',
              })}
        </span>
      </div>

      {/* Amber because it is something to do; nothing at all when nothing is owed — a green
          "0 zł" would celebrate an absence. */}
      {data.due > 0 && (
        <p className="text-sm text-amber-500">{t('subscription.due', { amount: money(data.due) })}</p>
      )}

      {data.months.length > 0 && (
        <ul className="divide-y divide-surface-800">
          {data.months.map((m) => (
            <li key={m.month} className="flex items-center gap-3 py-1.5 text-xs">
              <span className="flex-1 min-w-0 truncate text-surface-300 capitalize">{monthLabel(m.month)}</span>
              {m.skipped ? (
                <span className="text-surface-400">{t('subscription.waived')}</span>
              ) : (
                <>
                  <span className="shrink-0 text-surface-200 tabular-nums">{money(m.amount ?? 0)}</span>
                  <span className="w-28 shrink-0 text-right tabular-nums">
                    {/* A part payment is neither paid nor unpaid — say how much is missing. */}
                    {m.remaining === 0 ? (
                      <span className="text-surface-400">
                        {m.paidOn
                          ? t('subscription.paidOn', { date: format(parseCalendarDate(m.paidOn), 'dd.MM.yyyy') })
                          : t('subscription.paid')}
                      </span>
                    ) : m.covered > 0 ? (
                      <span className="text-amber-500">{t('subscription.short', { amount: money(m.remaining) })}</span>
                    ) : (
                      <span className="text-amber-500">{t('subscription.unpaid')}</span>
                    )}
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      <p className="text-xs text-surface-500">{t('subscription.hint')}</p>
    </section>
  )
}
