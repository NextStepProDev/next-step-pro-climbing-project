import { describe, expect, it } from 'vitest'
import { scrollLeftForDayColumn, WEEK_GUTTER_PX } from './weekScroll'

function columnAt(offsetLeft: number): HTMLElement {
  const el = document.createElement('div')
  Object.defineProperty(el, 'offsetLeft', { value: offsetLeft })
  return el
}

describe('scrollLeftForDayColumn', () => {
  it('should put the column right next to the sticky gutter', () => {
    // Sunday in a 900px grid: 60px gutter + 6 columns of 120px
    expect(scrollLeftForDayColumn(columnAt(WEEK_GUTTER_PX + 6 * 120))).toBe(6 * 120)
  })

  it('should not scroll for the first column', () => {
    expect(scrollLeftForDayColumn(columnAt(WEEK_GUTTER_PX))).toBe(0)
  })

  it('should fall back to the start when the column is not rendered', () => {
    expect(scrollLeftForDayColumn(null)).toBe(0)
    expect(scrollLeftForDayColumn(undefined)).toBe(0)
  })
})
