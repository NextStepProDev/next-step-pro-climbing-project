import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { format } from 'date-fns'
import { AlertTriangle, Building2, ChevronDown, ChevronRight, CircleHelp, Coins, Download, TrendingUp, Users } from 'lucide-react'
import { Button } from '../../components/ui/Button'
import { DateInput } from '../../components/ui/DateInput'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { STATS_FILL, swatchClass } from '../../components/admin/userstats/statsPalette'
import { adminSettlementsApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { parseCalendarDate, todayInWarsaw } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { MAX_PAYOUT_AMOUNT, formatPln, parseAmount } from '../../utils/money'
import type {
  MonthlyRevenue,
  OutstandingItem,
  PersonRevenue,
  PayoutPeriod,
  PayoutSource,
  PayoutsSummary,
  SettlementOverview,
  UnpricedSummary,
} from '../../types'

/**
 * The Settlements tab: what is owed, what came in, and from whom.
 *
 * Everything is drawn from one server response, the same discipline as the user-base statistics:
 * the figures share denominators, and recomputing any of them here would put two counts from two
 * moments on one screen.
 *
 * Two axes, and the screen NAMES them — revenue is counted on the payment date, debt on the
 * session's own date, because an unpaid row has no payment date to be counted on. In practice they
 * agree, since the default payment date is the session date; they part only when the admin
 * overrides it, and then neither figure is lying.
 *
 * Charts are plain divs, like the user statistics and the training ones: a handful of bars is not
 * worth a charting dependency.
 */
export function AdminSettlementsPanel() {
  const { t } = useTranslation('admin')
  const [searchParams, setSearchParams] = useSearchParams()
  const queryClient = useQueryClient()

  // No parameter = the newest year holding data, decided by the server. An empty January of a new
  // year looks exactly like lost history, so "current year" is the wrong default.
  const year = searchParams.get('year') ?? undefined

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'settlements', 'overview', year ?? 'default'],
    queryFn: () => adminSettlementsApi.getOverview(year),
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })

  const selectYear = (next: string) => {
    const params = new URLSearchParams(searchParams)
    if (next === 'default') params.delete('year')
    else params.set('year', next)
    setSearchParams(params, { replace: true })
  }

  if (isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (isError || !data) return <QueryError error={error} onRetry={() => refetch()} />

  // Every source of content, not just settlements: a tab used only for bulk work has no settlement
  // years and no debts, and would otherwise announce itself as empty over a full payouts table.
  const nothingAtAll =
    data.years.length === 0 && data.outstanding.count === 0 && data.unpriced.count === 0
    && data.payouts.periods.length === 0 && data.payouts.sources.length === 0

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-surface-100">{t('settlements.tab.title')}</h2>
        {data.years.length > 0 && (
          <label className="flex items-center gap-2 text-sm text-surface-400">
            {t('settlements.tab.year')}
            <select
              value={data.year === null ? 'all' : String(data.year)}
              onChange={(e) => selectYear(e.target.value)}
              className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
            >
              {data.years.map((available) => (
                <option key={available} value={available}>{available}</option>
              ))}
              <option value="all">{t('settlements.tab.allYears')}</option>
            </select>
          </label>
        )}
      </div>

      {!nothingAtAll && <ExportButton year={data.year} />}

      {nothingAtAll ? (
        <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 space-y-4 text-center text-surface-400">
          <p>{t('settlements.tab.empty')}</p>
          <p className="text-xs text-surface-500">{t('settlements.tab.payouts.setupHint')}</p>
          <div className="flex justify-center">
            <SourceManager sources={data.payouts.sources} onChanged={refresh} />
          </div>
        </div>
      ) : (
        <>
          {/* First, because it is the half that cannot ask for itself: an unpaid amount at least
              exists as a debt, a session nobody priced is invisible everywhere else. */}
          <UnpricedCard unpriced={data.unpriced} />
          <OutstandingCard overview={data} />
          <RevenueCard overview={data} />
          <PayoutsCard payouts={data.payouts} />
          <PeopleCard people={data.people} />
        </>
      )}
    </div>
  )
}

/**
 * Pulls the year's line items and writes a spreadsheet.
 *
 * The rows are fetched on click rather than carried by the overview: the tab renders four cards
 * from aggregates, and making that read haul a year of lines would charge every visit for the one
 * time in January somebody exports.
 */
