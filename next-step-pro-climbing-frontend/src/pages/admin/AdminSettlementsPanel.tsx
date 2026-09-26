import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { format } from 'date-fns'
import { AlertTriangle, Building2, ChevronDown, ChevronRight, CircleHelp, Coins, Download, PiggyBank, TrendingUp, UserX, Users } from 'lucide-react'
import { Button } from '../../components/ui/Button'
import { DateInput } from '../../components/ui/DateInput'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import { STATS_FILL } from '../../components/admin/userstats/statsPalette'
import { PayoutPeriodRow } from '../../components/admin/PayoutPeriodRow'
import { useMoney } from '../../components/admin/useMoney'
import { adminSettlementsApi } from '../../api/client'
import { getErrorMessage } from '../../utils/errors'
import { parseCalendarDate, todayInWarsaw } from '../../utils/calendarDate'
import { useDateLocale } from '../../utils/dateFnsLocale'
import { MAX_PAYOUT_AMOUNT, parseAmount } from '../../utils/money'
import type {
  CreditItem,
  CreditsSummary,
  MonthlyRevenue,
  OutstandingItem,
  PersonRevenue,
  PayoutSource,
  PayoutsSummary,
  SettlementOverview,
  SettlementTarget,
  UnassignedSummary,
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
    && data.unassigned.count === 0
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
          {/* First, because it is the furthest out of reach: a session nobody priced is at least
              known to the pricing queue through the people on it, while one with nobody on it is
              not visible to any other screen in the app. */}
          <UnassignedCard unassigned={data.unassigned} />
          {/* Then the half that cannot ask for itself either: an unpaid amount at least exists as
              a debt, a session nobody priced is invisible everywhere else. */}
          <UnpricedCard unpriced={data.unpriced} />
          <OutstandingCard overview={data} />
          <CreditsCard credits={data.credits} />
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
            t('settlements.tab.export.colPaid'),
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

// ---------- work queues ----------

/**
 * One row of either work queue: a date, a link into the session, and whatever the queue counts.
 *
 * Shared because the two lists differ in what is missing, not in how a row behaves — and the link
 * is the part worth writing once, since a wrong `targetType` in it opens the wrong modal and only
 * says so when somebody presses it.
 */
function SessionRow({
  targetType,
  targetId,
  date,
  title,
  openLabel,
  aside,
}: {
  targetType: SettlementTarget
  targetId: string
  date: string
  title: string | null
  openLabel: string
  aside?: string
}) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const location = useLocation()

  return (
    <li className="flex items-center gap-3 py-2">
      <span className="w-24 shrink-0 text-xs text-surface-400 tabular-nums">
        {format(parseCalendarDate(date), 'dd.MM.yyyy', { locale })}
      </span>
      <Link
        to={`/calendar?date=${date}&${targetType}=${targetId}`}
        state={{ returnTo: location.pathname + location.search }}
        aria-label={openLabel}
        className="flex-1 min-w-0 text-sm text-surface-300 truncate hover:text-primary-300 transition-colors"
      >
        {title ?? t(`settlements.tab.outstanding.untitled.${targetType}`)}
      </Link>
      {aside && (
        <span className="shrink-0 text-xs text-surface-500 tabular-nums">{aside}</span>
      )}
    </li>
  )
}

/**
 * Sessions that were worked and have nobody to bill at all.
 *
 * The one queue no other screen can stand in for: a session with zero people on it produces no
 * rows in the pricing queue's reads, is neither revenue nor debt, and is missing from the hourly
 * rate's denominator — so forgetting to name the payer makes the rate read high, silently and for
 * good. The row links into the same modal that already has "mark as settled in bulk" on it.
 */
