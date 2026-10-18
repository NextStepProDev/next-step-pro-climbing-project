/**
 * How far along a weight goal is, measured start → target against the current trend.
 * Null whenever any of the three numbers is missing — a half-known bar would just mislead.
 *
 * ⚠️ Progress is measured IN THE DIRECTION OF THE GOAL. The first version took the distance from
 * the start with `Math.abs`, so moving away from the target filled the bar too: start 70, target 65,
 * trend 72 read as 40% done. Gaining when the goal is to lose is zero progress, not some.
 * `remaining` follows the same rule — past the target there is nothing left, not the overshoot.
 */
export function weightProgress(
  targetWeightKg: number | null,
  startWeightKg: number | null,
  currentTrendKg: number | null,
): { percent: number; remaining: number } | null {
  if (targetWeightKg == null || startWeightKg == null || currentTrendKg == null) return null
  const total = targetWeightKg - startWeightKg
  if (total === 0) return null
  const direction = Math.sign(total)
  const done = (currentTrendKg - startWeightKg) * direction
  return {
    percent: Math.min(100, Math.max(0, Math.round((done / Math.abs(total)) * 100))),
    remaining: Math.max(0, (targetWeightKg - currentTrendKg) * direction),
  }
}
