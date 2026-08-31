import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { Coins } from 'lucide-react'
import { adminSettlementsApi } from '../../../api/client'
import { parseCalendarDate } from '../../../utils/calendarDate'
import { useDateLocale } from '../../../utils/dateFnsLocale'
import { formatPln } from '../../../utils/money'

/**
 * What this client has paid and still owes.
 *
 * Its own request rather than fields on the user card's DTO: the settlement types are deliberately
 * unreachable from the package that builds that card, and one rule beats an exception to it.
 *
 * Whole history, not the Settlements tab's selected year — "what do I have with this person" has no
 * year in it.
 */
export function UserMoneyCard({ userId }: { userId: string }) {
  const { t, i18n } = useTranslation('admin')
  const locale = useDateLocale()

  const { data } = useQuery({
    queryKey: ['admin', 'settlements', 'payer', userId],
    queryFn: () => adminSettlementsApi.getPayerSummary(userId),
  })

  const money = (amount: number) => formatPln(amount, i18n.language)

  // Nothing recorded is not a zero balance — rendering "0 zł paid" would state something about
  // somebody nobody has ever priced.
  if (!data) return null
  const empty = data.settlementCount === 0 && data.outstanding === 0 && data.recent.length === 0

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium text-surface-300">
        <Coins className="w-4 h-4 text-surface-400" />
        {t('users.detail.money.title')}
      </div>

      {empty ? (
        <p className="text-sm text-surface-400">{t('users.detail.money.none')}</p>
      ) : (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Figure label={t('users.detail.money.paid')} value={money(data.paid)} />
            <Figure
              label={t('users.detail.money.owed')}
              value={data.outstanding > 0 ? money(data.outstanding) : '—'}
              tone={data.outstanding > 0 ? 'text-amber-500' : 'text-surface-500'}
            />
            <Figure label={t('users.detail.money.count')} value={String(data.settlementCount)} />
            <Figure
              label={t('users.detail.money.last')}
              value={
                data.lastPayment
                  ? format(parseCalendarDate(data.lastPayment), 'dd.MM.yyyy', { locale })
                  : '—'
              }
            />
          </div>

          <ul className="divide-y divide-surface-800">
            {data.recent.map((line) => (
              <li key={`${line.date}:${line.title}:${line.amount}`} className="flex items-center gap-3 py-1.5 text-xs">
                <span className="w-24 shrink-0 text-surface-400 tabular-nums">
                  {format(parseCalendarDate(line.date), 'dd.MM.yyyy', { locale })}
                </span>
                <span className="flex-1 min-w-0 truncate text-surface-300">
                  {line.title ?? t('users.detail.money.untitled')}
                </span>
                <span className="shrink-0 text-surface-200 tabular-nums">{money(line.amount)}</span>
                <span className="w-24 shrink-0 text-right tabular-nums">
                  {line.settledOn ? (
                    <span className="text-surface-500">
                      {format(parseCalendarDate(line.settledOn), 'dd.MM.yyyy', { locale })}
                    </span>
                  ) : (
                    <span className="text-amber-500">{t('users.detail.money.unpaid')}</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: string }) {
  return (
    <div>
      <div className={`text-lg font-semibold tabular-nums ${tone ?? 'text-surface-100'}`}>{value}</div>
      <div className="text-xs text-surface-400">{label}</div>
    </div>
  )
}
