import { addDays, format, startOfMonth, startOfWeek } from 'date-fns'

/**
 * Date maths for the month view. Lives next to the components rather than in utils/,
 * matching weekLayout.ts.
 *
 * Dates are 'yyyy-MM-dd' strings — the shape the API already speaks — produced by
 * format() on local Date objects. Never toISOString(): it converts to UTC first, which
 * for Europe/Warsaw hands back the previous day for every date before 02:00.
 */

/**
 * A month page is always exactly six rows of seven, never five. Deriving the length from
 * where the month happens to fall gives 35 days some months and 42 others, so the grid
 * changes height as you page and the content below it jumps — which reads as the page
 * reloading rather than as navigation.
 */
export const MONTH_GRID_DAYS = 42

/** Monday on or before the 1st. Weeks start on Monday here, like the rest of the app. */
function monthGridStart(anchor: Date): Date {
  return startOfWeek(startOfMonth(anchor), { weekStartsOn: 1 })
}

export function monthGridDays(anchor: Date): Date[] {
  const start = monthGridStart(anchor)
  return Array.from({ length: MONTH_GRID_DAYS }, (_, i) => addDays(start, i))
}

/** The range to request for a month page: the whole grid, including the greyed edge days. */
export function monthGridRange(anchor: Date): { from: string; to: string } {
  const start = monthGridStart(anchor)
  return {
    from: format(start, 'yyyy-MM-dd'),
    to: format(addDays(start, MONTH_GRID_DAYS - 1), 'yyyy-MM-dd'),
  }
}

/** The range to request for a week page: Monday..Sunday of the week the anchor falls in. */
export function weekRange(anchor: Date): { from: string; to: string; weekStart: string } {
  const start = startOfWeek(anchor, { weekStartsOn: 1 })
  const from = format(start, 'yyyy-MM-dd')
  return { from, to: format(addDays(start, 6), 'yyyy-MM-dd'), weekStart: from }
}

/**
 * The anchor one week either side. It lives here, next to the range it has to stay in step
 * with, because the pair has to satisfy one property that is not obvious from either half:
 * stepping must actually MOVE the anchor. The anchor round-trips through the URL as a
 * 'yyyy-MM-dd', and when that string was read back with `new Date()` (UTC midnight, so a day
 * early west of Greenwich) `startOfWeek` snapped to the previous Monday and +7 landed back
 * on the string already in the URL — a paging button that writes what is already there
 * re-renders nothing, so "next week" did nothing at all. Covered in monthGrid.test.ts.
 */
export function stepWeek(anchor: Date, weeks: number): Date {
  return addDays(startOfWeek(anchor, { weekStartsOn: 1 }), weeks * 7)
}

export type CalendarView = 'week' | 'month'

/**
 * The `?cal=` param is law. Once it is in the URL nothing may override it — not a window
 * resize, not a phone rotating into landscape. Flipping the view someone deliberately
 * chose is a worse surprise than showing them a dense month.
 *
 * The viewport only picks a value for a first visit that carries no param yet, because a
 * month grid cannot be read at phone width and a single week wastes a desktop. Callers
 * must therefore pass a `compact` value frozen at first render, not a live one.
 */
export function resolveInitialView(param: string | null, compact: boolean): CalendarView {
  if (param === 'month') return 'month'
  if (param === 'week') return 'week'
  return compact ? 'week' : 'month'
}
