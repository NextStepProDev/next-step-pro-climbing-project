import { Fragment, useState } from 'react'
import { format } from 'date-fns'
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronDown, EyeOff, Globe, Pencil, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import clsx from 'clsx'
import { StarRating } from './StarRating'
import { parseCalendarDate } from '../../utils/calendarDate'
import { renderRichText, toPlainText } from '../../utils/renderRichText'
import type { AscentSortKey, SortDirection } from './ascentFiltering'
import type { Ascent, AscentTerrain } from '../../types'

interface AscentTableProps {
  terrain: AscentTerrain
  entries: Ascent[]
  sortKey: AscentSortKey
  sortDirection: SortDirection
  onSort: (key: AscentSortKey) => void
  onEdit?: (ascent: Ascent) => void
  onDelete?: (ascent: Ascent) => void
  /** Admin only: takes one entry off the public list, or puts it back. */
  onSetPublicVisibility?: (ascent: Ascent, hidden: boolean) => void
  /** Which entry is mid-request, so its control can be disabled without a per-row state. */
  pendingVisibilityId?: string | null
}

/**
 * The logbook itself. Read-only when `onEdit`/`onDelete` are absent — that is how the admin's
 * view renders, and the absent handler is the whole mechanism: there is no "can I edit" flag to
 * get wrong.
 *
 * <p>`onSetPublicVisibility` is the mirror image: present only for the admin, absent for the
 * author. It is a separate prop rather than a third item on the edit pair because it answers a
 * different question — not "may I change this entry" but "does this entry belong on the public
 * list". The two never appear together.
 */
