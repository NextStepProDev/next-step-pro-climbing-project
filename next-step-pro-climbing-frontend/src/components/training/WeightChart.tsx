import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { differenceInCalendarDays, format, parseISO } from 'date-fns'
import clsx from 'clsx'
import { Trash2 } from 'lucide-react'
import { useDateLocale } from '../../utils/dateFnsLocale'
import type { WeightEntry } from '../../types'

/**
 * Hand-rolled SVG rather than a charting library: this is one series of at most ~120 points,
 * and a library would cost more bundle than the whole training zone.
 *
 * <p>Two marks, deliberately unequal in weight. Daily readings are faint dots — they swing a
 * kilo on water and salt and mean little on their own. The 7-day trend is the solid line and
 * the only labelled value, because that is the number worth reacting to.
 */

// Green, matching the heatmap. `primary` in this theme is a dark grey-blue that is unreadable
// as a data colour on the dark surface — see the palette note in CLAUDE.md.
const TREND_STROKE = 'rgb(34 197 94)' // green-500

const W = 640
const H = 190
const PAD = { top: 12, right: 14, bottom: 22, left: 40 }
const PLOT_W = W - PAD.left - PAD.right
const PLOT_H = H - PAD.top - PAD.bottom

/**
 * Below one dot per two pixels the daily readings stop being separate marks and turn into a
 * grey smear behind the line — noise pretending to be data. Past that density we draw the
 * trend alone, and the legend stops naming a mark that is not on screen.
 */
const MIN_PX_PER_DOT = 2
const MAX_DOTS = Math.floor(PLOT_W / MIN_PX_PER_DOT)

interface WeightChartProps {
  entries: WeightEntry[]
  isStale?: boolean
  // Athlete only — omitted for the coach, who never edits somebody else's readings
  onDelete?: (measuredOn: string) => void
}

