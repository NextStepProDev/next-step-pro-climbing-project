import { describe, it, expect, vi, afterEach } from 'vitest'
import { format } from 'date-fns'
import {
  isTodayInWarsaw,
  nowInWarsaw,
  parseCalendarDate,
  parseCalendarDateTime,
  todayInWarsaw,
  toWarsawWallClock,
} from './calendarDate'

/**
 * These run under whatever TZ the runner has, and that is the point: every assertion below
 * has to hold in Warsaw and in New York alike. `npm run test:tz` runs the whole suite a
 * second time as America/New_York, which is the only place the timezone bugs ever showed.
 */
describe('calendarDate', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  describe('parseCalendarDate', () => {
    it('shouldKeepTheCalendarDayWhenParsingADateOnlyLabel', () => {
      // `new Date('2026-07-13')` is UTC midnight — the 12th anywhere west of Greenwich
      expect(format(parseCalendarDate('2026-07-13'), 'yyyy-MM-dd')).toBe('2026-07-13')
      expect(parseCalendarDate('2026-07-13').getDate()).toBe(13)
    })

    it('shouldRoundTripEveryDayOfAWeekThroughFormat', () => {
      for (const day of ['2026-07-11', '2026-07-12', '2026-07-13', '2026-01-01', '2026-12-31']) {
        expect(format(parseCalendarDate(day), 'yyyy-MM-dd')).toBe(day)
      }
    })
  })

  describe('parseCalendarDateTime', () => {
    it('shouldReadTheStoredWallClockTimeAsWritten', () => {
      const start = parseCalendarDateTime('2026-08-13', '17:00:00')
      expect(format(start, 'yyyy-MM-dd HH:mm')).toBe('2026-08-13 17:00')
    })
  })

  describe('nowInWarsaw', () => {
    it('shouldReadTheWarsawWallClockWhateverTheDeviceClockSays', () => {
      // 2026-08-13 23:30 UTC = 2026-08-14 01:30 in Warsaw, 19:30 on the 13th in New York.
      vi.setSystemTime(new Date('2026-08-13T23:30:00Z'))
      expect(format(nowInWarsaw(), 'yyyy-MM-dd HH:mm')).toBe('2026-08-14 01:30')
      expect(todayInWarsaw()).toBe('2026-08-14')
    })

    it('shouldFollowPolishSummerTime', () => {
      // Winter: UTC+1
      vi.setSystemTime(new Date('2026-01-15T12:00:00Z'))
      expect(format(nowInWarsaw(), 'HH:mm')).toBe('13:00')
      // Summer: UTC+2
      vi.setSystemTime(new Date('2026-07-15T12:00:00Z'))
      expect(format(nowInWarsaw(), 'HH:mm')).toBe('14:00')
    })

    it('shouldCompareDirectlyAgainstAStoredTrainingStart', () => {
      // The "has this training started" question, asked the way the backend asks it.
      // 15:00 UTC = 17:00 Warsaw = 11:00 in New York.
      vi.setSystemTime(new Date('2026-08-13T15:00:00Z'))
      expect(parseCalendarDateTime('2026-08-13', '17:00') <= nowInWarsaw()).toBe(true)
      expect(parseCalendarDateTime('2026-08-13', '18:00') <= nowInWarsaw()).toBe(false)
    })
  })

  describe('isTodayInWarsaw', () => {
    it('shouldMarkTheWarsawDayEvenWhileTheDeviceIsStillOnTheDayBefore', () => {
      vi.setSystemTime(new Date('2026-08-13T23:30:00Z'))
      expect(isTodayInWarsaw('2026-08-14')).toBe(true)
      expect(isTodayInWarsaw('2026-08-13')).toBe(false)
      expect(isTodayInWarsaw(parseCalendarDate('2026-08-14'))).toBe(true)
    })
  })

  describe('toWarsawWallClock', () => {
    it('shouldRereadAnInstantAsPolishWallClock', () => {
      const midnightUtc = new Date('2026-08-13T22:15:00Z')
      expect(format(toWarsawWallClock(midnightUtc), 'yyyy-MM-dd HH:mm')).toBe('2026-08-14 00:15')
    })
  })
})
