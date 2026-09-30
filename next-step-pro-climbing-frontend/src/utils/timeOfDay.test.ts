import { describe, expect, it } from 'vitest'
import { addMinutes, minutesToTime, timeToMinutes } from './timeOfDay'

describe('timeOfDay', () => {
  it('reads "HH:mm" and ignores seconds the API sometimes sends', () => {
    expect(timeToMinutes('00:00')).toBe(0)
    expect(timeToMinutes('07:30')).toBe(450)
    expect(timeToMinutes('23:00')).toBe(1380)
    expect(timeToMinutes('18:05:00')).toBe(1085)
  })

  it('writes zero-padded "HH:mm"', () => {
    expect(minutesToTime(0)).toBe('00:00')
    expect(minutesToTime(545)).toBe('09:05')
  })

  it('adds minutes but stops at the latest time allowed', () => {
    expect(addMinutes('10:00', 90, 23 * 60 + 55)).toBe('11:30')
    expect(addMinutes('23:30', 60, 23 * 60 + 55)).toBe('23:55')
    expect(addMinutes('23:30', 60, 23 * 60 + 59)).toBe('23:59')
  })
})
