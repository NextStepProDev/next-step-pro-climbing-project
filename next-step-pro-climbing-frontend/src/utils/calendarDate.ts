import { format, parseISO } from 'date-fns'

/**
 * One clock for the whole calendar: Europe/Warsaw.
 *
 * The backend does not store instants for anything on a calendar — it stores a LocalDate and
 * a LocalTime, both meaning Polish wall clock, and every rule it enforces (the 12 h booking
 * cutoff, "has this training started", MISSED derivation, weight backfill) is evaluated with
 * `LocalDateTime.now(Europe/Warsaw)`. A browser that answers those same questions with the
 * device clock is answering a different question, and the two only agree while the device
 * happens to sit in Poland. For an athlete travelling to the US they disagree by 8 hours:
 * the "complete training" button appears hours after the server would accept the click, and
 * a week that pages by one week writes back the date already in the URL, so the arrow dies.
 *
 * Two rules cover every case:
 *
 * 1. A 'yyyy-MM-dd' from the API is a LABEL, not a moment. Never `new Date(s)` — that parses
 *    date-only strings as UTC midnight, which is the previous day for anyone west of
 *    Greenwich. `parseCalendarDate` keeps the label intact in every timezone on earth.
 * 2. "Now" for calendar arithmetic is Warsaw's now, not the device's. `nowInWarsaw()` returns
 *    a Date whose LOCAL fields carry the Warsaw wall clock, so it compares directly against
 *    anything built by `parseCalendarDate` / `parseCalendarDateTime` — the same frame the
 *    server uses.
 *
 * ⚠️ The values from rule 2 are wall-clock readings, not instants. Never compare `nowInWarsaw()`
 * with `new Date(someInstant)` (createdAt, completedAt, expiresAt, confirmationDeadline — those
 * carry a zone and are correct in device time); put the instant through `toWarsawWallClock`
 * first if it has to be measured in Polish calendar days.
 */

export const APP_TIME_ZONE = 'Europe/Warsaw'

// Built once: this runs inside render paths that paint 42 day tiles.
const warsawParts = new Intl.DateTimeFormat('en-CA', {
  timeZone: APP_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

/** A 'yyyy-MM-dd' label as a Date at LOCAL midnight — the same calendar day everywhere. */
export function parseCalendarDate(date: string): Date {
  return parseISO(date)
}

/**
 * A 'yyyy-MM-dd' + 'HH:mm[:ss]' pair as one local Date. Both halves are Polish wall clock,
 * so the result belongs to the same frame as `nowInWarsaw()` and may be compared with it.
 */
export function parseCalendarDateTime(date: string, time: string): Date {
  return parseISO(`${date}T${time.slice(0, 5)}`)
}

/** Any instant re-read as Warsaw wall clock, for comparing against calendar labels. */
export function toWarsawWallClock(instant: Date): Date {
  const parts = warsawParts.formatToParts(instant)
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((p) => p.type === type)?.value ?? 0)
  return new Date(
    get('year'), get('month') - 1, get('day'),
    get('hour'), get('minute'), get('second'),
  )
}

/** Now, as Warsaw reads it. The frontend's answer to "has it started yet" must match the server's. */
export function nowInWarsaw(): Date {
  return toWarsawWallClock(new Date())
}

/** Today's 'yyyy-MM-dd' in Warsaw — what the server means by "today". */
export function todayInWarsaw(): string {
  return format(nowInWarsaw(), 'yyyy-MM-dd')
}

/** Is this calendar day today in Warsaw? Replaces date-fns `isToday`, which asks the device. */
export function isTodayInWarsaw(date: string | Date): boolean {
  const day = typeof date === 'string' ? date : format(date, 'yyyy-MM-dd')
  return day === todayInWarsaw()
}
