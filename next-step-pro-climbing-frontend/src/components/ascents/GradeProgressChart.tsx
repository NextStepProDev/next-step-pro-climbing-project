import { useTranslation } from 'react-i18next'
import type { GradeProgressPoint } from '../../types'

interface GradeProgressChartProps {
  points: GradeProgressPoint[]
}

const WIDTH = 520
const HEIGHT = 150
const PADDING = { top: 12, right: 12, bottom: 22, left: 40 }

/**
 * Best grade per year, all-time. Hand-drawn SVG, no charting library — same call as WeightChart.
 *
 * The Y axis is the grade RANK, and the labels are the grade labels that rank belongs to. That
 * is the only way to plot a scale whose steps are named rather than numeric: 7a to 7a+ is one
 * step, and so is 8c to 8c+, however differently they read.
 */
export function GradeProgressChart({ points }: GradeProgressChartProps) {
  const { t } = useTranslation('ascents')

  // One year is a dot, not a trend — the chart earns its space from the second year on
  if (points.length < 2) return null

  const ranks = points.flatMap(point => [point.bestRank, point.bestOnsightRank])
    .filter((rank): rank is number => rank !== null)
  const minRank = Math.min(...ranks)
  const maxRank = Math.max(...ranks)
  const span = Math.max(maxRank - minRank, 10)

  const innerWidth = WIDTH - PADDING.left - PADDING.right
  const innerHeight = HEIGHT - PADDING.top - PADDING.bottom

  const x = (index: number) => PADDING.left
    + (points.length === 1 ? innerWidth / 2 : (index / (points.length - 1)) * innerWidth)
  const y = (rank: number) => PADDING.top + innerHeight - ((rank - minRank) / span) * innerHeight

  const best = points.map((point, index) => ({ point, index }))
    .filter(({ point }) => point.bestRank !== null)
  const onsights = points.map((point, index) => ({ point, index }))
    .filter(({ point }) => point.bestOnsightRank !== null)

  const line = (series: typeof best, pick: (p: GradeProgressPoint) => number | null) =>
    series.map(({ point, index }, position) =>
      `${position === 0 ? 'M' : 'L'} ${x(index)} ${y(pick(point) as number)}`).join(' ')

  const first = points[0]
  const last = points[points.length - 1]

  return (
    <div className="space-y-1.5">
      <h4 className="text-xs font-medium text-surface-400 uppercase tracking-wide">
        {t('stats.progression')}
      </h4>
      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="w-full h-auto"
        role="img"
        aria-label={`${t('stats.progression')}: ${first.year}–${last.year}`}
      >
        {/* Axis labels are the grades themselves — a bare rank of 150 means nothing */}
        <text x={PADDING.left - 6} y={y(maxRank) + 4} textAnchor="end"
              className="fill-surface-500 text-[10px]">
          {gradeLabelForRank(points, maxRank)}
        </text>
        <text x={PADDING.left - 6} y={y(minRank) + 4} textAnchor="end"
              className="fill-surface-500 text-[10px]">
          {gradeLabelForRank(points, minRank)}
        </text>

        <path d={line(best, p => p.bestRank)} fill="none" stroke="rgb(56 189 248)" strokeWidth={2} />
        {best.map(({ point, index }) => (
          <circle key={`b-${point.year}`} cx={x(index)} cy={y(point.bestRank as number)} r={3}
                  className="fill-sky-400" />
        ))}

        {onsights.length > 1 && (
          <path d={line(onsights, p => p.bestOnsightRank)} fill="none"
                stroke="rgb(74 222 128)" strokeWidth={2} strokeDasharray="4 3" />
        )}
        {onsights.map(({ point, index }) => (
          <circle key={`o-${point.year}`} cx={x(index)} cy={y(point.bestOnsightRank as number)} r={3}
                  className="fill-green-400" />
        ))}

        {points.map((point, index) => (
          <text key={point.year} x={x(index)} y={HEIGHT - 6} textAnchor="middle"
                className="fill-surface-500 text-[10px]">
            {point.year}
          </text>
        ))}
      </svg>

      <div className="flex items-center gap-3 text-[11px] text-surface-500">
        <span className="inline-flex items-center gap-1">
          <span className="w-3 h-0.5 bg-sky-400" aria-hidden="true" />
          {t('stats.progressionBest')}
        </span>
        {onsights.length > 0 && (
          <span className="inline-flex items-center gap-1">
            <span className="w-3 h-0.5 bg-green-400" aria-hidden="true" />
            {t('stats.progressionOnsight')}
          </span>
        )}
      </div>
    </div>
  )
}

function gradeLabelForRank(points: GradeProgressPoint[], rank: number): string {
  const match = points.find(point => point.bestRank === rank)
    ?? points.find(point => point.bestOnsightRank === rank)
  if (match?.bestRank === rank) return match.bestGradeLabel ?? ''
  return match?.bestOnsightLabel ?? ''
}
