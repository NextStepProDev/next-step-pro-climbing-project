import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { format } from 'date-fns'
import { AlertTriangle, CircleHelp, Coins, TrendingUp, Users } from 'lucide-react'
import { Button } from '../../components/ui/Button'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { STATS_FILL, swatchClass } from '../../components/admin/userstats/statsPalette'
import { adminSettlementsApi } from '../../api/client'
import { parseCalendarDate } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { formatPln } from '../../utils/money'
import type {
  MonthlyRevenue,
  OutstandingItem,
  PersonRevenue,
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

  // No parameter = the newest year holding data, decided by the server. An empty January of a new
  // year looks exactly like lost history, so "current year" is the wrong default.
  const year = searchParams.get('year') ?? undefined

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'settlements', 'overview', year ?? 'default'],
    queryFn: () => adminSettlementsApi.getOverview(year),
  })

  const selectYear = (next: string) => {
    const params = new URLSearchParams(searchParams)
    if (next === 'default') params.delete('year')
    else params.set('year', next)
    setSearchParams(params, { replace: true })
  }

  if (isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (isError || !data) return <QueryError error={error} onRetry={() => refetch()} />

  const nothingAtAll =
    data.years.length === 0 && data.outstanding.count === 0 && data.unpriced.count === 0

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

      {nothingAtAll ? (
        <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center text-surface-400">
          {t('settlements.tab.empty')}
        </div>
      ) : (
        <>
          {/* First, because it is the half that cannot ask for itself: an unpaid amount at least
              exists as a debt, a session nobody priced is invisible everywhere else. */}
          <UnpricedCard unpriced={data.unpriced} />
          <OutstandingCard overview={data} />
          <RevenueCard overview={data} />
          <PeopleCard people={data.people} />
        </>
      )}
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

/** Two debts of the same person are two rows, so identity is the whole address, not the payer. */
function isSameDebt(a: OutstandingItem | undefined, b: OutstandingItem): boolean {
  return a !== undefined
    && a.targetType === b.targetType && a.targetId === b.targetId
    && a.payerType === b.payerType && a.payerId === b.payerId
}

/**
 * Debts, oldest first — the useful order for a list of things owed is how long they have been owed.
 *
 * The header states in words that this section ignores the year picker above it. Without that line
 * a section that disobeys its own filter is indistinguishable from a filter that does not work.
 */
function OutstandingCard({ overview }: { overview: SettlementOverview }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const queryClient = useQueryClient()
  const location = useLocation()
  // Carries the year filter too, so closing the modal returns to the same view of the tab.
  const backHere = location.pathname + location.search

  const settle = useMutation({
    // Paid on the day of the session: the same default the modal offers, and the one that puts the
    // money in the month the work happened.
    mutationFn: (item: OutstandingItem) =>
      adminSettlementsApi.save(
        item.targetType, item.targetId, item.payerType, item.payerId, item.amount, item.date,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] }),
  })

  const { outstanding } = overview

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
          <div className="overflow-x-auto">
            <ul className="min-w-[32rem] divide-y divide-surface-800">
              {outstanding.items.map((item) => (
                <li
                  key={`${item.targetType}:${item.targetId}:${item.payerType}:${item.payerId}`}
                  className="flex items-center gap-3 py-2"
                >
                  <span className="w-24 shrink-0 text-xs text-surface-400 tabular-nums">
                    {format(parseCalendarDate(item.date), 'dd.MM.yyyy', { locale })}
                  </span>
                  {/* Straight into the entry, so collecting a debt does not start with hunting
                      through the calendar for the day it was on. Uses the existing deep link the
                      athlete calendar's invitation overlay already relies on — `?slot=`/`?event=`
                      opens that modal, and `returnTo` brings closing it back here rather than
                      stranding the admin on the public calendar. */}
                  <Link
                    to={`/calendar?date=${item.date}&${item.targetType}=${item.targetId}`}
                    state={{ returnTo: backHere }}
                    aria-label={t('settlements.tab.outstanding.open', {
                      name: item.name,
                      date: format(parseCalendarDate(item.date), 'dd.MM.yyyy'),
                    })}
                    className="flex-1 min-w-0 text-sm text-surface-300 truncate hover:text-primary-300 transition-colors"
                  >
                    {item.title ?? t(`settlements.tab.outstanding.untitled.${item.targetType}`)}
                  </Link>
                  <span className="w-40 shrink-0 text-sm text-surface-200 truncate">{item.name}</span>
                  <span className="w-24 shrink-0 text-right text-sm text-amber-500 tabular-nums">
                    {money(item.amount)}
                  </span>
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => settle.mutate(item)}
                    loading={settle.isPending && isSameDebt(settle.variables, item)}
                  >
                    {t('settlements.tab.outstanding.settle')}
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        </>
      )}
    </Card>
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

      <RevenueChart months={revenue.months} />

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
function RevenueChart({ months }: { months: MonthlyRevenue[] }) {
  const money = useMoney()
  const locale = useDateLocale()
  const max = Math.max(1, ...months.map((bucket) => bucket.amount))

  return (
    <>
      <div className="flex items-end gap-1 h-32">
        {months.map((bucket) => {
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
                  className={`rounded-t ${STATS_FILL.done}`}
                  style={{ height: `${(bucket.amount / max) * 100}%` }}
                />
              ) : (
                <div className="h-0.5 rounded-full bg-surface-800" />
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
