import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { format } from 'date-fns'
import { Users, MailCheck, Dumbbell, Newspaper, ShieldCheck, TrendingUp, Filter, Activity, Trophy } from 'lucide-react'
import { adminUserStatsApi } from '../../../api/client'
import { LoadingSpinner } from '../../ui/LoadingSpinner'
import { QueryError } from '../../ui/QueryError'
import { useDateLocale } from '../../../utils/dateFnsLocale'
import { parseCalendarDate } from '../../../utils/calendarDate'
import { STATS_FILL, swatchClass } from './statsPalette'
import type { MonthlyRegistrations, TopClient, UserStats } from '../../../types'

/**
 * The Statistics view of the Users panel: the whole base at a glance.
 *
 * <p>Everything is drawn from one server response on purpose — the funnel, the cohorts and the
 * headline tiles share a denominator, and recomputing any of it from the user list this panel
 * already holds would put two counts from two moments on one screen.
 *
 * <p>Charts are plain divs, like the training statistics: this is a handful of bars, and a charting
 * library would be a dependency for four shapes we can spell out.
 */
export function UserStatsView() {
  const { t } = useTranslation('admin')

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['admin', 'userStats'],
    queryFn: adminUserStatsApi.get,
  })

  if (isLoading) return <div className="py-16 flex justify-center"><LoadingSpinner /></div>
  if (isError || !data) return <QueryError error={error} onRetry={() => refetch()} />

  if (data.totals.accounts === 0) {
    return (
      <div className="bg-surface-900 rounded-xl border border-surface-800 p-8 text-center text-surface-400">
        {t('users.stats.empty')}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <TotalsRow stats={data} />
      <RegistrationsChart bars={data.registrations} />
      {/* items-start: without it the grid stretches every card to its row's tallest, and the two
          short ones (activity, top clients) end in a block of empty panel that reads as content
          that failed to load. */}
      <div className="grid gap-4 lg:grid-cols-2 items-start">
        <FunnelCard stats={data} />
        <CohortsCard stats={data} />
        <TopClientsCard clients={data.topClients} />
        <div className="space-y-4">
          <NewsletterCard stats={data} />
          <AthletesCard stats={data} />
        </div>
      </div>
    </div>
  )
}

// ---------- shared pieces ----------

function useNumberFormat() {
  const { i18n } = useTranslation()
  return (n: number) => n.toLocaleString(i18n.language)
}

function percent(value: number, total: number): number {
  return total === 0 ? 0 : Math.round((value / total) * 100)
}

function Card({ title, icon: Icon, children }: { title: string; icon: typeof Users; children: React.ReactNode }) {
  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-3">
      <div className="flex items-center gap-2 text-sm font-medium text-surface-300">
        <Icon className="w-4 h-4 text-surface-400" />
        {title}
      </div>
      {children}
    </div>
  )
}

/**
 * One step of a funnel: a label, a bar as a share of the whole, and the number.
 *
 * <p>The value is always written out next to the bar. A bar whose only reading is its length is a
 * bar somebody has to measure against the one above it.
 */
function FunnelRow({ label, value, total, fmt }: { label: string; value: number; total: number; fmt: (n: number) => string }) {
  return (
    <div className="space-y-1">
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className="text-surface-300 truncate">{label}</span>
        <span className="text-surface-200 font-medium tabular-nums shrink-0">
          {fmt(value)} <span className="text-surface-500">· {percent(value, total)}%</span>
        </span>
      </div>
      <div className="h-2 rounded-full bg-surface-800 overflow-hidden">
        <div className={`h-full rounded-full ${STATS_FILL.done}`} style={{ width: `${percent(value, total)}%` }} />
      </div>
    </div>
  )
}

interface Segment {
  key: string
  label: string
  value: number
  fill: string
}

/**
 * A part-to-whole bar plus its legend.
 *
 * <p>The legend carries every segment's number, which is what lets the bar use a fill that sits
 * under the 3:1 contrast line in one of the two themes — identity never rests on the colour alone.
 * Segments are separated by a 2px gap of the surface so two adjacent fills cannot read as one.
 */
function SplitBar({ segments, total, fmt }: { segments: Segment[]; total: number; fmt: (n: number) => string }) {
  const shown = segments.filter((s) => s.value > 0)
  return (
    <div className="space-y-2">
      <div className="flex gap-0.5 h-3 rounded-full overflow-hidden bg-surface-800">
        {shown.map((segment) => (
          <div key={segment.key} className={segment.fill} style={{ width: `${(100 * segment.value) / total}%` }} />
        ))}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-surface-400">
        {segments.map((segment) => (
          <span key={segment.key} className="inline-flex items-center gap-1.5">
            <span className={swatchClass(segment.fill)} />
            {segment.label}
            <span className="text-surface-200 tabular-nums">{fmt(segment.value)}</span>
          </span>
        ))}
      </div>
    </div>
  )
}

// ---------- sections ----------