function ExportButton({ year }: { year: number | null }) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()

  const run = useMutation({
    mutationFn: async () => {
      const [{ exportSettlements }, rows] = await Promise.all([
        // Two levels of laziness, like the logbook export: the module, and the library inside it.
        import('./settlementExport'),
        adminSettlementsApi.getExportRows(
          year === null ? 'all' : String(year),
          t('settlements.tab.export.kindClient'),
          t('settlements.tab.export.kindPayout'),
        ),
      ])
      await exportSettlements({
        rows,
        year,
        labels: {
          summary: t('settlements.tab.export.summary', {
            year: year === null ? t('settlements.tab.allYears') : year,
            generated: format(parseCalendarDate(todayInWarsaw()), 'dd.MM.yyyy', { locale }),
            count: rows.length,
          }),
          columns: [
            t('settlements.tab.export.colKind'),
            t('settlements.tab.export.colDate'),
            t('settlements.tab.export.colTitle'),
            t('settlements.tab.export.colPayer'),
            t('settlements.tab.export.colAmount'),
            t('settlements.tab.export.colSettledOn'),
          ],
          unpaid: t('settlements.tab.export.unpaid'),
        },
      })
    },
  })

  return (
    <div className="flex justify-end">
      <Button size="sm" variant="ghost" loading={run.isPending} onClick={() => run.mutate()}>
        <Download className="w-3.5 h-3.5 mr-1" />
        {t('settlements.tab.export.action')}
      </Button>
    </div>
  )
}

// ---------- shared pieces ----------

function Card({
  title,
  icon: Icon,
  aside,
  children,
}: {
  title: string
  icon: typeof Coins
  aside?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2 text-sm font-medium text-surface-300">
          <Icon className="w-4 h-4 text-surface-400" />
          {title}
        </div>
        {aside}
      </div>
      {children}
    </div>
  )
}

function useMoney() {
  const { i18n } = useTranslation()
  return (amount: number) => formatPln(amount, i18n.language)
}

// ---------- to be priced ----------

/**
 * Sessions that are over and were never priced at all.
 *
 * Grouped per session rather than per person: you collect money from a person, but you PRICE a
 * session, and the modal each row links to prices everyone on it in one go. The count of people
 * still to price is what says whether opening a row is one field or ten.
 */
