import { useMemo, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, Ban, Building2, CalendarCheck, ExternalLink, Phone } from 'lucide-react'
import clsx from 'clsx'
import { calendarApi } from '../../api/client'
import { buildDayAgenda, type AgendaEntry, type ProposedRange } from '../../utils/dayAgenda'

/*
 * The day a proposal falls on, shown where it is answered.
 *
 * Two shapes over one query: the full list inside the create form, and a single line on the
 * request card, so most proposals can be decided without opening anything. Both read the key the
 * calendar page uses (`['calendar','day',date]`), so they share its cache and every invalidation
 * that already refreshes it — creating the slot included.
 */

// A native date field can be emptied, and is half-typed mid-edit: never query or format that.
const isDateLabel = (date: string) => /^\d{4}-\d{2}-\d{2}$/.test(date)

function useDayAgenda(date: string, proposed: ProposedRange | null) {
  const usable = isDateLabel(date)
  const query = useQuery({
    queryKey: ['calendar', 'day', date],
    queryFn: () => calendarApi.getDayView(date),
    enabled: usable,
    // The whole point is "what is there NOW" — the calendar page reads this key the same way.
    staleTime: 0,
  })
  const start = proposed?.start
  const end = proposed?.end
  const entries = useMemo(
    () => query.data ? buildDayAgenda(query.data, start && end ? { start, end } : null) : null,
    [query.data, start, end],
  )
  return { usable, query, entries }
}

function useEntryLabel() {
  const { t } = useTranslation('calendar')
  return (entry: AgendaEntry) => {
    if (entry.kind === 'unavailable') return entry.title ?? t('dayAgenda.unavailable')
    if (entry.kind === 'closed') return entry.title ?? t('dayAgenda.closed')
    if (entry.kind === 'window') return t('dayAgenda.window')
    return entry.title ?? t('dayAgenda.untitledSlot')
  }
}

function hours(entry: AgendaEntry, allDayLabel: string) {
  return entry.allDay ? allDayLabel : `${entry.start}–${entry.end}`
}

/** The full list — inside the form that answers a proposal. The proposed hours ride along as a row. */
export function DayAgendaPreview({ date, proposed }: {
  date: string
  /** The hours in the form right now, or `null` for a whole-day entry. */
  proposed: ProposedRange | null
}) {
  const { t } = useTranslation('calendar')
  const label = useEntryLabel()
  const { usable, query, entries } = useDayAgenda(date, proposed)

  if (!usable) return null

  const proposedRow = (
    <li key="proposed" className="flex items-center gap-3 rounded-md border border-dashed border-primary-500/60 bg-primary-500/10 px-2.5 py-1.5">
      <span className="w-24 shrink-0 tabular-nums text-primary-300">
        {proposed ? `${proposed.start}–${proposed.end}` : t('dayAgenda.allDay')}
      </span>
      <span className="font-medium text-primary-300">{t('dayAgenda.proposed')}</span>
    </li>
  )

  // Slot the proposal in chronologically, so the collision reads as neighbouring rows.
  const rows: ReactNode[] = []
  let placed = false
  for (const entry of entries ?? []) {
    if (!placed && !entry.allDay && proposed && entry.start >= proposed.start) {
      rows.push(proposedRow)
      placed = true
    }
    rows.push(
      <li
        key={entry.id}
        className={clsx(
          'flex items-center gap-3 rounded-md px-2.5 py-1.5',
          entry.overlaps ? 'bg-rose-500/10 border border-rose-500/30' : 'bg-surface-800/60 border border-transparent',
        )}
      >
        <span className="w-24 shrink-0 tabular-nums text-surface-300">{hours(entry, t('dayAgenda.allDay'))}</span>
        <span className={clsx(
          'flex min-w-0 flex-1 items-center gap-1.5',
          entry.kind === 'unavailable' ? 'text-slate-300'
            : entry.kind === 'closed' ? 'text-indigo-300'
            : entry.kind === 'window' ? 'text-teal-300'
            : 'text-surface-100',
        )}>
          {entry.kind === 'unavailable' && <Ban className="h-3.5 w-3.5 shrink-0" />}
          {entry.kind === 'closed' && <Building2 className="h-3.5 w-3.5 shrink-0" />}
          {entry.kind === 'window' && <Phone className="h-3.5 w-3.5 shrink-0" />}
          <span className="truncate">{label(entry)}</span>
        </span>
        {entry.occupancy && (
          <span className="shrink-0 tabular-nums text-xs text-surface-400">
            {entry.occupancy.taken}/{entry.occupancy.max}
          </span>
        )}
        {entry.overlaps && (
          <span className="flex shrink-0 items-center gap-1 text-xs font-medium text-rose-300">
            <AlertTriangle className="h-3.5 w-3.5" />
            {t('dayAgenda.conflict')}
          </span>
        )}
      </li>,
    )
  }
  if (!placed) rows.push(proposedRow)

  return (
    <section className="rounded-lg border border-surface-700 bg-surface-900/60 p-3">
      <div className="mb-2 flex items-center justify-between gap-2">
        <h3 className="text-sm font-medium text-surface-200">{t('dayAgenda.title')}</h3>
        {/* A new tab on purpose: this sits inside a half-filled form, and leaving it loses the form. */}
        <a
          href={`/calendar?date=${date}`}
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-1 text-xs font-medium text-primary-400 hover:text-primary-300"
        >
          {t('dayAgenda.openDay')}
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      </div>

      {query.isError ? (
        // Never silence: an empty list here would read as "the day is free".
        <p className="text-sm text-rose-400/80">
          {t('dayAgenda.error')}{' '}
          <button type="button" onClick={() => void query.refetch()} className="underline hover:text-rose-300">
            {t('dayAgenda.retry')}
          </button>
        </p>
      ) : !entries ? (
        <p className="text-sm text-surface-400">{t('dayAgenda.loading')}</p>
      ) : (
        <>
          <ul className="space-y-1 text-sm">{rows}</ul>
          {entries.length === 0 && (
            <p className="mt-2 text-xs text-surface-400">{t('dayAgenda.empty')}</p>
          )}
        </>
      )}
    </section>
  )
}

/** One line for the request card: the verdict, without opening anything. */
export function DayAgendaSummary({ date, proposed }: { date: string; proposed: ProposedRange }) {
  const { t } = useTranslation('calendar')
  const label = useEntryLabel()
  const { usable, query, entries } = useDayAgenda(date, proposed)

  // Loading and failure say nothing here: the form shows both properly, and a card line that
  // flickers "checking…" on every request is noise.
  if (!usable || !entries || query.isError) return null

  const conflicts = entries.filter((e) => e.overlaps)
  if (conflicts.length > 0) {
    const first = conflicts[0]
    const what = `${hours(first, t('dayAgenda.allDay'))} ${label(first)}${first.occupancy ? ` (${first.occupancy.taken}/${first.occupancy.max})` : ''}`
    return (
      <p className="mt-2 flex items-center gap-1.5 text-sm font-medium text-rose-300">
        <AlertTriangle className="h-4 w-4 shrink-0" />
        {conflicts.length > 1
          ? t('dayAgenda.summaryConflictMore', { what, count: conflicts.length - 1 })
          : t('dayAgenda.summaryConflict', { what })}
      </p>
    )
  }

  return (
    <p className="mt-2 flex items-center gap-1.5 text-sm text-green-400">
      <CalendarCheck className="h-4 w-4 shrink-0" />
      {entries.length === 0
        ? t('dayAgenda.summaryEmpty')
        : t('dayAgenda.summaryFreeOthers', { count: entries.length })}
    </p>
  )
}
