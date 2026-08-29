import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { format } from 'date-fns'
import { BarChart3, MapPin, Mountain, Repeat, Star } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { GradePyramid } from './GradePyramid'
import { GradeProgressChart } from './GradeProgressChart'
import { QueryError } from '../ui/QueryError'
import { parseCalendarDate } from '../../utils/calendarDate'
import { keepWithinEntity } from '../../utils/queryEntity'
import type { AscentAdapter } from './ascentAdapter'
import type { AscentDisciplineStats, AscentTerrain, MountainStats } from '../../types'

interface AscentStatsPanelProps {
  api: AscentAdapter
  scopeKey: string
  terrain: AscentTerrain
  /** The year parameter the table is filtered on ('' = server picks, '2026', 'all'). */
  year: string
  /** Which year the server actually served the table; null when the table covers every year. */
  selectedYear: number | null
}

/** Whether the pyramid and the personal bests describe the table's year or the whole logbook. */
type StatsScope = 'year' | 'all'

export function AscentStatsPanel({ api, scopeKey, terrain, year, selectedYear }: AscentStatsPanelProps) {
  const { t } = useTranslation('ascents')

  // Deliberately its own state, not the table's filter: "what did I climb in 2026" and "what is
  // my pyramid" are different questions, and the second one is usually asked all-time. Starts on
  // the table's year, so the numbers describe what is on screen until you say otherwise.
  const [scope, setScope] = useState<StatsScope>('year')
  const effectiveYear = scope === 'all' ? 'all' : year

  const key = ['ascents', 'stats', scopeKey, terrain, effectiveYear] as const
  const statsQuery = useQuery({
    queryKey: key,
    queryFn: () => api.getStats(terrain, effectiveYear),
    // Entity first, page last: switching athlete must not show the previous one's numbers
    placeholderData: (previous, previousQuery) => keepWithinEntity(previous, previousQuery, key, 1),
  })

  // With the table already on every year the toggle would offer the choice it is already making
  const scopeToggle = selectedYear === null ? null : (
    <div className="flex gap-1 p-0.5 bg-surface-800 border border-surface-700 rounded-lg"
         role="group" aria-label={t('stats.scopeLabel')}>
      {([['year', String(selectedYear)], ['all', t('filters.allYears')]] as const).map(([value, label]) => (
        <button
          key={value}
          type="button"
          onClick={() => setScope(value)}
          aria-pressed={scope === value}
          className={`px-2.5 py-1 text-xs font-medium rounded-md transition-colors ${
            scope === value ? 'bg-primary-600 text-white' : 'text-surface-400 hover:text-surface-200'
          }`}
        >
          {label}
        </button>
      ))}
    </div>
  )

  if (statsQuery.isLoading) {
    return (
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="h-24 bg-surface-900 rounded-xl border border-surface-800 animate-pulse" />
        ))}
      </div>
    )
  }

  if (statsQuery.isError) {
    return <QueryError error={statsQuery.error} onRetry={() => statsQuery.refetch()} />
  }

  const stats = statsQuery.data
  if (!stats) return null

  // The gate looks at the whole logbook, not at one block: telling a boulderer that statistics
  // appear after their first ascent, with the numbers already computed, is the TASK mistake again
  if (stats.totalAscents === 0) {
    return (
      <div className="bg-surface-900 rounded-xl border border-surface-800 p-6 text-center">
        <BarChart3 className="w-8 h-8 mx-auto mb-2 text-surface-600" aria-hidden="true" />
        <p className="text-sm text-surface-400">{t('stats.empty')}</p>
      </div>
    )
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-medium text-surface-300 uppercase tracking-wide">
          <BarChart3 className="w-4 h-4" aria-hidden="true" />
          {t('stats.title')}
        </h3>
        {scopeToggle}
      </div>

      <div className={`grid grid-cols-2 gap-3 ${terrain === 'ROCK' ? 'lg:grid-cols-4' : 'lg:grid-cols-2'}`}>
        <StatTile
          icon={<Mountain className="w-4 h-4" aria-hidden="true" />}
          value={stats.totalAscents}
          label={t('stats.totalAscents')}
          sub={stats.firstAscentDate
            ? t('stats.since', { date: format(parseCalendarDate(stats.firstAscentDate), 'MM.yyyy') })
            : undefined}
        />
        <StatTile
          icon={<MapPin className="w-4 h-4" aria-hidden="true" />}
          value={stats.areaCount}
          label={t('stats.areas')}
          sub={t('stats.crags', { count: stats.cragCount })}
        />
        {/* Attempts and route ratings are rock conventions — in the mountains both tiles would
            show a permanent dash, which is worse than not asking the question */}
        {terrain === 'ROCK' && (
          <>
            <StatTile
              icon={<Repeat className="w-4 h-4" aria-hidden="true" />}
              value={stats.avgAttemptsToRedpoint ?? '—'}
              label={t('stats.avgAttempts')}
              // The denominator is not decoration: attempts are optional, so "3,4" alone would
              // not say whether it came from two redpoints or two hundred
              sub={stats.redpointsWithAttempts > 0
                ? t('stats.avgAttemptsFrom', { count: stats.redpointsWithAttempts })
                : t('stats.avgAttemptsEmpty')}
            />
            <StatTile
              icon={<Star className="w-4 h-4" aria-hidden="true" />}
              value={stats.avgQualityStars ?? '—'}
              label={t('stats.avgStars')}
            />
          </>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        {stats.disciplines.map(block => (
          <DisciplineBlock key={block.discipline} block={block} />
        ))}
        {stats.mountain && <MountainBlock stats={stats.mountain} />}
      </div>

      {stats.topAreas.length > 0 && (
        <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-2">
          <h4 className="text-xs font-medium text-surface-400 uppercase tracking-wide">
            {t('stats.topAreas')}
          </h4>
          <div className="space-y-1.5">
            {stats.topAreas.map(area => (
              <div key={area.area} className="flex items-center gap-2">
                <span className="flex-1 text-sm text-surface-300 truncate">{area.area}</span>
                <div className="w-32 h-1.5 rounded-full bg-surface-800 overflow-hidden">
                  <div
                    className="h-full bg-green-500"
                    style={{ width: `${(area.ascentCount / stats.topAreas[0].ascentCount) * 100}%` }}
                  />
                </div>
                <span className="w-8 text-right text-sm text-surface-400 tabular-nums">
                  {area.ascentCount}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {stats.disciplines.length > 1 && (
        <p className="text-xs text-surface-500">{t('stats.scaleNote')}</p>
      )}
    </section>
  )
}

/**
 * The mountain half. Every total carries the number of entries it was built from — length,
 * pitches and duration are optional, so a bare "4200 m" would not say whether that is the season
 * or the two entries somebody bothered to measure.
 */
function MountainBlock({ stats }: { stats: MountainStats }) {
  const { t } = useTranslation('ascents')
  const hours = Math.round(stats.totalMinutes / 6) / 10

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-4">
      <div className="flex items-baseline justify-between gap-2">
        <h4 className="text-sm font-semibold text-surface-100">{t('mountain.title')}</h4>
        <span className="text-xs text-surface-500 tabular-nums">
          {t('mountain.summer')} {stats.summerCount} · {t('mountain.winter')} {stats.winterCount}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <MountainFigure value={stats.totalMeters} unit="m" label={t('mountain.meters')}
                        sub={t('mountain.fromEntries', { count: stats.entriesWithLength })} />
        <MountainFigure value={stats.totalPitches} label={t('mountain.pitches')}
                        sub={t('mountain.fromEntries', { count: stats.entriesWithPitches })} />
        <MountainFigure value={hours} unit="h" label={t('mountain.time')}
                        sub={t('mountain.fromEntries', { count: stats.entriesWithDuration })} />
        <MountainFigure value={stats.summitCount} label={t('mountain.summits')} />
      </div>

      <GradePyramid rows={stats.pyramid} />

      {Object.entries(stats.hardestByStyle).length > 0 && (
        <div className="space-y-1.5">
          <h5 className="text-xs font-medium text-surface-400 uppercase tracking-wide">
            {t('stats.hardest')}
          </h5>
          <ul className="space-y-1">
            {Object.entries(stats.hardestByStyle).map(([style, best]) => (
              <li key={style} className="flex items-center gap-2 text-sm">
                <span className="w-16 shrink-0 text-xs font-medium text-surface-400">
                  {t(`style.${style}`)}
                </span>
                <span className="font-semibold text-surface-100 tabular-nums">{best.gradeLabel}</span>
                <span className="text-surface-400 truncate">{best.routeName}</span>
                <span className="ml-auto shrink-0 text-xs text-surface-500 truncate max-w-[8rem]">
                  {best.crag}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {stats.leadPyramid.length > 0 && (
        <div className="space-y-1.5">
          <h5 className="text-xs font-medium text-surface-400 uppercase tracking-wide">
            {t('mountain.lead')}
          </h5>
          <GradePyramid rows={stats.leadPyramid} showHeading={false} />
          {stats.hardestLed && (
            <p className="text-sm text-surface-400">
              <span className="font-semibold text-surface-100 tabular-nums">
                {stats.hardestLed.gradeLabel}
              </span>
              {' · '}{stats.hardestLed.routeName}
              <span className="text-surface-600"> · </span>
              {t('mountain.ledPitches', { count: stats.ledPitchesTotal })}
            </p>
          )}
        </div>
      )}
    </div>
  )
}

function MountainFigure({ value, unit, label, sub }: {
  value: number
  unit?: string
  label: string
  sub?: string
}) {
  return (
    <div>
      <div className="text-xl font-bold text-surface-100 tabular-nums">
        {value}{unit && <span className="text-sm font-normal text-surface-400"> {unit}</span>}
      </div>
      <div className="text-xs text-surface-400">{label}</div>
      {sub && <div className="text-[11px] text-surface-500">{sub}</div>}
    </div>
  )
}

function DisciplineBlock({ block }: { block: AscentDisciplineStats }) {
  const { t } = useTranslation('ascents')
  const hardest = Object.entries(block.hardestByStyle)

  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4 space-y-4">
      <div className="flex items-baseline justify-between gap-2">
        <h4 className="text-sm font-semibold text-surface-100">
          {t(`discipline.${block.discipline}`)}
        </h4>
        <span className="text-xs text-surface-500 tabular-nums">
          {block.ascentCount} · {t('stats.onsightRate')} {block.onsightRatePercent ?? 0}%
        </span>
      </div>

      <GradePyramid rows={block.pyramid} />

      {hardest.length > 0 && (
        <div className="space-y-1.5">
          <h5 className="text-xs font-medium text-surface-400 uppercase tracking-wide">
            {t('stats.hardest')}
          </h5>
          <ul className="space-y-1">
            {hardest.map(([style, best]) => (
              <li key={style} className="flex items-center gap-2 text-sm">
                <span className="w-16 shrink-0 text-xs font-medium text-surface-400">{t(`style.${style}`)}</span>
                <span className="font-semibold text-surface-100 tabular-nums">{best.gradeLabel}</span>
                {/* The route, because "8a" without a name is a number, not a memory */}
                <span className="text-surface-400 truncate">{best.routeName}</span>
                <span className="ml-auto shrink-0 text-xs text-surface-500 truncate max-w-[8rem]">
                  {best.crag}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <GradeProgressChart points={block.progressionByYear} />
    </div>
  )
}

function StatTile({ icon, value, label, sub }: {
  icon: React.ReactNode
  value: React.ReactNode
  label: string
  sub?: string
}) {
  return (
    <div className="bg-surface-900 rounded-xl border border-surface-800 p-4">
      <div className="flex items-center gap-1.5 text-surface-500 mb-1">{icon}</div>
      <div className="text-2xl font-bold text-surface-100 tabular-nums">{value}</div>
      <div className="text-xs text-surface-400">{label}</div>
      {sub && <div className="text-[11px] text-surface-500 mt-0.5">{sub}</div>}
    </div>
  )
}
