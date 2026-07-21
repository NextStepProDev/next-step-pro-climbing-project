import { useEffect, useMemo, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { format, addDays, startOfWeek, subDays } from 'date-fns'
import clsx from 'clsx'
import {
  Activity,
  AlertTriangle,
  Award,
  BarChart3,
  CalendarCheck,
  CalendarDays,
  Dumbbell,
  Flame,
  Gauge,
  MapPin,
  TrendingUp,
  Trophy,
} from 'lucide-react'
import { QueryError } from '../ui/QueryError'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { AthleteStats } from '../../types'

interface TrainingStatsSectionProps {
  api: TrainingCalendarAdapter
  // 'me' for the athlete's own tab, athleteId in the coach panel
  scopeKey: string
  // Coach view: the "unrated" nudge is athlete-only; the warning wording differs
  isCoachView?: boolean
}

const TOTAL_MILESTONES = [10, 25, 50, 100, 250]
const STREAK_MILESTONES = [4, 8, 12]

export function TrainingStatsSection({ api, scopeKey, isCoachView }: TrainingStatsSectionProps) {
  const { t, i18n } = useTranslation('training')

  const statsQuery = useQuery({
    queryKey: ['trainingCalendar', 'stats', scopeKey],
    queryFn: api.getStats,
    // Stats are live-derived server-side; reservation changes happen outside this screen
    refetchInterval: 60_000,
    refetchIntervalInBackground: false,
    refetchOnMount: 'always',
  })

  if (statsQuery.isLoading) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-24 bg-surface-900 rounded-xl border border-surface-800 animate-pulse" />
        ))}
      </div>
    )
  }
  if (statsQuery.isError) {
    return <QueryError error={statsQuery.error} onRetry={() => statsQuery.refetch()} />
  }
  const stats = statsQuery.data
  if (!stats) return null

  const fmt = (n: number) => n.toLocaleString(i18n.language)

  if (stats.totalCount === 0) {
    return (
      <div className="bg-surface-900 rounded-xl border border-surface-800 p-6 text-center">
        <BarChart3 className="w-6 h-6 text-surface-600 mx-auto mb-2" />
        <p className="text-sm text-surface-500">{t('stats.empty')}</p>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <h3 className="flex items-center gap-2 text-sm font-semibold text-surface-300 uppercase tracking-wide pt-2">
        <BarChart3 className="w-4 h-4 text-surface-400" />
        {t('stats.title')}
      </h3>

      {!isCoachView && stats.unratedActivitiesCount > 0 && (
        <div className="flex items-center gap-2 p-2.5 rounded-lg bg-amber-500/10 border border-amber-500/25 text-sm text-amber-300">
          <Gauge className="w-4 h-4 shrink-0" />
          {t('rpe.unrated', { count: stats.unratedActivitiesCount })}
        </div>
      )}

      <KpiTiles stats={stats} fmt={fmt} />
      <ActivityHeatmap heatmap={stats.heatmap} />
      <RpeDistributionCard stats={stats} isCoachView={isCoachView} />
      <BreakdownCards stats={stats} fmt={fmt} />
      <BadgeRow stats={stats} fmt={fmt} />
    </div>
  )
}

// Intensity balance (90 days): light / medium / hard sessions as a stacked bar. A balance, not a
// score — with an amber hint when the recent ratings are consistently maxed out.
function RpeDistributionCard({ stats, isCoachView }: { stats: AthleteStats; isCoachView?: boolean }) {
  const { t } = useTranslation('training')
  const d = stats.rpeDistribution
  const total = d.light + d.medium + d.hard
  if (total === 0) return null
  const pct = (n: number) => `${(100 * n) / total}%`

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-2">
      <div className="flex items-center gap-2 text-sm font-medium text-surface-300">
        <Gauge className="w-4 h-4 text-surface-400" />
        {t('stats.rpeDist.title')}
      </div>
      <div className="flex h-3 rounded-full overflow-hidden bg-surface-800">
        {d.light > 0 && <div className="bg-green-500/70" style={{ width: pct(d.light) }} />}
        {d.medium > 0 && <div className="bg-amber-500/70" style={{ width: pct(d.medium) }} />}
        {d.hard > 0 && <div className="bg-rose-500/70" style={{ width: pct(d.hard) }} />}
      </div>
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-surface-400">
        <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-green-500/70" />{t('stats.rpeDist.light', { count: d.light })}</span>
        <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-amber-500/70" />{t('stats.rpeDist.medium', { count: d.medium })}</span>
        <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-rose-500/70" />{t('stats.rpeDist.hard', { count: d.hard })}</span>
      </div>
      <p className="text-xs text-surface-500">{t('stats.rpeDist.balanceHint')}</p>
      {stats.sustainedHighRpe && (
        <p className="text-xs text-amber-400/90 flex items-start gap-1.5">
          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          {t(isCoachView ? 'stats.rpeWarning.coach' : 'stats.rpeWarning.athlete')}
        </p>
      )}
    </div>
  )
}