function UnpricedCard({ unpriced }: { unpriced: UnpricedSummary }) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const location = useLocation()
  const backHere = location.pathname + location.search

  if (unpriced.count === 0) return null

  return (
    <Card
      title={t('settlements.tab.unpriced.title')}
      icon={CircleHelp}
      aside={
        <span className="text-sm font-semibold text-surface-200 tabular-nums">
          {t('settlements.tab.unpriced.count', { n: unpriced.count })}
        </span>
      }
    >
      {/* Two rules at once, and both would otherwise be guessed: this list disobeys the year
          picker, and it stops at the window — so an older session missing from it is policy,
          not a bug. */}
      <p className="text-xs text-surface-500">
        {t('settlements.tab.unpriced.scope', { days: unpriced.windowDays })}
      </p>
      <div className="overflow-x-auto">
        <ul className="min-w-[28rem] divide-y divide-surface-800">
          {unpriced.sessions.map((session) => (
            <li key={`${session.targetType}:${session.targetId}`} className="flex items-center gap-3 py-2">
              <span className="w-24 shrink-0 text-xs text-surface-400 tabular-nums">
                {format(parseCalendarDate(session.date), 'dd.MM.yyyy', { locale })}
              </span>
              <Link
                to={`/calendar?date=${session.date}&${session.targetType}=${session.targetId}`}
                state={{ returnTo: backHere }}
                aria-label={t('settlements.tab.unpriced.open', {
                  date: format(parseCalendarDate(session.date), 'dd.MM.yyyy'),
                })}
                className="flex-1 min-w-0 text-sm text-surface-300 truncate hover:text-primary-300 transition-colors"
              >
                {session.title ?? t(`settlements.tab.outstanding.untitled.${session.targetType}`)}
              </Link>
              <span className="shrink-0 text-xs text-surface-500 tabular-nums">
                {t('settlements.tab.unpriced.people', { n: session.payerCount })}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  )
}

// ---------- outstanding ----------

/**
 * Debts, oldest first — the useful order for a list of things owed is how long they have been owed.
 *
 * The header states in words that this section ignores the year picker above it. Without that line
 * a section that disobeys its own filter is indistinguishable from a filter that does not work.
 */
function OutstandingCard({ overview }: { overview: SettlementOverview }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const { outstanding } = overview

  // Grouped by payer, because that is how the money arrives: one person settles a month at a time,
  // and their four debts scattered among everybody else's by date cannot be acted on as one.
  const groups = useMemo(() => {
    const byPayer = new Map<string, { key: string; name: string; items: OutstandingItem[]; total: number }>()
    for (const item of outstanding.items) {
      const key = `${item.payerType}:${item.payerId}`
      const group = byPayer.get(key) ?? { key, name: item.name, items: [], total: 0 }
      group.items.push(item)
      group.total += item.amount
      byPayer.set(key, group)
    }
    // Oldest debt first, same order as the flat list had — a backlog reads in the order it grew.
    return [...byPayer.values()]
  }, [outstanding.items])

  return (
    <Card
      title={t('settlements.tab.outstanding.title')}
      icon={AlertTriangle}
      aside={
        outstanding.count > 0 ? (
          <span className="text-sm text-surface-200 tabular-nums">
            <span className="font-semibold text-amber-500">{money(outstanding.total)}</span>
            <span className="text-surface-500">
              {' · '}
              {t('settlements.tab.outstanding.count', { n: outstanding.count })}
              {outstanding.oldest && (
                <>
                  {' · '}
                  {t('settlements.tab.outstanding.oldest', {
                    date: format(parseCalendarDate(outstanding.oldest), 'dd.MM.yyyy'),
                  })}
                </>
              )}
            </span>
          </span>
        ) : undefined
      }
    >
      {outstanding.count === 0 ? (
        <p className="text-sm text-surface-400">{t('settlements.tab.outstanding.none')}</p>
      ) : (
        <>
          <p className="text-xs text-surface-500">{t('settlements.tab.outstanding.ignoresYear')}</p>
          <ul className="divide-y divide-surface-800">
            {groups.map((group) => (
              <PayerDebtGroup key={group.key} group={group} />
            ))}
          </ul>
        </>
      )}
    </Card>
  )
}

/**
 * One person and everything they owe.
 *
 * ⚠️ The payment date defaults to TODAY here, not to each session's own day as the modal does — and
 * the difference is the point. In the modal one amount belongs to one session, so its date is the
 * honest default. Here one transfer covered a month of them, so the only date true of all of them
 * is the day it arrived. Defaulting to the sessions would scatter a single payment across the
 * months it paid for.
 */
function PayerDebtGroup({
  group,
}: {
  group: { key: string; name: string; items: OutstandingItem[]; total: number }
}) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [paidOn, setPaidOn] = useState(() => todayInWarsaw())
  // What actually changed hands, defaulting to what is owed — the common case is one click, and the
  // field is there for the times a note is bigger than the bill.
  const [received, setReceived] = useState(() => String(group.total))

  const first = group.items[0]
  const backHere = location.pathname + location.search

  const settleAll = useMutation({
    mutationFn: () =>
      adminSettlementsApi.settleOutstanding(
        first.payerType, first.payerId, paidOn, parseAmount(received, MAX_PAYOUT_AMOUNT) ?? 0),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] }),
  })

  return (
    <li className="py-2 space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="flex flex-1 min-w-0 items-center gap-1.5 text-left text-sm text-surface-200 hover:text-surface-100 transition-colors"
        >
          {open ? <ChevronDown className="w-4 h-4 shrink-0" /> : <ChevronRight className="w-4 h-4 shrink-0" />}
          <span className="truncate">{group.name}</span>
          <span className="text-surface-500 shrink-0">
            · {t('settlements.tab.outstanding.sessions', { n: group.items.length })}
          </span>
        </button>
        <span className="shrink-0 text-sm font-semibold text-amber-500 tabular-nums">
          {money(group.total)}
        </span>
        <input
          inputMode="decimal"
          value={received}
          onChange={(e) => setReceived(e.target.value)}
          aria-label={t('settlements.tab.outstanding.receivedLabel', { name: group.name })}
          className="w-24 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
        />
        <DateInput
          value={paidOn}
          onChange={setPaidOn}
          aria-label={t('settlements.tab.outstanding.paidOnLabel', { name: group.name })}
          className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
        />
        <Button
          size="sm"
          variant="primary"
          loading={settleAll.isPending}
          disabled={paidOn === '' || parseAmount(received, MAX_PAYOUT_AMOUNT) === null}
          onClick={() => settleAll.mutate()}
        >
          {t('settlements.tab.outstanding.settleAll', { amount: money(group.total) })}
        </Button>
      </div>

      {open && (
        <ul className="pl-6 space-y-1">
          {group.items.map((item) => (
            <li
              key={`${item.targetType}:${item.targetId}`}
              className="flex flex-wrap items-center gap-2 text-xs"
            >
              <span className="w-24 shrink-0 text-surface-400 tabular-nums">
                {format(parseCalendarDate(item.date), 'dd.MM.yyyy', { locale })}
              </span>
              {/* A standing fee has no calendar entry behind it, so it is text — a link that goes
                  nowhere is worse than no link. */}
              {item.targetId === null ? (
                <span className="flex-1 min-w-0 truncate text-surface-300">
                  {t('settlements.tab.outstanding.untitled.month')}
                </span>
              ) : (
                <Link
                  to={`/calendar?date=${item.date}&${item.targetType}=${item.targetId}`}
                  state={{ returnTo: backHere }}
                  aria-label={t('settlements.tab.outstanding.open', {
                    name: item.name,
                    date: format(parseCalendarDate(item.date), 'dd.MM.yyyy'),
                  })}
                  className="flex-1 min-w-0 truncate text-surface-300 hover:text-primary-300 transition-colors"
                >
                  {item.title ?? t(`settlements.tab.outstanding.untitled.${item.targetType}`)}
                </Link>
              )}
              <span className="shrink-0 text-amber-500 tabular-nums">{money(item.amount)}</span>
            </li>
          ))}
        </ul>
      )}

      {settleAll.isError && (
        <p className="text-sm text-rose-400/80">{getErrorMessage(settleAll.error)}</p>
      )}
    </li>
  )
}