function TotalsRow({ stats }: { stats: UserStats }) {
  const { t } = useTranslation('admin')
  const fmt = useNumberFormat()
  const { totals } = stats

  const tiles = [
    { key: 'accounts', value: totals.accounts, share: null, icon: Users, color: 'text-surface-300', bg: 'bg-surface-800' },
    { key: 'verified', value: totals.verified, share: totals.accounts, icon: MailCheck, color: 'text-green-400', bg: 'bg-green-500/10' },
    { key: 'athletes', value: totals.athletes, share: totals.accounts, icon: Dumbbell, color: 'text-indigo-300', bg: 'bg-indigo-500/10' },
    { key: 'newsletter', value: totals.newsletter, share: totals.accounts, icon: Newspaper, color: 'text-amber-300', bg: 'bg-amber-500/10' },
    { key: 'admins', value: totals.admins, share: null, icon: ShieldCheck, color: 'text-surface-300', bg: 'bg-surface-800' },
  ]

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
      {tiles.map((tile) => {
        const Icon = tile.icon
        return (
          <div key={tile.key} className="bg-surface-900 border border-surface-800 rounded-xl p-4">
            <div className={`w-8 h-8 rounded-lg flex items-center justify-center mb-2 ${tile.bg}`}>
              <Icon className={`w-4 h-4 ${tile.color}`} />
            </div>
            <div className="text-2xl font-bold text-surface-100 tabular-nums">{fmt(tile.value)}</div>
            <div className="text-xs text-surface-400">{t(`users.stats.totals.${tile.key}`)}</div>
            {/* Denominators, not bare counts: "97" says nothing without the base it came from. */}
            {tile.share !== null && (
              <div className="text-xs text-surface-500 tabular-nums">
                {t('users.stats.ofAccounts', { percent: percent(tile.value, tile.share) })}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

/**
 * New accounts per month, split by whether the address was ever confirmed.
 *
 * <p>Empty months are drawn as a baseline tick rather than left out: a missing column reads as
 * missing data, a flat tick reads as a month when nobody signed up — which is the fact.
 */
function RegistrationsChart({ bars }: { bars: MonthlyRegistrations[] }) {
  const { t } = useTranslation('admin')
  const locale = useDateLocale()
  const fmt = useNumberFormat()

  const max = Math.max(1, ...bars.map((bar) => bar.total))

  return (
    <Card title={t('users.stats.registrations.title')} icon={TrendingUp}>
      <div className="flex items-end gap-1 h-32">
        {bars.map((bar) => {
          const month = parseCalendarDate(bar.month)
          const unverified = bar.total - bar.verified
          return (
            <div
              key={bar.month}
              className="group relative flex-1 h-full flex flex-col justify-end gap-0.5"
              role="img"
              aria-label={`${format(month, 'LLLL yyyy', { locale })}: ${t('users.stats.registrations.aria', { total: bar.total, verified: bar.verified })}`}
            >
              <div className="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1 hidden group-hover:block z-10 px-2 py-1 rounded-md bg-surface-800 border border-surface-700 text-xs text-surface-200 whitespace-nowrap shadow-lg">
                <span className="font-medium">{format(month, 'LLLL yyyy', { locale })}</span>
                {' · '}
                {t('users.stats.registrations.tooltip', { total: fmt(bar.total), verified: fmt(bar.verified) })}
              </div>
              {unverified > 0 && (
                <div className={`rounded-t ${STATS_FILL.pending}`} style={{ height: `${(unverified / max) * 100}%` }} />
              )}
              {bar.verified > 0 && (
                <div
                  className={`${unverified > 0 ? '' : 'rounded-t'} ${STATS_FILL.done}`}
                  style={{ height: `${(bar.verified / max) * 100}%` }}
                />
              )}
              {bar.total === 0 && <div className="h-0.5 rounded-full bg-surface-800" />}
            </div>
          )
        })}
      </div>

      <div className="flex gap-1">
        {bars.map((bar) => (
          <div key={bar.month} className="flex-1 text-center text-[10px] text-surface-500 truncate">
            {format(parseCalendarDate(bar.month), 'LLL', { locale })}
          </div>
        ))}
      </div>

      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-surface-400">
        <span className="inline-flex items-center gap-1.5">
          <span className={swatchClass(STATS_FILL.done)} />{t('users.stats.registrations.legendVerified')}
        </span>
        <span className="inline-flex items-center gap-1.5">
          <span className={swatchClass(STATS_FILL.pending)} />{t('users.stats.registrations.legendUnverified')}
        </span>
      </div>
    </Card>
  )
}

function FunnelCard({ stats }: { stats: UserStats }) {
  const { t } = useTranslation('admin')
  const fmt = useNumberFormat()
  const total = stats.totals.accounts

  return (
    <Card title={t('users.stats.funnel.title')} icon={Filter}>
      <div className="space-y-2.5">
        <FunnelRow label={t('users.stats.funnel.accounts')} value={total} total={total} fmt={fmt} />
        <FunnelRow label={t('users.stats.funnel.verified')} value={stats.totals.verified} total={total} fmt={fmt} />
        <FunnelRow label={t('users.stats.funnel.booked')} value={stats.funnel.booked} total={total} fmt={fmt} />
        <FunnelRow label={t('users.stats.funnel.returning')} value={stats.funnel.returning} total={total} fmt={fmt} />
      </div>
      <p className="text-xs text-surface-500">
        {t('users.stats.funnel.neverNote', { count: stats.cohorts.never })}
      </p>
    </Card>
  )
}

function CohortsCard({ stats }: { stats: UserStats }) {
  const { t } = useTranslation('admin')
  const fmt = useNumberFormat()
  const { cohorts, totals } = stats

  return (
    <Card title={t('users.stats.cohorts.title')} icon={Activity}>
      <SplitBar
        total={totals.accounts}
        fmt={fmt}
        segments={[
          { key: 'active', label: t('users.stats.cohorts.active'), value: cohorts.active, fill: STATS_FILL.done },
          { key: 'dormant', label: t('users.stats.cohorts.dormant'), value: cohorts.dormant, fill: STATS_FILL.pending },
          { key: 'never', label: t('users.stats.cohorts.never'), value: cohorts.never, fill: STATS_FILL.absent },
        ]}
      />
      {/* Names the rule it applies. The screen measures bookings because logins are not recorded —
          leaving that implicit would let "active" be read as "logged in recently". */}
      <p className="text-xs text-surface-500">{t('users.stats.cohorts.hint', { days: cohorts.windowDays })}</p>
    </Card>
  )
}

function TopClientsCard({ clients }: { clients: TopClient[] }) {
  const { t } = useTranslation('admin')
  const fmt = useNumberFormat()
  const max = Math.max(1, ...clients.map((client) => client.attended))

  return (
    <Card title={t('users.stats.top.title')} icon={Trophy}>
      {clients.length === 0 ? (
        <p className="text-sm text-surface-400">{t('users.stats.top.empty')}</p>
      ) : (
        <ol className="space-y-1.5">
          {clients.map((client, index) => (
            <li key={client.userId}>
              <Link
                to={`/admin/users/${client.userId}`}
                className="relative flex items-center gap-2 px-2 py-1.5 rounded-lg overflow-hidden hover:bg-surface-800/60 transition-colors"
              >
                {/* The bar is a backdrop, not the reading: the count next to the name is. */}
                <span
                  className="absolute inset-y-0 left-0 bg-green-400/15 rounded-lg"
                  style={{ width: `${(100 * client.attended) / max}%` }}
                  aria-hidden="true"
                />
                <span className="relative text-xs text-surface-500 tabular-nums w-4 shrink-0">{index + 1}.</span>
                <span className="relative text-sm text-surface-200 truncate">
                  {client.firstName} {client.lastName}
                </span>
                <span className="relative ml-auto text-sm text-surface-300 tabular-nums shrink-0">
                  {fmt(client.attended)}
                </span>
              </Link>
            </li>
          ))}
        </ol>
      )}
      <p className="text-xs text-surface-500">{t('users.stats.top.hint')}</p>
    </Card>
  )
}

function NewsletterCard({ stats }: { stats: UserStats }) {
  const { t } = useTranslation('admin')
  const fmt = useNumberFormat()
  const { newsletter, totals } = stats

  return (
    <Card title={t('users.stats.newsletter.title')} icon={Newspaper}>
      <SplitBar
        total={totals.accounts}
        fmt={fmt}
        segments={[
          { key: 'subscribed', label: t('users.stats.newsletter.subscribed'), value: newsletter.subscribed, fill: STATS_FILL.done },
          { key: 'undecided', label: t('users.stats.newsletter.undecided'), value: newsletter.undecided, fill: STATS_FILL.pending },
          { key: 'unsubscribed', label: t('users.stats.newsletter.unsubscribed'), value: newsletter.unsubscribed, fill: STATS_FILL.absent },
        ]}
      />
    </Card>
  )
}

/**
 * Flag granted → consent signed → plan used.
 *
 * <p>Hidden when nobody carries the flag: three zeroes would read as a feature that is broken
 * rather than one nobody has switched on yet.
 */
function AthletesCard({ stats }: { stats: UserStats }) {
  const { t } = useTranslation('admin')
  const fmt = useNumberFormat()
  const { athletes } = stats

  if (athletes.flagged === 0) return null

  return (
    <Card title={t('users.stats.athletes.title')} icon={Dumbbell}>
      <div className="space-y-2.5">
        <FunnelRow label={t('users.stats.athletes.flagged')} value={athletes.flagged} total={athletes.flagged} fmt={fmt} />
        <FunnelRow label={t('users.stats.athletes.consented')} value={athletes.consented} total={athletes.flagged} fmt={fmt} />
        <FunnelRow label={t('users.stats.athletes.withPlan')} value={athletes.withPlan} total={athletes.flagged} fmt={fmt} />
      </div>
      <p className="text-xs text-surface-500">{t('users.stats.athletes.hint')}</p>
    </Card>
  )
}
