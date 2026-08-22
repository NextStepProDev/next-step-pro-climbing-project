import { format } from 'date-fns'
import { ArrowDown, ArrowUp, ArrowUpDown, EyeOff, Globe, Pencil, Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { StarRating } from './StarRating'
import { parseCalendarDate } from '../../utils/calendarDate'
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
    <div className="bg-surface-900 rounded-xl border border-surface-800 overflow-x-auto">
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
          {entries.map(entry => (
            <tr key={entry.id} className="border-b border-surface-800/60 last:border-0 hover:bg-surface-800/40">
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
                {entry.comment && (
                  <span className="block text-xs text-surface-500 line-clamp-1">{entry.comment}</span>
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
          ))}
        </tbody>
      </table>
    </div>
  )
}