// ---------- KPI tiles ----------

function KpiTiles({ stats, fmt }: { stats: AthleteStats; fmt: (n: number) => string }) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()

  const delta = stats.thisMonthCount - stats.prevMonthCount
  const rpeMain = stats.avgRpeLast30Days ?? stats.avgRpeOverall
  const rpeShowsWindow = stats.avgRpeLast30Days != null
  const rpeSub =
    rpeShowsWindow && stats.avgRpeOverall != null && stats.avgRpeOverall !== stats.avgRpeLast30Days
      ? t('stats.avgRpeOverall', { value: fmt(stats.avgRpeOverall) })
      : undefined

  return (
    <>
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <StatTile
          icon={<CalendarCheck className="w-4 h-4 text-green-400" />}
          value={fmt(stats.thisMonthCount)}
          valueExtra={
            <span
              aria-label={t('stats.vsPrevMonth')}
              className={clsx(
                'text-xs font-semibold tabular-nums',
                delta > 0 ? 'text-green-400' : delta < 0 ? 'text-rose-400' : 'text-surface-500',
              )}
            >
              {delta > 0 ? `▲ +${fmt(delta)}` : delta < 0 ? `▼ -${fmt(Math.abs(delta))}` : '—'}
            </span>
          }
          label={t('stats.thisMonth')}
          sub={t('stats.vsPrevMonth')}
        />
        <StatTile
          icon={<Dumbbell className="w-4 h-4 text-surface-400" />}
          value={fmt(stats.totalCount)}
          label={t('stats.total')}
        />
        <StatTile
          icon={<Flame className={clsx('w-4 h-4', stats.currentStreakWeeks > 0 ? 'text-amber-400' : 'text-surface-500')} />}
          value={fmt(stats.currentStreakWeeks)}
          label={t('stats.streak')}
          sub={stats.bestStreakWeeks > 0 ? t('stats.bestStreak', { count: stats.bestStreakWeeks }) : undefined}
        />
        {stats.firstActivityDate && (
          <StatTile
            icon={<CalendarDays className="w-4 h-4 text-surface-400" />}
            value={format(new Date(stats.firstActivityDate), 'LLL yyyy', { locale })}
            label={t('stats.trainingSince')}
          />
        )}
      </div>

      {(stats.avgPerMonth != null || stats.attendanceRatePercent != null || rpeMain != null) && (
        <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">
          {stats.avgPerMonth != null && (
            <StatTile
              icon={<TrendingUp className="w-4 h-4 text-surface-400" />}
              value={fmt(stats.avgPerMonth)}
              label={t('stats.avgPerMonth')}
            />
          )}
          {stats.attendanceRatePercent != null && (
            <StatTile
              icon={<Activity className="w-4 h-4 text-surface-400" />}
              value={`${fmt(stats.attendanceRatePercent)}%`}
              label={t('stats.attendance')}
              sub={t('stats.attendanceHint')}
            />
          )}
          {rpeMain != null && (
            <StatTile
              icon={<Gauge className="w-4 h-4 text-surface-400" />}
              value={fmt(rpeMain)}
              label={rpeShowsWindow ? t('stats.avgRpe30') : t('stats.avgRpe')}
              sub={rpeSub}
            />
          )}
        </div>
      )}
    </>
  )
}

function StatTile({
  icon,
  value,
  valueExtra,
  label,
  sub,
}: {
  icon: React.ReactNode
  value: string
  valueExtra?: React.ReactNode
  label: string
  sub?: string
}) {
  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4">
      <div className="flex items-center gap-2 mb-1.5">{icon}</div>
      <div className="flex items-baseline gap-2">
        <span className="text-2xl font-bold text-surface-100 tabular-nums">{value}</span>
        {valueExtra}
      </div>
      <div className="text-xs text-surface-400 mt-0.5">{label}</div>
      {sub && <div className="text-xs text-surface-500 mt-0.5">{sub}</div>}
    </div>
  )
}