// ---------- revenue ----------

function RevenueCard({ overview }: { overview: SettlementOverview }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const { revenue } = overview

  const tiles: { key: string; value: number }[] = [
    { key: 'total', value: revenue.total },
    ...(revenue.monthlyAverage !== null ? [{ key: 'average', value: revenue.monthlyAverage }] : []),
  ]

  // Only when there is something to compare against: a year with no predecessor would otherwise
  // read as "-100%", which says the business collapsed rather than that it had not started.
  const comparable = revenue.previousMonths.length > 0 && revenue.previousTotal > 0
  const change = comparable
    ? Math.round(((revenue.total - revenue.previousTotal) / revenue.previousTotal) * 100)
    : null

  return (
    <Card
      title={t('settlements.tab.revenue.title')}
      icon={TrendingUp}
      aside={<span className="text-xs text-surface-500">{t('settlements.tab.revenue.axis')}</span>}
    >
      <div className="grid grid-cols-2 gap-3">
        {tiles.map((tile) => (
          <div key={tile.key} className="bg-surface-800/50 rounded-lg p-3">
            <div className="text-xl font-bold text-surface-100 tabular-nums">{money(tile.value)}</div>
            <div className="text-xs text-surface-400">{t(`settlements.tab.revenue.${tile.key}`)}</div>
          </div>
        ))}
      </div>

      {change !== null && (
        <p className="text-xs text-surface-400">
          {/* Against the SAME months a year earlier, never against last month: climbing is
              seasonal, so a month-over-month arrow is a confident wrong reading. */}
          <span className={change >= 0 ? 'text-green-400' : 'text-amber-500'}>
            {change >= 0 ? '▲' : '▼'} {Math.abs(change)}%
          </span>{' '}
          {t('settlements.tab.revenue.vsLastYear', {
            previous: money(revenue.previousTotal),
          })}
        </p>
      )}

      <RevenueChart months={revenue.months} previousMonths={revenue.previousMonths} />

      {revenue.total > 0 && (
        <div className="space-y-2">
          <div className="flex gap-0.5 h-3 rounded-full overflow-hidden bg-surface-800">
            {revenue.fromSlots > 0 && (
              <div
                className={STATS_FILL.done}
                style={{ width: `${(100 * revenue.fromSlots) / revenue.total}%` }}
              />
            )}
            {revenue.fromEvents > 0 && (
              <div
                className={STATS_FILL.pending}
                style={{ width: `${(100 * revenue.fromEvents) / revenue.total}%` }}
              />
            )}
          </div>
          {/* Every segment's number is in the legend, which is what lets the bar use fills that sit
              under the 3:1 line in one of the two themes — identity never rests on colour alone. */}
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-surface-400">
            <span className="inline-flex items-center gap-1.5">
              <span className={swatchClass(STATS_FILL.done)} />
              {t('settlements.tab.revenue.fromSlots')}
              <span className="text-surface-200 tabular-nums">{money(revenue.fromSlots)}</span>
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className={swatchClass(STATS_FILL.pending)} />
              {t('settlements.tab.revenue.fromEvents')}
              <span className="text-surface-200 tabular-nums">{money(revenue.fromEvents)}</span>
            </span>
          </div>
        </div>
      )}
    </Card>
  )
}

