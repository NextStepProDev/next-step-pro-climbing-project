import { describe, it, expect } from 'vitest'
import { HEATMAP_LEGEND, levelClass } from './heatmapScale'

// Regression for #84: the heatmap used a 5-step GitHub-style intensity ramp. Athletes
// almost never log more than one activity a day, so the extra shades carried no signal
// and read as random. The scale must stay three states: 0 / 1 / 2+.
describe('heatmap level scale', () => {
  it('should render an empty day as the neutral surface colour', () => {
    expect(levelClass(0)).toBe('bg-surface-800')
  })

  it('should render a single activity as the base green', () => {
    expect(levelClass(1)).toBe('bg-green-500')
  })

  it('should render two activities as the "2+" green', () => {
    expect(levelClass(2)).toBe('bg-green-300')
  })

  it('should not ramp beyond "2+" however busy the day was', () => {
    expect(levelClass(5)).toBe(levelClass(2))
    expect(levelClass(12)).toBe(levelClass(2))
    expect(levelClass(100)).toBe(levelClass(2))
  })

  it('should use exactly three distinct colours', () => {
    const distinct = new Set([0, 1, 2, 3, 4, 5, 9, 40].map(levelClass))
    expect(distinct.size).toBe(3)
  })

  it('should treat a negative count as empty rather than crash or colour it', () => {
    expect(levelClass(-1)).toBe('bg-surface-800')
  })

  it('should expose a legend matching the three states', () => {
    expect(HEATMAP_LEGEND.map(([, label]) => label)).toEqual(['0', '1', '2+'])
    expect(HEATMAP_LEGEND.map(([count]) => levelClass(count)))
      .toEqual(['bg-surface-800', 'bg-green-500', 'bg-green-300'])
  })
})