export function WeightChart({ entries, isStale, onDelete }: WeightChartProps) {
  const { t, i18n } = useTranslation('training')
  const locale = useDateLocale()
  const [tableOpen, setTableOpen] = useState(false)
  const [hovered, setHovered] = useState<number | null>(null)

  const geometry = useMemo(() => {
    if (entries.length === 0) return null

    const first = parseISO(entries[0].measuredOn)
    const last = parseISO(entries[entries.length - 1].measuredOn)
    // Space points by the CALENDAR, not by index — a two-week gap must look like a gap
    const span = Math.max(1, differenceInCalendarDays(last, first))

    const values = entries.flatMap((e) => [e.weightKg, e.trendKg])
    const min = Math.min(...values)
    const max = Math.max(...values)
    // Never anchor at zero: on a 70 kg athlete it would flatten every real change to a line.
    // The floor keeps a perfectly flat series from rendering as a zigzag.
    const padding = Math.max(0.6, (max - min) * 0.2)
    const lo = min - padding
    const hi = max + padding

    const x = (date: string) => PAD.left + (differenceInCalendarDays(parseISO(date), first) / span) * PLOT_W
    const y = (value: number) => PAD.top + PLOT_H - ((value - lo) / (hi - lo)) * PLOT_H

    return { x, y, lo, hi, first, span }
  }, [entries])

  const fmt = (n: number) => n.toLocaleString(i18n.language, { minimumFractionDigits: 1, maximumFractionDigits: 1 })

  if (!geometry || entries.length === 0) return null
  const { x, y, lo, hi } = geometry

  const trendPath = entries
    .map((e, i) => `${i === 0 ? 'M' : 'L'} ${x(e.measuredOn).toFixed(1)} ${y(e.trendKg).toFixed(1)}`)
    .join(' ')

  const lastEntry = entries[entries.length - 1]

  // Roughly the width of "100,0 kg" at 10px semibold. The current-trend label is normally
  // end-anchored to the LEFT of its dot, which works because the last reading sits at the right
  // edge of the plot. A single reading is the exception: it sits at the LEFT edge (span collapses
  // to one day), and an end-anchored label there runs past x=0 and gets clipped by the viewBox.
  const LABEL_W = 46
  const labelFitsLeft = !lastEntry || x(lastEntry.measuredOn) - PAD.left >= LABEL_W

  // Three gridlines is enough to read a value off; more just adds noise behind the data
  const gridValues = [lo, (lo + hi) / 2, hi]

  // Two or three date labels, deduplicated (a short series can collapse to one day)
  const tickIndexes = [...new Set([0, Math.floor((entries.length - 1) / 2), entries.length - 1])]

  const nearestIndex = (clientX: number, rect: DOMRect) => {
    const svgX = ((clientX - rect.left) / rect.width) * W
    let best = 0
    let bestDist = Infinity
    entries.forEach((entry, i) => {
      const dist = Math.abs(x(entry.measuredOn) - svgX)
      if (dist < bestDist) {
        bestDist = dist
        best = i
      }
    })
    return best
  }

  const hoveredEntry = hovered != null ? entries[hovered] : null
  // Too many points to render as distinct marks — show the trend line alone
  const showDots = entries.length <= MAX_DOTS

  return (
    <div className="space-y-2">
      <div className={clsx('relative transition-opacity', isStale && 'opacity-60')}>
        <svg
          viewBox={`0 0 ${W} ${H}`}
          className="w-full h-auto"
          role="img"
          aria-label={t('weight.chartLabel')}
          onMouseLeave={() => setHovered(null)}
          onMouseMove={(e) => setHovered(nearestIndex(e.clientX, e.currentTarget.getBoundingClientRect()))}
        >
          {/* Gridlines + y labels */}
          {gridValues.map((value) => (
            <g key={value}>
              <line
                x1={PAD.left}
                x2={W - PAD.right}
                y1={y(value)}
                y2={y(value)}
                className="stroke-surface-800"
                strokeWidth="1"
              />
              <text
                x={PAD.left - 6}
                y={y(value) + 3}
                textAnchor="end"
                className="fill-surface-500"
                style={{ fontSize: 9 }}
              >
                {fmt(value)}
              </text>
            </g>
          ))}

          {/* x labels */}
          {tickIndexes.map((i) => (
            <text
              key={entries[i].measuredOn}
              x={x(entries[i].measuredOn)}
              y={H - 6}
              textAnchor={i === 0 ? 'start' : i === entries.length - 1 ? 'end' : 'middle'}
              className="fill-surface-500"
              style={{ fontSize: 9 }}
            >
              {format(parseISO(entries[i].measuredOn), 'd MMM', { locale })}
            </text>
          ))}

          {/* Hover guide */}
          {hoveredEntry && (
            <line
              x1={x(hoveredEntry.measuredOn)}
              x2={x(hoveredEntry.measuredOn)}
              y1={PAD.top}
              y2={PAD.top + PLOT_H}
              className="stroke-surface-600"
              strokeWidth="1"
              strokeDasharray="3 3"
            />
          )}

          {/* Daily readings: quiet on purpose, and dropped entirely once they would smear */}
          {showDots &&
            entries.map((entry) => (
              <circle
                key={entry.measuredOn}
                cx={x(entry.measuredOn)}
                cy={y(entry.weightKg)}
                r="2"
                className="fill-surface-500"
                opacity="0.65"
              />
            ))}

          {/* The trend — the line that actually means something */}
          {entries.length > 1 && (
            <path d={trendPath} fill="none" stroke={TREND_STROKE} strokeWidth="2" strokeLinejoin="round" />
          )}

          {/* Only the current trend is labelled; everything else lives in the table */}
          {lastEntry && (
            <>
              <circle
                cx={x(lastEntry.measuredOn)}
                cy={y(lastEntry.trendKg)}
                r="4.5"
                fill={TREND_STROKE}
                className="stroke-surface-900"
                strokeWidth="2"
              />
              <text
                x={x(lastEntry.measuredOn) + (labelFitsLeft ? -10 : 10)}
                y={y(lastEntry.trendKg) - 8}
                textAnchor={labelFitsLeft ? 'end' : 'start'}
                fill={TREND_STROKE}
                style={{ fontSize: 10, fontWeight: 600 }}
              >
                {fmt(lastEntry.trendKg)} kg
              </text>
            </>
          )}
        </svg>

        {hoveredEntry && (
          <div
            className="absolute top-0 pointer-events-none px-2 py-1 rounded-md bg-surface-800 border border-surface-700 text-[11px] text-surface-200 whitespace-nowrap shadow-lg"
            style={{ left: `${(x(hoveredEntry.measuredOn) / W) * 100}%`, transform: 'translateX(-50%)' }}
          >
            <div className="text-surface-400">
              {format(parseISO(hoveredEntry.measuredOn), 'dd.MM.yyyy')}
            </div>
            <div>{fmt(hoveredEntry.weightKg)} kg</div>
            <div className="text-surface-400">
              {t('weight.legendTrend')}: {fmt(hoveredEntry.trendKg)} kg
            </div>
          </div>
        )}
      </div>

      {/* Legend names both marks, so identity never rests on colour alone */}
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 text-[11px] text-surface-500">
        <div className="flex items-center gap-4">
          <span className="inline-flex items-center gap-1.5">
            <span className="w-4 h-0.5 rounded" style={{ backgroundColor: TREND_STROKE }} />
            {t('weight.legendTrend')}
          </span>
          {/* The legend names only what is actually drawn */}
          {showDots && (
            <span className="inline-flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-surface-500" />
              {t('weight.legendDaily')}
            </span>
          )}
          {!showDots && <span>{t('weight.dailyHiddenHint')}</span>}
        </div>
        <button
          onClick={() => setTableOpen((open) => !open)}
          className="text-surface-400 hover:text-surface-200 transition-colors underline underline-offset-2"
        >
          {tableOpen ? t('weight.hideTable') : t('weight.showTable')}
        </button>
      </div>

      {/* The a11y path: no value may be reachable only by hovering */}
      {tableOpen && (
        <div className="max-h-56 overflow-y-auto rounded-lg border border-surface-800">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-surface-800 text-surface-300">
              <tr>
                <th className="text-left font-medium px-3 py-1.5">{t('weight.tableDate')}</th>
                <th className="text-right font-medium px-3 py-1.5">{t('weight.tableWeight')}</th>
                <th className="text-right font-medium px-3 py-1.5">{t('weight.legendTrend')}</th>
                {onDelete && <th className="w-10" aria-label={t('weight.deleteEntry')} />}
              </tr>
            </thead>
            <tbody>
              {[...entries].reverse().map((entry) => (
                  <tr key={entry.measuredOn} className="border-t border-surface-800/70">
                    <td className="px-3 py-1.5 text-surface-300">
                      {format(parseISO(entry.measuredOn), 'dd.MM.yyyy')}
                    </td>
                    <td className="px-3 py-1.5 text-right text-surface-200 tabular-nums">
                      {fmt(entry.weightKg)}
                    </td>
                    <td className="px-3 py-1.5 text-right text-surface-400 tabular-nums">
                      {fmt(entry.trendKg)}
                    </td>
                    {/* Correcting a wrong number is an overwrite; only a reading on a day the
                        athlete never weighed in needs removing, and nothing can overwrite that */}
                    {onDelete && (
                      <td className="px-2 py-1.5 text-right">
                        <button
                          type="button"
                          onClick={() => onDelete(entry.measuredOn)}
                          title={t('weight.deleteEntry')}
                          aria-label={`${t('weight.deleteEntry')} ${format(parseISO(entry.measuredOn), 'dd.MM.yyyy')}`}
                          className="p-1 rounded text-surface-500 hover:text-rose-300 hover:bg-surface-800 transition-colors"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    )}
                  </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
