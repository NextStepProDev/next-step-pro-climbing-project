import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format } from 'date-fns'
import { ArrowLeft, Building2 } from 'lucide-react'
import { adminSettlementsApi } from '../../api/client'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { PayoutPeriodRow } from '../../components/admin/PayoutPeriodRow'
import { useMoney } from '../../components/admin/useMoney'
import { STATS_FILL } from '../../components/admin/userstats/statsPalette'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { MonthlyRevenue, PayoutSourceHistory, PayoutYear } from '../../types'

/**
 * One institution's whole history: what they had, what they paid, and how that moved.
 *
 * <p>A screen of its own rather than a modal, the same shape as a client's card: it carries a chart
 * and two tables, it is worth linking to, and the back arrow is a cheaper way out than a close
 * button on a phone.
 *
 * <p><b>All-time, deliberately deaf to the year filter on the tab behind it.</b> "What do I have
 * with this place" has no year in it — and the years are broken out below anyway, so filtering
 * would only take history away. The year the admin was looking at travels in the back link, so
 * going back returns to the tab as they left it.
 */
export function AdminPayoutSourcePanel() {
  const { t } = useTranslation('admin')
  const { sourceId } = useParams<{ sourceId: string }>()
  const [searchParams] = useSearchParams()
  const queryClient = useQueryClient()

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'settlements', 'source', sourceId],
    queryFn: () => adminSettlementsApi.getSourceHistory(sourceId!),
    enabled: !!sourceId,
  })

  // The tab's own year, carried through so the back arrow returns to the view it was left in.
  const year = searchParams.get('year')
  const backTo = year ? `/admin/settlements?year=${year}` : '/admin/settlements'

  if (isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (isError || !data) return <QueryError error={error} onRetry={() => refetch()} />

  return (
    <div className="space-y-4">
      <Link
        to={backTo}
        className="inline-flex items-center gap-2 text-sm text-surface-400 hover:text-surface-200 transition-colors"
      >
        <ArrowLeft className="w-4 h-4" />
        {t('settlements.tab.title')}
      </Link>

      <Header history={data} />

      {data.periods.length === 0 ? (
        /* A payer who has just been created is a real state — and the way out of it is the calendar,
           not this screen, so it says what to do rather than drawing empty axes. */
        <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center space-y-2">
          <p className="text-surface-400">{t('settlements.source.empty')}</p>
          <p className="text-xs text-surface-500">{t('settlements.source.emptyHint')}</p>
        </div>
      ) : (
        <>
          <MonthlyChart chart={data.chart} />
          <YearTable years={data.years} />
          <PeriodTable
            history={data}
            onChanged={() => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })}
          />
        </>
      )}
    </div>
  )
}

/** Who they are, how long it has run, and the four figures the whole screen is about. */
function Header({ history }: { history: PayoutSourceHistory }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Building2 className="w-5 h-5 text-surface-400 shrink-0" />
        <h2 className="text-lg font-semibold text-surface-100">{history.name}</h2>
        {/* Archived is stated, not hidden: the money it earned is still on this screen, and a name
            with no explanation next to old figures reads as a payer who simply stopped. */}
        {history.archived && (
          <span className="px-2 py-0.5 text-xs rounded bg-surface-800 text-surface-400">
            {t('settlements.source.archived')}
          </span>
        )}
      </div>

      {history.firstActivity && (
        <p className="text-xs text-surface-500">
          {t('settlements.source.since', {
            date: format(parseCalendarDate(history.firstActivity), 'LLLL yyyy', { locale }),
            months: history.months,
          })}
        </p>
      )}

      <dl className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <Figure label={t('settlements.source.received')} value={money(history.totalAmount)} />
        <Figure
          label={t('settlements.source.hours')}
          value={(history.totalMinutes / 60).toFixed(1)}
          /* The denominator's own gap, named here as it is in every row below. */
          note={history.sessionsWithoutHours > 0
            ? t('settlements.tab.payouts.withoutHours', { n: history.sessionsWithoutHours })
            : undefined}
        />
        <Figure label={t('settlements.source.sessions')} value={String(history.totalSessions)} />
        <Figure
          label={t('settlements.source.averageRate')}
          /* Null rather than zero when either half is missing — a rate needs both, and this is the
             figure the whole feature exists for, so it must not be invented. */
          value={history.averageRatePerHour === null ? '—' : money(history.averageRatePerHour)}
        />
      </dl>
    </div>
  )
}

function Figure({ label, value, note }: { label: string; value: string; note?: string }) {
  return (
    <div>
      <dt className="text-xs text-surface-500">{label}</dt>
      <dd className="text-lg font-semibold text-surface-100 tabular-nums">
        {value}
        {note && <span className="ml-1 text-xs font-normal text-amber-500">{note}</span>}
      </dd>
    </div>
  )
}

/**
 * A bar per month of the collaboration, oldest first.
 *
 * ⚠️ Bucketed by the month the work was FOR, not by the day the money landed — unlike revenue
 * everywhere else here. It sits directly above rows that are period months, and two axes on one
 * screen disagree with each other in front of the reader. The heading says which one this is.
 *
 * Gaps are drawn as gaps: a month with nothing in it is a fact about the collaboration, and closing
 * it up would draw a busier partner than the data has.
 */
