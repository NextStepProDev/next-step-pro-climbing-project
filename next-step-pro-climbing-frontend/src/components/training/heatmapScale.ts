/**
 * Activity heatmap colour scale — three states, not a GitHub-style 5-step ramp (#84).
 * Athletes almost never log more than one activity per day, so extra shades carried no
 * information and read as random noise: gray = nothing, green = one, lighter green = 2+.
 */
export function levelClass(count: number): string {
  if (count <= 0) return 'bg-surface-800'
  if (count === 1) return 'bg-green-500'
  return 'bg-green-300'
}

/** Legend swatches, in render order: 0 / 1 / 2+ */
export const HEATMAP_LEGEND = [[0, '0'], [1, '1'], [2, '2+']] as const