function UnassignedCard({ unassigned }: { unassigned: UnassignedSummary }) {
  const { t } = useTranslation('admin')

  if (unassigned.count === 0) return null

  return (
    <Card
      title={t('settlements.tab.unassigned.title')}
      icon={UserX}
      aside={
        <span className="text-sm font-semibold text-surface-200 tabular-nums">
          {t('settlements.tab.unassigned.count', { n: unassigned.count })}
        </span>
      }
    >
      {/* Three rules stated rather than guessed: what puts a session here, that the list disobeys
          the year picker, and where it stops. */}
      <p className="text-xs text-surface-500">
        {t('settlements.tab.unassigned.scope', { days: unassigned.windowDays })}
      </p>
      <div className="overflow-x-auto">
        <ul className="min-w-[28rem] divide-y divide-surface-800">
          {unassigned.sessions.map((session) => (
            <SessionRow
              key={`${session.targetType}:${session.targetId}`}
              targetType={session.targetType}
              targetId={session.targetId}
              date={session.date}
              title={session.title}
              openLabel={t('settlements.tab.unassigned.open', {
                date: format(parseCalendarDate(session.date), 'dd.MM.yyyy'),
              })}
            />
          ))}
        </ul>
      </div>
    </Card>
  )
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
            <SessionRow
              key={`${session.targetType}:${session.targetId}`}
              targetType={session.targetType}
              targetId={session.targetId}
              date={session.date}
              title={session.title}
              openLabel={t('settlements.tab.unpriced.open', {
                date: format(parseCalendarDate(session.date), 'dd.MM.yyyy'),
              })}
              aside={t('settlements.tab.unpriced.people', { n: session.payerCount })}
            />
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
  //
  // The credit joins the group here rather than the item, which is where the server sends it: one
  // overpayment spread over somebody's four debts would be added to the group total four times.
  const groups = useMemo(() => {
    // ⚠️ Defensive on a field the type says is always there: during a deploy the new bundle can be
    // served for a few seconds while the previous backend still answers, and `.map` of undefined in
    // a useMemo throws during render — which in this panel is a white screen, not a missing line.
    const credits = new Map<string, number>(
      (outstanding.credits ?? []).map(
        (credit) => [`${credit.payerType}:${credit.payerId}`, credit.credit],
      ),
    )
    const byPayer = new Map<string, PayerDebt>()
    for (const item of outstanding.items) {
      const key = `${item.payerType}:${item.payerId}`
      const group = byPayer.get(key)
        ?? { key, name: item.name, items: [], total: 0, credit: credits.get(key) ?? 0 }
      group.items.push(item)
      group.total += item.amount
      byPayer.set(key, group)
    }
    // Oldest debt first, same order as the flat list had — a backlog reads in the order it grew.
    return [...byPayer.values()]
  }, [outstanding.items, outstanding.credits])

  // ⚠️ The headline is what is left to COLLECT, not the sum of open rows. Gross, it said "100 zł"
  // about somebody holding 90 of yours, and the owner read it as her owing 100. Summed from the
  // groups, never from `outstanding.total` minus all credits: a credit bigger than its owner's debt
  // must not eat into somebody else's. The gross figure stays beside it whenever the two differ,
  // so the heading still visibly adds up to the rows below.
  const toCollect = groups.reduce((sum, group) => sum + collectable(group), 0)

  return (
    <Card
      title={t('settlements.tab.outstanding.title')}
      icon={AlertTriangle}
      aside={
        outstanding.count > 0 ? (
          <span className="text-sm text-surface-200 tabular-nums">
            <span className="font-semibold text-amber-500">{money(toCollect)}</span>
            <span className="text-surface-500">
              {toCollect < outstanding.total - 0.005 && (
                <>
                  {' · '}
                  {t('settlements.tab.outstanding.grossTotal', { amount: money(outstanding.total) })}
                </>
              )}
              {' · '}
              {t('settlements.tab.outstanding.count', { count: outstanding.count })}
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

/** One payer's debts, plus whatever they have already left with you against them. */
interface PayerDebt {
  key: string
  name: string
  items: OutstandingItem[]
  total: number
  /** Always positive, and 0 for the ordinary case where they are holding nothing of yours. */
  credit: number
}

/** What is genuinely left to collect from one payer once the money they left with you is spent. */
function collectable(group: PayerDebt): number {
  return Math.max(0, group.total - group.credit)
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
function PayerDebtGroup({ group }: { group: PayerDebt }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const location = useLocation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [paidOn, setPaidOn] = useState(() => todayInWarsaw())
  // What actually changed hands, defaulting to what is owed — the common case is one click, and the
  // field is there for the times a note is bigger than the bill.
  //
  // ⚠️ `toFixed(2)`, never `String()`. The total is accumulated with `+=` over floats, so about
  // three groups in ten land on something like `671.1600000000001` — while the heading right beside
  // this field renders the same number as `671,16 zł` through `Intl`. The stored value would still
  // be right (the server rounds to the column's scale), but a money screen that disagrees with
  // itself by eleven decimal places is not one anybody should have to trust.
  //
  // ⚠️ What is left AFTER the credit, not the whole debt. The server pulls an overpayment back into
  // the pool before it starts paying rows off, so typing the gross figure over a credit hands the
  // person a second overpayment of exactly that size — and the ordinary case, where the credit
  // covers the lot, is a zero somebody would otherwise have to know to type.
  const toCollect = collectable(group)
  const [received, setReceived] = useState(() => toCollect.toFixed(2))
  const receivedAmount = parseAmount(received)
  // Nothing changes hands, the credit pays: say so, instead of "receive 0 zł" — the same endpoint
  // the "settle from credit" button in the session modal uses.
  const fromCreditOnly = receivedAmount === 0 && group.credit > 0

  const first = group.items[0]
  const backHere = location.pathname + location.search

  const settleAll = useMutation({
    mutationFn: () =>
      // The settlement ceiling, NOT the payout one: this money lands on a settlement row, which the
      // server caps at MAX_AMOUNT. Passing the higher transfer ceiling here left Save enabled on an
      // amount the server then rejected — the mirror image of the bug that ceiling was added for.
      adminSettlementsApi.settleOutstanding(
        first.payerType, first.payerId, paidOn, receivedAmount ?? 0),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] }),
  })

  return (
    <li className="py-2 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="flex flex-1 min-w-0 items-center gap-1.5 text-left text-sm text-surface-200 hover:text-surface-100 transition-colors"
        >
          {open ? <ChevronDown className="w-4 h-4 shrink-0" /> : <ChevronRight className="w-4 h-4 shrink-0" />}
          <span className="truncate">{group.name}</span>
          <span className="text-surface-500 shrink-0">
            · {t('settlements.tab.outstanding.sessions', { count: group.items.length })}
          </span>
        </button>
        {/* ⚠️ The row's figure is what is left AFTER the credit — the gross sum of the open rows is
            the smaller line under it. Gross on top read as a demand for money the person had
            already handed over. Neutral, not green, for the credit: green means "done" in this app,
            and a credit is not done. */}
        <div className="shrink-0 text-right tabular-nums">
          <div className="text-sm font-semibold text-amber-500">{money(toCollect)}</div>
          {group.credit > 0 && (
            <div className="text-xs text-surface-400">
              {t('settlements.tab.outstanding.credit', {
                gross: money(group.total),
                credit: money(group.credit),
              })}
            </div>
          )}
        </div>
      </div>

      {/* The controls get their own row. Sharing one wrapping row with the name and the figure put a
          native iOS date field beside them, and Safari draws that control wider than the box it is
          given — so it painted over the amount. `min-w-0` + a fixed width + `appearance-none` is
          what makes WebKit keep it inside its box. */}
      <div className="flex flex-wrap items-center gap-2">
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
          className="w-36 min-w-0 appearance-none bg-surface-800 border border-surface-600 rounded px-2 py-1 text-sm text-surface-100 focus:outline-none focus:border-primary-500"
        />
        {/* The label names the money that ARRIVES, read from the field — it used to name the gross
            debt, so "Settle all — 100 zł" sat over a field holding 10. */}
        <Button
          size="sm"
          variant="primary"
          loading={settleAll.isPending}
          disabled={paidOn === '' || receivedAmount === null}
          onClick={() => settleAll.mutate()}
        >
          {fromCreditOnly
            ? t('settlements.tab.outstanding.settleFromCredit')
            : t('settlements.tab.outstanding.settleAll', { amount: money(receivedAmount ?? 0) })}
        </Button>
      </div>

      {open && (
        <ul className="pl-6 space-y-1">
          {group.items.map((item) => (
            <li
              // ⚠️ The date belongs in the key. A standing fee has no calendar entry, so it
              // travels with a null targetId, and one person can hold several of them —
              // target alone collapses every month onto "month:null", and duplicate keys
              // among siblings let React reuse the wrong node on the next render.
              key={`${item.targetType}:${item.targetId}:${item.date}`}
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

// ---------- credits ----------

/** One person and every session parking money of theirs. */
interface PayerCredit {
  key: string
  name: string
  isGuest: boolean
  payerId: string
  items: CreditItem[]
  total: number
}

/**
 * People holding money of yours with nothing owing — the half of the ledger that had nowhere to be
 * seen.
 *
 * Credit used to be computed only for people who also owed something, so somebody who overpaid once
 * and owes nothing appeared in no figure on this tab: not in revenue (it did arrive), not in debt
 * (he owes nothing), not in the credit note (which only annotates debtors). His money was visible
 * solely by opening the session it sits on.
 *
 * ⚠️ Neutral, never green or amber. Amber is work to do and this is not work; green means "done"
 * and a credit is not done either. The WORD carries the meaning here, the colour only says whether
 * something is a task.
 *
 * ⚠️ Read-only on purpose. A credit is spent at a session, by the "settle from credit" button in
 * the settlement section — there is nothing to settle from this list, so it names WHERE the money
 * is parked and links there.
 */
function CreditsCard({ credits }: { credits: CreditsSummary | undefined }) {
  const { t } = useTranslation('admin')
  const money = useMoney()

  // Grouped by payer, same routine as the debt list and on the same key: the server sends flat
  // items so that two lists about the same money have one shape on the wire.
  const groups = useMemo(() => {
    const byPayer = new Map<string, PayerCredit>()
    // ⚠️ Defensive on a field the type says is always there, and here the exposure is worse than
    // on the debt list: `credits` is a WHOLE new top-level field, so a browser holding the new
    // bundle while the previous backend still answers gets `undefined` rather than an empty list —
    // and dereferencing it inside a useMemo is a white screen, not a missing card.
    for (const item of credits?.items ?? []) {
      const key = `${item.payerType}:${item.payerId}`
      const group = byPayer.get(key) ?? {
        key,
        name: item.name,
        isGuest: item.payerType === 'guest',
        payerId: item.payerId,
        items: [],
        total: 0,
      }
      group.items.push(item)
      group.total += item.amount
      byPayer.set(key, group)
    }
    // Server order is biggest credit first — the one worth remembering when pricing the next
    // session. Kept, not recomputed: re-sorting here on a float sum is how the card and the
    // heading start disagreeing about who is top of the list.
    return [...byPayer.values()]
  }, [credits?.items])

  // Same reason as the loop above: an absent field is not a zero, and both have to draw nothing.
  if (!credits || credits.payers === 0) return null

  return (
    <Card
      title={t('settlements.tab.credits.title')}
      icon={PiggyBank}
      aside={
        <span className="text-sm text-surface-200 tabular-nums">
          <span className="font-semibold">{money(credits.total)}</span>
          <span className="text-surface-500">
            {' · '}
            {t('settlements.tab.credits.payers', { count: credits.payers })}
          </span>
        </span>
      }
    >
      <p className="text-xs text-surface-500">{t('settlements.tab.credits.ignoresYear')}</p>
      {/* Says out loud who is NOT here. A debtor's credit is named beside their debt, where
          settling spends it; repeating them under a heading that totals money you hold would
          state the opposite of their net position on the same screen. */}
      <p className="text-xs text-surface-500">{t('settlements.tab.credits.excludesDebtors')}</p>
      <ul className="divide-y divide-surface-800">
        {groups.map((group) => (
          <PayerCreditGroup key={group.key} group={group} />
        ))}
      </ul>
    </Card>
  )
}

function PayerCreditGroup({ group }: { group: PayerCredit }) {
  const { t } = useTranslation('admin')
  const money = useMoney()
  const locale = useDateLocale()
  const location = useLocation()
  const [open, setOpen] = useState(false)

  const backHere = location.pathname + location.search

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
            {group.isGuest && <> · {t('settlements.line.guest')}</>}
            {' · '}
            {t('settlements.tab.credits.sessions', { count: group.items.length })}
          </span>
        </button>
        <span className="shrink-0 text-sm font-semibold text-surface-200 tabular-nums">
          {money(group.total)}
        </span>
      </div>

      {open && (
        <ul className="pl-6 space-y-1">
          {group.items.map((item) => (
            <li
              // ⚠️ The date belongs in the key. A standing fee has no calendar entry, so it
              // travels with a null targetId, and one person can hold several of them —
              // target alone collapses every month onto "month:null", and duplicate keys
              // among siblings let React reuse the wrong node on the next render.
              key={`${item.targetType}:${item.targetId}:${item.date}`}
              className="flex flex-wrap items-center gap-2 text-xs"
            >
              <span className="w-24 shrink-0 text-surface-400 tabular-nums">
                {format(parseCalendarDate(item.date), 'dd.MM.yyyy', { locale })}
              </span>
              {/* A standing fee has no calendar entry behind it, so it is text — a link that goes
                  nowhere is worse than no link. */}
              {item.targetId === null ? (
                <span className="flex-1 min-w-0 truncate text-surface-300">
                  {t('settlements.tab.credits.untitled.month')}
                </span>
              ) : (
                <Link
                  to={`/calendar?date=${item.date}&${item.targetType}=${item.targetId}`}
                  state={{ returnTo: backHere }}
                  aria-label={t('settlements.tab.credits.open', {
                    name: item.name,
                    date: format(parseCalendarDate(item.date), 'dd.MM.yyyy'),
                  })}
                  className="flex-1 min-w-0 truncate text-surface-300 hover:text-primary-300 transition-colors"
                >
                  {item.title ?? t(`settlements.tab.credits.untitled.${item.targetType}`)}
                </Link>
              )}
              <span className="shrink-0 text-surface-300 tabular-nums">{money(item.amount)}</span>
            </li>
          ))}
          {/* A guest has no card to open — the session above is the only place their figure can be
              corrected, which is what a guest is: a booking with no continuity behind it. */}
          {!group.isGuest && (
            <li className="pt-1">
              <Link
                to={`/admin/users/${group.payerId}`}
                className="text-xs text-primary-400 hover:text-primary-300 transition-colors"
              >
                {t('settlements.tab.credits.openUser')}
              </Link>
            </li>
          )}
        </ul>
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

      {/* ⚠️ ALL FOUR sources, and one row each rather than one stacked bar.
          Two reasons. The split is read against the headline total, so a source left out is an
          unexplained gap in it — bulk transfers were computed and never drawn, and a retainer was
          counted as slot income, which claims session earnings for a client whose sessions are
          deliberately unpriced. And four categories need four fills that hold up in BOTH themes,
          which the three-fill semantic palette does not have and must not be guessed at (see
          statsPalette: the obvious green/amber-400 pairing is unreadable in the light theme). A row
          per source carries identity in the label, so colour is left saying only "money in". */}
      {revenue.total > 0 && (
        <div className="space-y-1.5">
          {([
            ['fromSlots', revenue.fromSlots],
            ['fromEvents', revenue.fromEvents],
            ['fromSubscriptions', revenue.fromSubscriptions],
            ['fromPayouts', revenue.fromPayouts],
          ] as const)
            .filter(([, value]) => value > 0)
            .map(([key, value]) => (
              <div key={key} className="flex items-center gap-2 text-xs">
                <span className="w-28 shrink-0 truncate text-surface-400">
                  {t(`settlements.tab.revenue.${key}`)}
                </span>
                <span className="flex-1 h-2 rounded-full bg-surface-800 overflow-hidden">
                  <span
                    className={`block h-full ${STATS_FILL.done}`}
                    style={{ width: `${(100 * value) / revenue.total}%` }}
                  />
                </span>
                <span className="w-24 shrink-0 text-right text-surface-200 tabular-nums">
                  {money(value)}
                </span>
              </div>
            ))}
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

  /**
   * ⚠️ This used to `return null`, and that hid the ONLY way to add a first contractor: the
   * manager that creates them lives inside this card. So the card disappeared exactly when it was
   * needed, and bulk payouts could not be started at all.
   *
   * The whole-tab empty state has a second copy of the manager, which is why this looked fixed.
   * It is not: that state requires the tab to be empty of *everything* — no settlement years, no
   * debts, nothing awaiting pricing — so it only ever helped a brand-new install. Anybody already
   * pricing sessions per participant (which is everybody using this) stayed locked out.
   *
   * Empty now means "introduce yourself once", not "hide". No table, no axis note — there is no
   * data for either; just the sentence explaining what this is for and the way in.
   */
  if (payouts.sources.length === 0 && payouts.periods.length === 0) {
    return (
      <Card title={t('settlements.tab.payouts.title')} icon={Building2}>
        <p className="text-xs text-surface-500">{t('settlements.tab.payouts.setupHint')}</p>
        <SourceManager sources={payouts.sources} onChanged={refresh} />
      </Card>
    )
  }

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
