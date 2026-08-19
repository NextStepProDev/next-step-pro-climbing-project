/**
 * The three fills every chart on the user statistics screen draws from.
 *
 * <p>Semantic, not categorical: one hue for "done", one for "pending", one neutral for "absent".
 * Every chart here answers the same shape of question (how much of the base has got this far), so
 * reusing one meaning across all of them is what makes the screen readable as a whole — the same
 * stance as the RPE bar in the training statistics, which this matches on purpose.
 *
 * <p><b>Steps chosen by running the palette validator, in both themes.</b> The obvious pairing —
 * `green-400` with `amber-400` — is fine in the dark theme and broken in the light one, where the
 * light overrides turn amber-400 into a dark brown that lands next to the dark olive green: ΔE 10.6
 * for normal colour vision, i.e. hard to tell apart before colour blindness is even considered.
 * `amber-500` clears it in both (ΔE 16.8 dark / 21.6 light, and ≥ 8 under simulated protanopia and
 * deuteranopia). Do not "tidy" these to a matching step number.
 *
 * <p>Amber sits below the 3:1 contrast line against the light surface, so every segment ships with
 * its number in the legend rather than relying on the swatch alone. That is required, not decorative.
 */
export const STATS_FILL = {
  /** Reached this step: verified, active, subscribed, attended. */
  done: 'bg-green-400',
  /** Still open, and actionable: unverified, dormant, never asked. */
  pending: 'bg-amber-500',
  /** Nothing happened. Neutral on purpose — never booking is not a fault. */
  absent: 'bg-surface-600',
} as const

/** Legend swatch for a fill, so the swatch and the bar can never drift apart. */
export function swatchClass(fill: string): string {
  return `inline-block w-2.5 h-2.5 rounded-sm shrink-0 ${fill}`
}
