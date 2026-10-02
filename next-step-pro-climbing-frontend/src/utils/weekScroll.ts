/**
 * Width of the hour-label gutter in both week views. The gutter is sticky, so on a phone
 * (where the 900px grid scrolls sideways) it sits ON TOP of whatever column is scrolled
 * under it — every scroll target has to land past it, not at the viewport's left edge.
 * The grids' `gridTemplateColumns` are built from this constant, so the scroll offset
 * cannot drift from the real gutter width.
 */
export const WEEK_GUTTER_PX = 60

/**
 * Horizontal scroll that puts a day column right next to the sticky gutter.
 *
 * Measured from the rendered column, never from an assumed column width: the old
 * `index * 130 - 20` guessed 130px for a column that is really (900 - 60) / 7 = 120px,
 * so the error grew with the weekday and on a Sunday today's column sat 40px under the
 * hour labels. `offsetLeft` is relative to the time grid (`relative`), which starts at
 * the scroll content's left edge.
 */
export function scrollLeftForDayColumn(column: HTMLElement | null | undefined): number {
  if (!column) return 0
  return Math.max(0, column.offsetLeft - WEEK_GUTTER_PX)
}