export function AscentTable({
  terrain, entries, sortKey, sortDirection, onSort, onEdit, onDelete,
  onSetPublicVisibility, pendingVisibilityId,
}: AscentTableProps) {
  const { t } = useTranslation('ascents')
  const editable = Boolean(onEdit && onDelete)
  const moderatable = Boolean(onSetPublicVisibility)
  const isMountain = terrain === 'MOUNTAIN'

  // A set rather than one open id: comparing two entries is the reason to open one at all.
  // Ids of rows filtered away simply stop matching, so nothing has to prune this.
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(new Set())
  const toggle = (id: string) => setExpanded(current => {
    const next = new Set(current)
    if (!next.delete(id)) next.add(id)
    return next
  })

  // date, route, grade, season/discipline, style, area, crag — then whatever the terrain adds
  const columnCount = 7 + (isMountain ? 4 : 2) + (moderatable ? 1 : 0) + (editable ? 1 : 0)

  const header = (key: AscentSortKey, label: string, className = '') => (
    <th className={`px-3 py-2 text-left font-medium ${className}`}>
      <button
        type="button"
        onClick={() => onSort(key)}
        className="inline-flex items-center gap-1 hover:text-surface-100 transition"
      >
        {label}
        {sortKey === key
          ? (sortDirection === 'asc'
            ? <ArrowUp className="w-3 h-3" aria-hidden="true" />
            : <ArrowDown className="w-3 h-3" aria-hidden="true" />)
          : <ArrowUpDown className="w-3 h-3 opacity-40" aria-hidden="true" />}
      </button>
    </th>
  )

  return (
    // `relative` is load-bearing, not decoration. The sr-only labels inside the header cells are
    // `position: absolute`, and without a positioned ancestor they resolve against the initial
    // containing block — so on a phone the "Akcje" label landed at x≈914 in DOCUMENT coordinates,
    // outside the scroll container's reach, and dragged the whole page 500px sideways. The
    // container's own `overflow-x-auto` cannot clip what was never positioned inside it.
    <div className="relative bg-surface-900 rounded-xl border border-surface-800 overflow-x-auto">
      <table className="w-full min-w-[940px] text-sm">
        <thead className="text-surface-400 border-b border-surface-800">
          <tr>
            {header('date', t('table.date'), 'w-28')}
            {header('route', t('table.route'))}
            {header('grade', t('table.grade'), 'w-20')}
            {/* The scale alone does not answer this: sport and trad share the French grades,
                so 7a and 7a are indistinguishable without saying which game it was */}
            <th className="px-3 py-2 text-left font-medium w-24">
              {isMountain ? t('table.season') : t('table.discipline')}
            </th>
            <th className="px-3 py-2 text-left font-medium w-20">{t('table.style')}</th>
            {header('area', t('table.area'))}
            <th className="px-3 py-2 text-left font-medium">
              {isMountain ? t('table.summit') : t('table.crag')}
            </th>
            {isMountain ? (
              <>
                <th className="px-3 py-2 text-left font-medium w-20">{t('table.lengthMeters')}</th>
                <th className="px-3 py-2 text-left font-medium w-16">{t('table.pitches')}</th>
                <th className="px-3 py-2 text-left font-medium w-16">{t('table.duration')}</th>
                <th className="px-3 py-2 text-left font-medium w-24">{t('table.led')}</th>
              </>
            ) : (
              <>
                <th className="px-3 py-2 text-left font-medium w-16">{t('table.attempts')}</th>
                {header('stars', t('table.stars'), 'w-28')}
              </>
            )}
            {moderatable && <th className="px-3 py-2 w-12"><span className="sr-only">{t('takedown.column')}</span></th>}
            {editable && <th className="px-3 py-2 w-20"><span className="sr-only">{t('table.actions')}</span></th>}
          </tr>
        </thead>
        <tbody>
          {entries.map(entry => {
            const isOpen = expanded.has(entry.id)
            return (
            <Fragment key={entry.id}>
            <tr className={clsx(
              'border-b border-surface-800/60 hover:bg-surface-800/40',
              // The comment row carries the border when it is showing, so the pair reads as one entry
              isOpen ? 'border-b-0' : 'last:border-0',
            )}>
              <td className="px-3 py-2 text-surface-400 tabular-nums whitespace-nowrap">
                {/* A 'yyyy-MM-dd' from the API is a label, not an instant — parseCalendarDate
                    keeps it on the right day west of Greenwich */}
                {format(parseCalendarDate(entry.climbedOn), 'dd.MM.yyyy')}
              </td>
              <td className="px-3 py-2 text-surface-100">
                <span className="font-medium">{entry.routeName}</span>
                {/* Shown to the author too, not just to the admin: their banner says their ascents
                    are public, so one of them missing from the list needs a reason on screen */}
                {entry.hiddenFromPublicAt && (
                  <span
                    className="ml-2 inline-flex items-center gap-1 align-middle px-1.5 py-0.5 rounded text-[11px] bg-amber-500/10 text-amber-300"
                    title={t('takedown.badgeHint')}
                  >
                    <EyeOff className="w-3 h-3" aria-hidden="true" />
                    {t('takedown.badge')}
                  </span>
                )}
                {/* The teaser is the control: a 2000-character field whose only render was one
                    clipped line meant the text could be written and never read back. Plain text
                    here on purpose — markers would be noise in a single clipped line, and the
                    formatted version is one click away. */}
                {entry.comment && (
                  <button
                    type="button"
                    onClick={() => toggle(entry.id)}
                    aria-expanded={isOpen}
                    aria-controls={`ascent-comment-${entry.id}`}
                    className="group/comment mt-0.5 flex w-full items-start gap-1 text-left text-xs text-surface-500 hover:text-surface-300 transition-colors"
                  >
                    <ChevronDown
                      className={clsx('w-3 h-3 mt-0.5 shrink-0 transition-transform', isOpen && 'rotate-180')}
                      aria-hidden="true"
                    />
                    <span className={clsx('min-w-0', !isOpen && 'line-clamp-1')}>
                      {isOpen ? t('table.commentHide') : toPlainText(entry.comment)}
                    </span>
                  </button>
                )}
              </td>
              <td className="px-3 py-2">
                <span className="inline-flex px-2 py-0.5 rounded bg-surface-800 text-surface-100 font-semibold tabular-nums">
                  {entry.gradeLabel}
                </span>
                {/* The guidebook's own grade next to the unified one — a quotation, not a key */}
                {entry.originalGrade && (
                  <span className="ml-1.5 text-xs text-surface-500">{entry.originalGrade}</span>
                )}
              </td>
              <td className="px-3 py-2 text-surface-400 whitespace-nowrap">
                {isMountain
                  ? t(entry.winter ? 'season.winter' : 'season.summer')
                  : entry.discipline && t(`discipline.${entry.discipline}`)}
              </td>
              <td className="px-3 py-2 text-surface-300 font-medium whitespace-nowrap">{t(`style.${entry.style}`)}</td>
              <td className="px-3 py-2 text-surface-400">{entry.area}</td>
              <td className="px-3 py-2 text-surface-400">{entry.crag}</td>
              {isMountain ? (
                <>
                  <td className="px-3 py-2 text-surface-400 tabular-nums">
                    {entry.lengthMeters != null ? `${entry.lengthMeters} m` : '—'}
                  </td>
                  <td className="px-3 py-2 text-surface-400 tabular-nums">{entry.pitches ?? '—'}</td>
                  <td className="px-3 py-2 text-surface-400 tabular-nums whitespace-nowrap">
                    {entry.durationMinutes != null
                      ? `${Math.round(entry.durationMinutes / 6) / 10} h`
                      : '—'}
                  </td>
                  <td className="px-3 py-2 text-surface-400 tabular-nums whitespace-nowrap">
                    {entry.ledGradeLabel
                      ? `${entry.ledGradeLabel}${entry.ledPitches != null ? ` · ${entry.ledPitches}` : ''}`
                      : '—'}
                  </td>
                </>
              ) : (
                <>
                  <td className="px-3 py-2 text-surface-400 tabular-nums">{entry.attempts ?? '—'}</td>
                  <td className="px-3 py-2">
                    <StarRating value={entry.qualityStars} size="sm" />
                  </td>
                </>
              )}
              {moderatable && (
                <td className="px-3 py-2">
                  <button
                    type="button"
                    disabled={pendingVisibilityId === entry.id}
                    onClick={() => onSetPublicVisibility?.(entry, !entry.hiddenFromPublicAt)}
                    className="p-1.5 rounded text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition disabled:opacity-40"
                    aria-label={`${t(entry.hiddenFromPublicAt ? 'takedown.restore' : 'takedown.hide')}: ${entry.routeName}`}
                    title={t(entry.hiddenFromPublicAt ? 'takedown.restore' : 'takedown.hide')}
                  >
                    {entry.hiddenFromPublicAt
                      ? <Globe className="w-4 h-4" aria-hidden="true" />
                      : <EyeOff className="w-4 h-4" aria-hidden="true" />}
                  </button>
                </td>
              )}
              {editable && (
                <td className="px-3 py-2">
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => onEdit?.(entry)}
                      className="p-1.5 rounded text-surface-400 hover:text-surface-100 hover:bg-surface-800 transition"
                      aria-label={`${t('edit')}: ${entry.routeName}`}
                    >
                      <Pencil className="w-4 h-4" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      onClick={() => onDelete?.(entry)}
                      className="p-1.5 rounded text-surface-400 hover:text-rose-400 hover:bg-surface-800 transition"
                      aria-label={`${t('delete')}: ${entry.routeName}`}
                    >
                      <Trash2 className="w-4 h-4" aria-hidden="true" />
                    </button>
                  </div>
                </td>
              )}
            </tr>
            {isOpen && entry.comment && (
              <tr id={`ascent-comment-${entry.id}`} className="border-b border-surface-800/60 last:border-0">
                <td colSpan={columnCount} className="px-3 pb-3 pt-0">
                  {/* max-w so a long note keeps a readable measure on a 940px-wide table, and it
                      starts at the left edge — where the route column the reader just clicked is */}
                  <div
                    className="max-w-3xl rounded-lg bg-surface-800/50 px-3 py-2 text-sm text-surface-300 whitespace-pre-wrap"
                    dangerouslySetInnerHTML={{ __html: renderRichText(entry.comment) }}
                  />
                </td>
              </tr>
            )}
            </Fragment>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