// ---------- activity heatmap (GitHub-style, last 12 months) ----------

const HEATMAP_DAYS = 365
const CELL = 'w-[11px] h-[11px] rounded-[2px]'

function levelClass(count: number): string {
  if (count <= 0) return 'bg-surface-800'
  if (count === 1) return 'bg-green-500'
  return 'bg-green-300'
}

function ActivityHeatmap({ heatmap }: { heatmap: Record<string, number> }) {
  const { t } = useTranslation('training')
  const locale = useDateLocale()
  const scrollRef = useRef<HTMLDivElement>(null)

  // Most recent weeks must be visible on narrow screens — scroll to the right edge
  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollLeft = el.scrollWidth
  }, [])

  const { weeks, monthLabels, windowStart, today } = useMemo(() => {
    const today = new Date()
    const windowStart = subDays(today, HEATMAP_DAYS - 1)
    const gridStart = startOfWeek(windowStart, { weekStartsOn: 1 })
    const weeks: Date[] = []
    for (let w = gridStart; w <= today; w = addDays(w, 7)) weeks.push(w)
    // Label a column when its month differs from the previous column's month
    const monthLabels = weeks.map((week, i) =>
      i === 0 || week.getMonth() !== weeks[i - 1].getMonth()
        ? format(week, 'LLL', { locale })
        : null,
    )
    return { weeks, monthLabels, windowStart, today }
  }, [locale])

  // Mon / Wed / Fri row labels, localized
  const dayLabels = useMemo(() => {
    const monday = startOfWeek(new Date(), { weekStartsOn: 1 })
    return [0, 2, 4].map((offset) => format(addDays(monday, offset), 'EEEEEE', { locale }))
  }, [locale])

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4">
      <div className="flex items-center gap-2 text-sm font-medium text-surface-300 mb-3">
        <CalendarDays className="w-4 h-4 text-surface-400" />
        {t('stats.heatmap.title')}
      </div>
      <div ref={scrollRef} className="overflow-x-auto pb-1">
        <div className="inline-flex gap-1.5">
          {/* Weekday labels: rows 0 (Mon), 2 (Wed), 4 (Fri) */}
          <div className="grid grid-rows-7 gap-[3px] text-[9px] text-surface-500 leading-none pr-0.5">
            {[0, 1, 2, 3, 4, 5, 6].map((row) => (
              <div key={row} className="h-[11px] flex items-center">
                {row === 0 ? dayLabels[0] : row === 2 ? dayLabels[1] : row === 4 ? dayLabels[2] : ''}
              </div>
            ))}
          </div>
          <div>
            <div className="relative h-4">
              {monthLabels.map((label, i) =>
                label ? (
                  <span
                    key={i}
                    className="absolute top-0 text-[9px] text-surface-500 leading-none whitespace-nowrap"
                    style={{ left: i * 14 }}
                  >
                    {label}
                  </span>
                ) : null,
              )}
            </div>
            <div className="grid grid-rows-7 grid-flow-col gap-[3px]">
              {weeks.map((week) =>
                [0, 1, 2, 3, 4, 5, 6].map((day) => {
                  const date = addDays(week, day)
                  if (date > today || date < windowStart) {
                    return <div key={`${week.toISOString()}-${day}`} className={clsx(CELL, 'invisible')} />
                  }
                  const key = format(date, 'yyyy-MM-dd')
                  const count = heatmap[key] ?? 0
                  const dateLabel = format(date, 'dd.MM.yyyy')
                  const tooltip =
                    count > 0
                      ? t('stats.heatmap.tooltip', { count, date: dateLabel })
                      : t('stats.heatmap.tooltipEmpty', { date: dateLabel })
                  return (
                    <div
                      key={key}
                      className={clsx(CELL, levelClass(count))}
                      title={tooltip}
                      aria-label={tooltip}
                    />
                  )
                }),
              )}
            </div>
          </div>
        </div>
      </div>
      <div className="flex items-center justify-end gap-3 mt-2 text-[10px] text-surface-500">
        {([[0, '0'], [1, '1'], [2, '2+']] as const).map(([lvl, label]) => (
          <span key={label} className="inline-flex items-center gap-1">
            <span className={clsx(CELL, levelClass(lvl), 'inline-block')} />
            {label}
          </span>
        ))}
      </div>
    </div>
  )
}