/**
 * Twelve buckets, always.
 *
 * Empty months are drawn as a baseline tick rather than left out: a missing column reads as missing
 * data, a flat tick reads as a month when nothing came in — which is the fact.
 */
function RevenueChart({
  months,
  previousMonths,
}: {
  months: MonthlyRevenue[]
  previousMonths: MonthlyRevenue[]
}) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  // Both years share one scale, or the comparison bar would lie about its own height.
  const max = Math.max(
    1,
    ...months.map((bucket) => bucket.amount),
    ...previousMonths.map((bucket) => bucket.amount),
  )

  return (
    <>
      <div className="flex items-end gap-1 h-32">
        {months.map((bucket, index) => {
          const month = parseCalendarDate(bucket.month)
          const previous = previousMonths[index]
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
                {previous !== undefined && (
                  <span className="text-surface-400">
                    {' · '}
                    {t('settlements.tab.revenue.lastYearTooltip', { amount: money(previous.amount) })}
                  </span>
                )}
              </div>
              {/* Last year as a faint ghost behind this one: a second full bar would double the
                  chart's density for a number that is context, not the subject. */}
              {previous !== undefined && previous.amount > 0 && (
                <div
                  className="absolute inset-x-0 bottom-0 rounded-t bg-surface-700"
                  style={{ height: `${(previous.amount / max) * 100}%` }}
                />
              )}
              {bucket.amount > 0 ? (
                <div
                  className={`relative rounded-t ${STATS_FILL.done}`}
                  style={{ height: `${(bucket.amount / max) * 100}%` }}
                />
              ) : (
                <div className="relative h-0.5 rounded-full bg-surface-800" />
              )}
            </div>
          )
        })}
      </div>
      <div className="flex gap-1">
        {months.map((bucket) => (
          <div key={bucket.month} className="flex-1 text-center text-[10px] text-surface-500">
            {format(parseCalendarDate(bucket.month), 'LLL', { locale })}
          </div>
        ))}
      </div>
    </>
  )
}

// ---------- bulk payouts ----------

/**
 * Work somebody else settles for a whole month at once.
 *
 * The table lists the union of both sides on purpose: a month with sessions and no transfer is the
 * invoice nobody has paid, and a transfer with no marked sessions says the calendar was not filled
 * in. Only when both halves are there is a rate shown — that figure is why this exists at all.
 */
