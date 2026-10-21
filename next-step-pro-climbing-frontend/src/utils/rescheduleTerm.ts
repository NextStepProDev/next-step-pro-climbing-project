import { format, type Locale } from 'date-fns'
import { parseCalendarDate } from './calendarDate'

/**
 * One term as a person reads it: "wtorek, 21 października, 18:00–20:00", a date range for a
 * multi-day event, or a bare day for an all-day one. Dates are labels ('yyyy-MM-dd' from the API),
 * so they go through parseCalendarDate, never `new Date` — see CLAUDE.md, "Czas".
 */
export function describeTerm(
  locale: Locale,
  date: string,
  endDate: string | null,
  startTime: string | null,
  endTime: string | null,
): string {
  const first = format(parseCalendarDate(date), 'EEEE, d MMMM yyyy', { locale })
  const days = endDate && endDate !== date
    ? `${format(parseCalendarDate(date), 'd MMM', { locale })} – ${format(parseCalendarDate(endDate), 'd MMM yyyy', { locale })}`
    : first
  const hours = startTime && endTime ? `, ${startTime.slice(0, 5)}–${endTime.slice(0, 5)}` : ''
  const text = days + hours
  // First letter only: CSS `capitalize` uppercases EVERY word, and "10 Listopada" is wrong in Polish.
  return text.charAt(0).toLocaleUpperCase(locale.code) + text.slice(1)
}
