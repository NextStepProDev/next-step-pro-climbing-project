import { format, type Locale } from 'date-fns'
import { parseCalendarDate } from './calendarDate'

/**
 * A term as one line — "śr. 14.10, 18:00–20:00" — for telling a client which hours were agreed on
 * and which they had proposed. Dates are `yyyy-MM-dd` labels, so they go through
 * `parseCalendarDate` and never `new Date` (that would shift the day west of Greenwich).
 */
export function formatTerm(
  term: { date: string; endDate?: string | null; startTime: string | null; endTime: string | null },
  locale: Locale,
  allDayLabel: string,
): string {
  const day = (d: string) => format(parseCalendarDate(d), 'EEE d.MM', { locale })
  const days = term.endDate && term.endDate !== term.date ? `${day(term.date)} – ${day(term.endDate)}` : day(term.date)
  const hours = term.startTime && term.endTime
    ? `${term.startTime.slice(0, 5)}–${term.endTime.slice(0, 5)}`
    : allDayLabel
  return `${days}, ${hours}`
}