function PayoutsCard({ payouts }: { payouts: PayoutsSummary }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const queryClient = useQueryClient()
  const [adding, setAdding] = useState(false)

  const active = payouts.sources.filter((source) => !source.archived)
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] })

  if (payouts.sources.length === 0 && payouts.periods.length === 0) return null

  return (
    <Card
      title={t('settlements.tab.payouts.title')}
      icon={Building2}
      aside={
        payouts.total > 0 ? (
          <span className="text-sm text-surface-200 tabular-nums">{money(payouts.total)}</span>
        ) : undefined
      }
    >
      <p className="text-xs text-surface-500">{t('settlements.tab.payouts.axis')}</p>

      {payouts.periods.length > 0 && (
        <div className="relative overflow-x-auto">
          <table className="min-w-[34rem] w-full text-sm">
            <thead>
              <tr className="text-xs text-surface-500 text-left">
                <th className="py-1 font-normal">{t('settlements.tab.payouts.payer')}</th>
                <th className="py-1 font-normal">{t('settlements.tab.payouts.period')}</th>
                <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.sessions')}</th>
                <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.hours')}</th>
                <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.amount')}</th>
                <th className="py-1 font-normal text-right">{t('settlements.tab.payouts.rate')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-800">
              {payouts.periods.map((period) => (
                <PayoutPeriodRow key={`${period.sourceId}:${period.month}`} period={period} onChanged={refresh} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      {adding ? (
        <PayoutForm
          sources={active}
          onCancel={() => setAdding(false)}
          onSaved={() => {
            setAdding(false)
            refresh()
          }}
        />
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="secondary" onClick={() => setAdding(true)} disabled={active.length === 0}>
            {t('settlements.tab.payouts.add')}
          </Button>
          <SourceManager sources={payouts.sources} onChanged={refresh} />
        </div>
      )}
    </Card>
  )
}

/**
 * One month of one payer, expandable into the transfers it adds up.
 *
 * The expansion is not decoration: the row is an aggregate, so without a way down to the individual
 * arrivals a mistyped 14000 for 1400 would be permanent.
 */
function PayoutPeriodRow({ period, onChanged }: { period: PayoutPeriod; onChanged: () => void }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const [open, setOpen] = useState(false)

  const remove = useMutation({
    mutationFn: (payoutId: string) => adminSettlementsApi.deletePayout(payoutId),
    onSuccess: onChanged,
  })

  return (
    <>
      <tr>
        <td className="py-2 text-surface-200">{period.sourceName}</td>
        <td className="py-2 text-surface-400">
          {period.transfers.length > 0 ? (
            <button
              type="button"
              onClick={() => setOpen((v) => !v)}
              aria-expanded={open}
              className="hover:text-primary-300 transition-colors"
            >
              {format(parseCalendarDate(period.month), 'LLLL yyyy', { locale })}
            </button>
          ) : (
            format(parseCalendarDate(period.month), 'LLLL yyyy', { locale })
          )}
        </td>
        <td className="py-2 text-right text-surface-400 tabular-nums">{period.sessions}</td>
        <td className="py-2 text-right text-surface-400 tabular-nums">
          {(period.minutes / 60).toFixed(1)}
          {/* The denominator's own gap, stated rather than hidden. */}
          {period.sessionsWithoutHours > 0 && (
            <span className="text-amber-500">
              {' '}
              {t('settlements.tab.payouts.withoutHours', { n: period.sessionsWithoutHours })}
            </span>
          )}
        </td>
        <td className="py-2 text-right tabular-nums">
          {period.amount > 0 ? (
            <span className="text-surface-200">{money(period.amount)}</span>
          ) : (
            /* Work done, nothing received: the row people actually come here for. */
            <span className="text-amber-500">{t('settlements.tab.payouts.awaiting')}</span>
          )}
        </td>
        <td className="py-2 text-right text-surface-200 tabular-nums">
          {period.ratePerHour === null ? (
            <span className="text-surface-500">—</span>
          ) : (
            money(period.ratePerHour)
          )}
        </td>
      </tr>
      {open && period.transfers.map((transfer) => (
        <tr key={transfer.id} className="text-xs">
          <td />
          <td className="py-1 pl-4 text-surface-500" colSpan={2}>
            {t('settlements.tab.payouts.received', {
              date: format(parseCalendarDate(transfer.receivedOn), 'dd.MM.yyyy', { locale }),
            })}
          </td>
          <td className="py-1 text-right text-surface-300 tabular-nums">{money(transfer.amount)}</td>
          <td className="py-1 text-right">
            <button
              type="button"
              onClick={() => remove.mutate(transfer.id)}
              aria-label={t('settlements.tab.payouts.deleteTransfer', {
                amount: money(transfer.amount),
              })}
              className="text-surface-500 hover:text-rose-400 transition-colors"
            >
              ×
            </button>
          </td>
        </tr>
      ))}
    </>
  )
}

/** Two dates because they answer different questions — what the money is for, and when it landed. */
function PayoutForm({
  sources,
  onCancel,
  onSaved,
}: {
  sources: PayoutSource[]
  onCancel: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation('admin')
  const [sourceId, setSourceId] = useState(sources[0]?.id ?? '')
  const [periodMonth, setPeriodMonth] = useState('')
  const [amount, setAmount] = useState('')
  const [receivedOn, setReceivedOn] = useState('')

  const parsed = parseAmount(amount, MAX_PAYOUT_AMOUNT)
  const ready = sourceId !== '' && periodMonth !== '' && receivedOn !== '' && parsed !== null

  const create = useMutation({
    mutationFn: () =>
      adminSettlementsApi.createPayout(sourceId, periodMonth, parsed as number, receivedOn),
    onSuccess: onSaved,
  })

  return (
    <div className="space-y-2 rounded-lg border border-surface-800 p-3">
      <div className="flex flex-wrap items-end gap-2">
        <label className="flex flex-col gap-1 text-xs text-surface-400">
          {t('settlements.tab.payouts.payer')}
          <select
            value={sourceId}
            onChange={(e) => setSourceId(e.target.value)}
            className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
          >
            {sources.map((source) => (
              <option key={source.id} value={source.id}>{source.name}</option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-xs text-surface-400">
          {/* Any day of it: the server snaps to the first, so this is a month picker without
              needing input[type=month], which desktop Safari does not implement. */}
          {t('settlements.tab.payouts.periodField')}
          <DateInput
            value={periodMonth}
            onChange={setPeriodMonth}
            className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-surface-400">
          {t('settlements.tab.payouts.amountField')}
          <input
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            className="w-28 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-surface-400">
          {t('settlements.tab.payouts.receivedField')}
          <DateInput
            value={receivedOn}
            onChange={setReceivedOn}
            className="bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
          />
        </label>
      </div>
      {create.isError && (
        <p className="text-sm text-rose-400/80">{getErrorMessage(create.error)}</p>
      )}
      <div className="flex justify-end gap-2">
        <Button size="sm" variant="ghost" onClick={onCancel}>
          {t('settlements.section.cancel')}
        </Button>
        <Button
          size="sm"
          variant="primary"
          disabled={!ready}
          loading={create.isPending}
          onClick={() => create.mutate()}
        >
          {t('settlements.actions.save')}
        </Button>
      </div>
    </div>
  )
}

/**
 * Adding, renaming, archiving and restoring payers, inline — a separate admin screen for a list of
 * three names would cost more navigation than it saves.
 *
 * Archived ones stay listed and restorable. Archiving is otherwise a one-way door, and the only way
 * back would be creating a namesake, which orphans the history under the old row.
 */
function SourceManager({ sources, onChanged }: { sources: PayoutSource[]; onChanged: () => void }) {
  const { t } = useTranslation('admin')
  const [name, setName] = useState('')
  const [editing, setEditing] = useState<string | null>(null)
  const [draft, setDraft] = useState('')

  const create = useMutation({
    mutationFn: () => adminSettlementsApi.createSource(name.trim()),
    onSuccess: () => {
      setName('')
      onChanged()
    },
  })
  const rename = useMutation({
    mutationFn: (sourceId: string) => adminSettlementsApi.renameSource(sourceId, draft.trim()),
    onSuccess: () => {
      setEditing(null)
      onChanged()
    },
  })
  const setArchived = useMutation({
    mutationFn: ({ id, archived }: { id: string; archived: boolean }) =>
      adminSettlementsApi.setSourceArchived(id, archived),
    onSuccess: onChanged,
  })

  return (
    <div className="flex flex-wrap items-center gap-2">
      {sources.map((source) =>
        editing === source.id ? (
          <span key={source.id} className="inline-flex items-center gap-1">
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              aria-label={t('settlements.tab.payouts.rename', { name: source.name })}
              className="w-40 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-xs text-surface-100 focus:outline-none focus:border-primary-500"
            />
            <Button
              size="sm"
              variant="ghost"
              disabled={draft.trim() === ''}
              loading={rename.isPending}
              onClick={() => rename.mutate(source.id)}
            >
              {t('settlements.actions.save')}
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setEditing(null)}>
              {t('settlements.section.cancel')}
            </Button>
          </span>
        ) : (
          <span
            key={source.id}
            className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs ${
              source.archived
                ? 'border-surface-800 text-surface-500'
                : 'border-surface-700 text-surface-300'
            }`}
          >
            <button
              type="button"
              onClick={() => {
                setEditing(source.id)
                setDraft(source.name)
              }}
              className="hover:text-primary-300 transition-colors"
            >
              {source.name}
            </button>
            <button
              type="button"
              onClick={() => setArchived.mutate({ id: source.id, archived: !source.archived })}
              aria-label={t(
                source.archived
                  ? 'settlements.tab.payouts.restore'
                  : 'settlements.tab.payouts.archive',
                { name: source.name },
              )}
              className="text-surface-500 hover:text-rose-400 transition-colors"
            >
              {source.archived ? '↩' : '×'}
            </button>
          </span>
        ),
      )}
      <input
        value={name}
        onChange={(e) => setName(e.target.value)}
        placeholder={t('settlements.tab.payouts.newPayer')}
        aria-label={t('settlements.tab.payouts.newPayer')}
        className="w-40 bg-surface-800 border border-surface-600 rounded px-2 py-1 text-xs text-surface-100 focus:outline-none focus:border-primary-500"
      />
      <Button
        size="sm"
        variant="ghost"
        disabled={name.trim() === ''}
        loading={create.isPending}
        onClick={() => create.mutate()}
      >
        {t('settlements.tab.payouts.addPayer')}
      </Button>
      {(create.isError || rename.isError) && (
        <span className="text-xs text-rose-400/80">
          {getErrorMessage(create.error ?? rename.error)}
        </span>
      )}
    </div>
  )
}

// ---------- people ----------

function PeopleCard({ people }: { people: PersonRevenue[] }) {
  const { t } = useTranslation('admin')
  const money = useMoney()

  if (people.length === 0) return null

  return (
    <Card title={t('settlements.tab.people.title')} icon={Users}>
      {/* The card above deliberately ignores the year picker and this one obeys it, so a debt from
          an earlier year shows there and not here. Unexplained, that reads as one of them lying. */}
      <p className="text-xs text-surface-500">{t('settlements.tab.people.scope')}</p>
      {/* relative: an sr-only cell inside a wide scroller resolves against the DOCUMENT without a
          positioned ancestor, and pushes the whole page sideways on a phone. */}
      <div className="relative overflow-x-auto">
        <table className="min-w-[34rem] w-full text-sm">
          <thead>
            <tr className="text-xs text-surface-500 text-left">
              <th className="py-1 font-normal">{t('settlements.tab.people.person')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.people.count')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.people.paid')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.people.owed')}</th>
              <th className="py-1 font-normal text-right">{t('settlements.tab.people.last')}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-800">
            {people.map((person) => (
              <tr key={`${person.payerType}:${person.userId ?? person.name}`}>
                <td className="py-2 text-surface-200">
                  {/* A guest has no account, so there is no card to link to — the null userId IS
                      that signal, rather than a separate flag to keep in step. */}
                  {person.userId ? (
                    <Link
                      to={`/admin/users/${person.userId}`}
                      className="text-primary-400 hover:text-primary-300 transition-colors"
                    >
                      {person.name}
                    </Link>
                  ) : (
                    <>
                      {person.name}
                      <span className="text-surface-500"> · {t('settlements.line.guest')}</span>
                    </>
                  )}
                </td>
                <td className="py-2 text-right text-surface-400 tabular-nums">{person.settlementCount}</td>
                <td className="py-2 text-right text-surface-200 tabular-nums">
                  {person.paid > 0 ? money(person.paid) : '—'}
                </td>
                <td className="py-2 text-right tabular-nums">
                  {person.outstanding > 0 ? (
                    <span className="text-amber-500">{money(person.outstanding)}</span>
                  ) : (
                    <span className="text-surface-500">—</span>
                  )}
                </td>
                <td className="py-2 text-right text-surface-400 tabular-nums">
                  {person.lastPayment
                    ? format(parseCalendarDate(person.lastPayment), 'dd.MM.yyyy')
                    : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}