// ---------- type breakdown + top locations ----------

function BreakdownCards({ stats, fmt }: { stats: AthleteStats; fmt: (n: number) => string }) {
  const { t } = useTranslation('training')

  const typeRows = (
    [
      ['personal', stats.byType.personal],
      ['individualSlot', stats.byType.individualSlot],
      ['course', stats.byType.course],
      ['training', stats.byType.training],
      ['workshop', stats.byType.workshop],
    ] as const
  ).filter(([, count]) => count > 0)

  const hasTypes = typeRows.length > 0
  const hasLocations = stats.topLocations.length > 0
  if (!hasTypes && !hasLocations) return null

  return (
    <div className={clsx('grid gap-3', hasTypes && hasLocations && 'md:grid-cols-2')}>
      {hasTypes && (
        <BreakdownCard
          icon={<Dumbbell className="w-4 h-4 text-surface-400" />}
          title={t('stats.byType.title')}
          rows={typeRows.map(([key, count]) => ({ label: t(`stats.byType.${key}`), count }))}
          fmt={fmt}
        />
      )}
      {hasLocations && (
        <BreakdownCard
          icon={<MapPin className="w-4 h-4 text-surface-400" />}
          title={t('stats.locations.title')}
          rows={stats.topLocations.map((l) => ({ label: l.name, count: l.count }))}
          fmt={fmt}
        />
      )}
    </div>
  )
}

function BreakdownCard({
  icon,
  title,
  rows,
  fmt,
}: {
  icon: React.ReactNode
  title: string
  rows: { label: string; count: number }[]
  fmt: (n: number) => string
}) {
  const max = Math.max(...rows.map((r) => r.count))
  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4">
      <div className="flex items-center gap-2 text-sm font-medium text-surface-300 mb-3">
        {icon}
        {title}
      </div>
      <div className="space-y-2.5">
        {rows.map((row) => (
          <div key={row.label}>
            <div className="flex items-baseline justify-between gap-3 mb-1">
              <span className="text-sm text-surface-300 truncate">{row.label}</span>
              <span className="text-sm font-medium text-surface-200 tabular-nums">{fmt(row.count)}</span>
            </div>
            <div className="h-1.5 rounded-full bg-surface-800">
              <div
                className="h-1.5 rounded-full bg-green-500/70"
                style={{ width: `${Math.max((row.count / max) * 100, 3)}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

// ---------- milestone badges ----------

function BadgeRow({ stats, fmt }: { stats: AthleteStats; fmt: (n: number) => string }) {
  const { t } = useTranslation('training')

  const earned: { key: string; icon: React.ReactNode; label: string }[] = []
  for (const m of TOTAL_MILESTONES) {
    if (stats.totalCount >= m) {
      earned.push({ key: `total-${m}`, icon: <Award className="w-3.5 h-3.5" />, label: t('stats.badges.total', { count: m }) })
    }
  }
  for (const m of STREAK_MILESTONES) {
    if (stats.bestStreakWeeks >= m) {
      earned.push({ key: `streak-${m}`, icon: <Flame className="w-3.5 h-3.5" />, label: t('stats.badges.streak', { count: m }) })
    }
  }
  if (stats.firstActivityDate && new Date(stats.firstActivityDate) <= subDays(new Date(), 365)) {
    earned.push({ key: 'one-year', icon: <Trophy className="w-3.5 h-3.5" />, label: t('stats.badges.oneYear') })
  }

  const nextTotal = TOTAL_MILESTONES.find((m) => m > stats.totalCount)

  if (earned.length === 0 && !nextTotal) return null

  return (
    <div className="flex flex-wrap items-center gap-2">
      {earned.map((badge) => (
        <span
          key={badge.key}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-amber-500/30 bg-amber-500/10 text-amber-300 text-xs font-medium"
        >
          {badge.icon}
          {badge.label}
        </span>
      ))}
      {nextTotal && (
        <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full border border-surface-700 bg-surface-900 text-surface-500 text-xs font-medium tabular-nums">
          <Award className="w-3.5 h-3.5" />
          {t('stats.badges.nextGoal', { current: fmt(stats.totalCount), target: fmt(nextTotal) })}
        </span>
      )}
    </div>
  )
}