function MonthlyChart({ chart }: { chart: MonthlyRevenue[] }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const max = Math.max(1, ...chart.map((bucket) => bucket.amount))

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div>
        <h3 className="text-sm font-medium text-surface-300">{t('settlements.source.chart')}</h3>
        <p className="text-xs text-surface-500">{t('settlements.source.chartAxis')}</p>
      </div>
      {/* A long collaboration outgrows the width rather than squeezing into it: bars thinner than a
          finger stop being readable, and this is a screen for reading. */}
      <div className="overflow-x-auto">
        <div className="min-w-full" style={{ width: `${Math.max(100, chart.length * 28)}px` }}>
          <div className="flex items-end gap-1 h-32">
            {chart.map((bucket) => {
              const month = parseCalendarDate(bucket.month)
              return (
                <div
                  key={bucket.month}
                  className="group relative flex-1 h-full flex flex-col justify-end"
                  role="img"
                  aria-label={`${format(month, 'LLLL yyyy', { locale })}: ${money(bucket.amount)}`}
                >
                  <div className="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1 hidden group-hover:block z-10 px-2 py-1 rounded-md bg-surface-800 border border-surface-700 text-xs text-surface-200 whitespace-nowrap shadow-lg">
                    <span className="font-medium">{format(month, 'LLLL yyyy', { locale })}</span>
                    {' · '}
                    {money(bucket.amount)}
                  </div>
                  {bucket.amount > 0 ? (
                    <div
                      className={`relative rounded-t ${STATS_FILL.done}`}
                      style={{ height: `${(bucket.amount / max) * 100}%` }}
                    />
                  ) : (
                    /* A month with nothing, drawn as a hairline rather than as nothing: an absent
                       bar and an absent month look the same, and only one of them is true here. */
                    <div className="relative h-0.5 rounded-full bg-surface-800" />
                  )}
                </div>
              )
            })}
          </div>
          <div className="flex gap-1 mt-1">
            {chart.map((bucket) => (
              <div key={bucket.month} className="flex-1 text-center text-[10px] text-surface-500">
                {/* January carries its year, so a chart spanning several does not need a legend. */}
                {format(parseCalendarDate(bucket.month),
                  parseCalendarDate(bucket.month).getMonth() === 0 ? 'LLL yy' : 'LLL', { locale })}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

/** Newest year first, like every list on this tab. */
function YearTable({ years }: { years: PayoutYear[] }) {
  const { t } = useTranslation('admin')
  const money = useMoney()

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <h3 className="text-sm font-medium text-surface-300">{t('settlements.source.years')}</h3>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[26rem] text-sm">
          <thead>
            <tr className="text-left text-xs text-surface-500 border-b border-surface-800">
              <th className="py-1 font-normal">{t('settlements.source.year')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.sessions')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.hours')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.amount')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.rate')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-800">
            {years.map((year) => (
              <tr key={year.year}>
                <td className="py-2 text-surface-200 tabular-nums">{year.year}</td>
                <td className="py-2 text-right text-surface-400 tabular-nums">{year.sessions}</td>
                <td className="py-2 text-right text-surface-400 tabular-nums">
                  {(year.minutes / 60).toFixed(1)}
                  {year.sessionsWithoutHours > 0 && (
                    <span className="text-amber-500">
                      {' '}
                      {t('settlements.tab.payouts.withoutHours', { n: year.sessionsWithoutHours })}
                    </span>
                  )}
                </td>
                <td className="py-2 text-right tabular-nums">
                  {year.amount > 0 ? (
                    <span className="text-surface-200">{money(year.amount)}</span>
                  ) : (
                    <span className="text-amber-500">{t('settlements.tab.payouts.awaiting')}</span>
                  )}
                </td>
                <td className="py-2 text-right text-surface-200 tabular-nums">
                  {year.ratePerHour === null ? (
                    <span className="text-surface-500">—</span>
                  ) : (
                    money(year.ratePerHour)
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/**
 * The months themselves — the same rows the tab draws, expandable into their sessions and
 * transfers, with the transfers still deletable.
 *
 * One renderer for both screens on purpose: a figure cannot mean one thing on the list and another
 * on the screen you reach by clicking it.
 */
function PeriodTable({ history, onChanged }: { history: PayoutSourceHistory; onChanged: () => void }) {
  const { t } = useTranslation('admin')

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <h3 className="text-sm font-medium text-surface-300">{t('settlements.source.months')}</h3>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[32rem] text-sm">
          <thead>
            <tr className="text-left text-xs text-surface-500 border-b border-surface-800">
              <th className="py-1 font-normal">{t('settlements.tab.payouts.payer')}</th>
              <th className="py-1 font-normal">{t('settlements.tab.payouts.period')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.sessions')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.hours')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.amount')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.rate')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-800">
            {history.periods.map((period) => (
              <PayoutPeriodRow
                key={`${period.sourceId}:${period.month}`}
                period={period}
                onChanged={onChanged}
                linkPayer={false}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
