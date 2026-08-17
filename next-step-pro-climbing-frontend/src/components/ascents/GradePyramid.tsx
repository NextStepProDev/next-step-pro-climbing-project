import { useTranslation } from 'react-i18next'
import type { AscentStyle, PyramidRow } from '../../types'

interface GradePyramidProps {
  rows: PyramidRow[]
  /** Set to false where the caller already wrote a heading above it (e.g. "pitches led"). */
  showHeading?: boolean
}

/**
 * Horizontal stacked bars, hardest grade on top. CSS widths rather than a charting library —
 * same call as WeightChart, and this one does not even need axes.
 *
 * Split by style on purpose: a pyramid without it says how many, a pyramid with it says how —
 * and "how" is the entire content. Ten redpoints at 7a and ten onsights at 7a are different
 * climbers.
 */
export function GradePyramid({ rows, showHeading = true }: GradePyramidProps) {
  const { t } = useTranslation('ascents')

  if (rows.length === 0) return null

  const widest = Math.max(...rows.map(row => row.total))

  return (
    <div className="space-y-1.5">
      {showHeading && (
        <h4 className="text-xs font-medium text-surface-400 uppercase tracking-wide">
          {t('stats.pyramid')}
        </h4>
      )}
      <div className="space-y-1">
        {rows.map(row => (
          <div key={row.grade} className="flex items-center gap-2">
            <span className="w-10 shrink-0 text-right text-xs font-semibold text-surface-300 tabular-nums">
              {row.gradeLabel}
            </span>
            {/* Centred, and on bare background: the SHAPE is the message. A wide base means a
                level that was actually built, a narrow one that the personal best was a single
                shot — and neither reads off bars pinned to the left edge. The track behind the
                bar goes too, because a full-width backdrop straightens out the very silhouette
                this is drawing. */}
            <div className="flex-1 flex justify-center">
              <div
                className="flex h-5 rounded overflow-hidden"
                // A single ascent under a ten-ascent rung would round to a sliver; this keeps
                // every rung visible (and hoverable) without distorting the ones that matter
                style={{ width: `${(row.total / widest) * 100}%`, minWidth: '0.75rem' }}
              >
                {STYLE_ORDER.filter(style => row.byStyle[style]).map(style => (
                  <div
                    key={style}
                    className={`h-full ${STYLE_COLOR[style]}`}
                    style={{ width: `${((row.byStyle[style] ?? 0) / row.total) * 100}%` }}
                    title={`${row.gradeLabel} · ${style}: ${row.byStyle[style]}`}
                  />
                ))}
              </div>
            </div>
            <span className="w-6 shrink-0 text-xs text-surface-500 tabular-nums">{row.total}</span>
          </div>
        ))}
      </div>
      <StyleLegend rows={rows} />
    </div>
  )
}

/** Cleanest first, so the bar reads left-to-right the way the styles rank. */
const STYLE_ORDER: AscentStyle[] = [
  'FREE_SOLO', 'SOLO', 'OS_GU', 'OS', 'FLASH_GU', 'FLASH', 'GU', 'RP', 'HP', 'TR', 'A0',
]

// Greens for the clean end, cooling off towards toprope — the same "green means done well"
// vocabulary the training heatmap already uses.
const STYLE_COLOR: Record<AscentStyle, string> = {
  // Free solo gets the one colour nothing else uses, and a ring to lift it off the bar: it is
  // not a cleaner tick than an onsight, it is a different thing entirely, and on a chart where
  // everything else is a shade of "how clean" it has to stop reading as one more step up.
  // Violet rather than a red: red sits next to amber SOLO and reads as its hotter version, which
  // is exactly the "one step further up the same scale" this colour has to avoid.
  FREE_SOLO: 'bg-violet-500 ring-1 ring-inset ring-violet-200/70',
  // Solo shares the "alone" family in amber, but without the ring — roped is roped
  SOLO: 'bg-amber-500',
  OS: 'bg-green-400',
  FLASH: 'bg-green-600',
  RP: 'bg-sky-500',
  TR: 'bg-surface-600',
  // Aid: the weakest style, so the flattest colour on the chart
  A0: 'bg-surface-700',
  // Trad's dialect never shares a bar with sport's — the blocks are per discipline — so these
  // only have to be told apart from each other. They keep the same vocabulary anyway: green for
  // the first-go end, cooling towards the worked ones, so a glance reads the same way in both.
  OS_GU: 'bg-emerald-400',
  FLASH_GU: 'bg-emerald-600',
  GU: 'bg-teal-500',
  HP: 'bg-indigo-500',
}

function StyleLegend({ rows }: { rows: PyramidRow[] }) {
  const { t } = useTranslation('ascents')
  const present = STYLE_ORDER.filter(style => rows.some(row => row.byStyle[style]))

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pl-12 pt-1">
      {present.map(style => (
        <span key={style} className="inline-flex items-center gap-1 text-[11px] text-surface-500">
          <span className={`w-2.5 h-2.5 rounded-sm ${STYLE_COLOR[style]}`} aria-hidden="true" />
          {t(`style.${style}`)}
        </span>
      ))}
    </div>
  )
}
