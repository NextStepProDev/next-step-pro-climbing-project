import { describe, it, expect } from 'vitest'
import { clickToTime, layoutDay, splitDay, timeToMin, HOUR_HEIGHT } from './weekLayout'
import { makeInvitation, makeReservation, makeTraining } from '../../test/factories'

describe('timeToMin', () => {
  it('should convert midnight to zero', () => {
    expect(timeToMin('00:00')).toBe(0)
  })

  it('should convert a half-hour time', () => {
    expect(timeToMin('07:30')).toBe(450)
  })

  it('should convert the grid end hour', () => {
    expect(timeToMin('23:00')).toBe(1380)
  })

  it('should ignore seconds sent by the API (HH:mm:ss)', () => {
    expect(timeToMin('10:15:00')).toBe(615)
  })
})

describe('clickToTime', () => {
  it('should map the top of the grid to the start hour', () => {
    expect(clickToTime(0)).toBe('07:00')
  })

  it('should map a full hour of pixels to the next hour', () => {
    expect(clickToTime(HOUR_HEIGHT)).toBe('08:00')
  })

  it('should snap a half-hour offset exactly', () => {
    expect(clickToTime(HOUR_HEIGHT / 2)).toBe('07:30')
  })

  it('should round up to the nearest 30 minutes', () => {
    // 10px = 15 min past 7:00 -> rounds up to 7:30
    expect(clickToTime(10)).toBe('07:30')
  })

  it('should round down to the nearest 30 minutes', () => {
    // 9px = 13.5 min past 7:00 -> rounds down to 7:00
    expect(clickToTime(9)).toBe('07:00')
  })

  it('should clamp a click above the grid to the start hour', () => {
    expect(clickToTime(-250)).toBe('07:00')
  })

  it('should clamp a click below the grid to the last usable slot', () => {
    // The grid ends at 23:00, so the latest startable half-hour is 22:30
    expect(clickToTime(100_000)).toBe('22:30')
  })

  it('should clamp a click exactly on the bottom border to 22:30', () => {
    expect(clickToTime(16 * HOUR_HEIGHT)).toBe('22:30')
  })

  it('should return the last full hour just above the bottom border', () => {
    expect(clickToTime(15 * HOUR_HEIGHT)).toBe('22:00')
  })
})

describe('layoutDay lanes', () => {
  it('should put two identical trainings side by side', () => {
    const items = layoutDay(
      [makeTraining({ startTime: '10:00', endTime: '11:00' }), makeTraining({ startTime: '10:00', endTime: '11:00' })],
      [], [],
    )
    expect(items).toHaveLength(2)
    expect(items.map((i) => i.lane)).toEqual([0, 1])
    expect(items.every((i) => i.lanes === 2)).toBe(true)
  })

  it('should split three overlapping trainings into three lanes', () => {
    const items = layoutDay(
      [
        makeTraining({ startTime: '10:00', endTime: '12:00' }),
        makeTraining({ startTime: '10:30', endTime: '11:30' }),
        makeTraining({ startTime: '11:00', endTime: '11:45' }),
      ],
      [], [],
    )
    expect(items.map((i) => i.lane)).toEqual([0, 1, 2])
    expect(items.every((i) => i.lanes === 3)).toBe(true)
  })

  it('should give a fully contained training its own lane', () => {
    const items = layoutDay(
      [
        makeTraining({ startTime: '10:00', endTime: '13:00', title: 'outer' }),
        makeTraining({ startTime: '11:00', endTime: '12:00', title: 'inner' }),
      ],
      [], [],
    )
    expect(items.map((i) => i.training?.title)).toEqual(['outer', 'inner'])
    expect(items.map((i) => i.lane)).toEqual([0, 1])
    expect(items[0].lanes).toBe(2)
  })

  it('should keep edge-to-edge trainings in a single lane', () => {
    // 10:00-11:00 and 11:00-12:00 touch but do not overlap
    const items = layoutDay(
      [makeTraining({ startTime: '10:00', endTime: '11:00' }), makeTraining({ startTime: '11:00', endTime: '12:00' })],
      [], [],
    )
    expect(items.map((i) => i.lane)).toEqual([0, 0])
    expect(items.every((i) => i.lanes === 1)).toBe(true)
  })

  it('should reuse a lane once its previous block has ended', () => {
    const items = layoutDay(
      [
        makeTraining({ startTime: '10:00', endTime: '11:00' }),
        makeTraining({ startTime: '10:30', endTime: '11:30' }),
        makeTraining({ startTime: '11:15', endTime: '12:00' }),
      ],
      [], [],
    )
    // Third block starts after the first ends -> back into lane 0
    expect(items.map((i) => i.lane)).toEqual([0, 1, 0])
    expect(items.every((i) => i.lanes === 2)).toBe(true)
  })

  it('should mix trainings, reservations and invitations in one lane assignment', () => {
    const items = layoutDay(
      [makeTraining({ startTime: '10:00', endTime: '11:00' })],
      [makeReservation({ startTime: '10:00', endTime: '11:00' })],
      [makeInvitation({ startTime: '10:30', endTime: '11:30' })],
    )
    expect(items).toHaveLength(3)
    expect(items.map((i) => i.lane)).toEqual([0, 1, 2])
    expect(items.every((i) => i.lanes === 3)).toBe(true)
    // Each positioned item keeps a reference to exactly one source entry
    expect(items.filter((i) => i.training).length).toBe(1)
    expect(items.filter((i) => i.reservation).length).toBe(1)
    expect(items.filter((i) => i.invitation).length).toBe(1)
  })

  it('should sort entries by start time, then by end time', () => {
    const items = layoutDay(
      [
        makeTraining({ startTime: '14:00', endTime: '15:00', title: 'late' }),
        makeTraining({ startTime: '09:00', endTime: '11:00', title: 'early long' }),
        makeTraining({ startTime: '09:00', endTime: '10:00', title: 'early short' }),
      ],
      [], [],
    )
    expect(items.map((i) => i.training?.title)).toEqual(['early short', 'early long', 'late'])
  })

  it('should share the max lane count across the whole day', () => {
    // Documented trade-off: a stable side-by-side split beats per-cluster widths that
    // would make blocks jump around as the day fills up.
    const items = layoutDay(
      [
        makeTraining({ startTime: '10:00', endTime: '11:00' }),
        makeTraining({ startTime: '10:00', endTime: '11:00' }),
        makeTraining({ startTime: '18:00', endTime: '19:00', title: 'alone' }),
      ],
      [], [],
    )
    expect(items.find((i) => i.training?.title === 'alone')?.lanes).toBe(2)
  })

  it('should return an empty list for a day with nothing on it', () => {
    expect(layoutDay([], [], [])).toEqual([])
  })
})

describe('layoutDay grid clamping', () => {
  it('should clamp a training starting before 7:00 and flag the top edge', () => {
    const [item] = layoutDay([makeTraining({ startTime: '06:00', endTime: '08:00' })], [], [])
    expect(item.startMin).toBe(7 * 60)
    expect(item.endMin).toBe(8 * 60)
    expect(item.clampedTop).toBe(true)
    expect(item.clampedBottom).toBe(false)
  })

  it('should clamp a training ending after 23:00 and flag the bottom edge', () => {
    const [item] = layoutDay([makeTraining({ startTime: '22:00', endTime: '23:30' })], [], [])
    expect(item.startMin).toBe(22 * 60)
    expect(item.endMin).toBe(23 * 60)
    expect(item.clampedTop).toBe(false)
    expect(item.clampedBottom).toBe(true)
  })

  it('should pin a training starting after the grid end to the last half hour', () => {
    const [item] = layoutDay([makeTraining({ startTime: '23:30', endTime: '23:59' })], [], [])
    expect(item.startMin).toBe(22 * 60 + 30)
    expect(item.endMin).toBe(23 * 60)
    expect(item.clampedBottom).toBe(true)
  })

  it('should pin a training ending before the grid start to the first half hour', () => {
    const [item] = layoutDay([makeTraining({ startTime: '05:00', endTime: '06:00' })], [], [])
    expect(item.startMin).toBe(7 * 60)
    expect(item.endMin).toBe(7 * 60 + 30)
    expect(item.clampedTop).toBe(true)
  })

  it('should give a very short training a 30-minute minimum height', () => {
    const [item] = layoutDay([makeTraining({ startTime: '10:00', endTime: '10:10' })], [], [])
    expect(item.endMin - item.startMin).toBe(30)
  })

  it('should not flag a training that fits inside the grid', () => {
    const [item] = layoutDay([makeTraining({ startTime: '09:00', endTime: '10:30' })], [], [])
    expect(item.clampedTop).toBe(false)
    expect(item.clampedBottom).toBe(false)
    expect(item.startMin).toBe(9 * 60)
    expect(item.endMin).toBe(10 * 60 + 30)
  })
})

describe('splitDay (untimed vs timed, V72)', () => {
  const date = '2026-07-20'

  it('should route an untimed training to the all-day lane, not the grid', () => {
    const untimed = makeTraining({ date, startTime: null, endTime: null, title: 'Rest day' })
    const result = splitDay(date, [untimed], [], [])
    expect(result.allDayTrainings).toEqual([untimed])
    expect(result.timed).toEqual([])
  })

  it('should route a timed training to the grid, not the all-day lane', () => {
    const timed = makeTraining({ date, startTime: '10:00', endTime: '11:00' })
    const result = splitDay(date, [timed], [], [])
    expect(result.timed.map((i) => i.training)).toEqual([timed])
    expect(result.allDayTrainings).toEqual([])
  })

  it('should split a mixed day into both lanes', () => {
    const untimed = makeTraining({ date, startTime: null, endTime: null, title: 'Rest day' })
    const timed = makeTraining({ date, startTime: '10:00', endTime: '11:00', title: 'Session' })
    const result = splitDay(date, [untimed, timed], [], [])
    expect(result.allDayTrainings.map((t) => t.title)).toEqual(['Rest day'])
    expect(result.timed.map((i) => i.training?.title)).toEqual(['Session'])
  })

  it('should route an invitation without times to the all-day lane', () => {
    const allDayInvite = makeInvitation({ date, startTime: null, endTime: null })
    const timedInvite = makeInvitation({ date, startTime: '18:00', endTime: '20:00' })
    const result = splitDay(date, [], [], [allDayInvite, timedInvite])
    expect(result.allDayInvitations).toEqual([allDayInvite])
    expect(result.timed.map((i) => i.invitation)).toEqual([timedInvite])
  })

  it('should ignore entries belonging to other days', () => {
    const result = splitDay(date, [
      makeTraining({ date: '2026-07-21', startTime: '10:00', endTime: '11:00' }),
      makeTraining({ date: '2026-07-21', startTime: null, endTime: null }),
    ], [
      makeReservation({ date: '2026-07-19' }),
    ], [
      makeInvitation({ date: '2026-07-22', startTime: null, endTime: null }),
    ])
    expect(result.timed).toEqual([])
    expect(result.allDayTrainings).toEqual([])
    expect(result.allDayInvitations).toEqual([])
  })

  it('should always place reservations in the grid (they always carry times)', () => {
    const reservation = makeReservation({ date, startTime: '17:00', endTime: '18:30' })
    const result = splitDay(date, [], [reservation], [])
    expect(result.timed.map((i) => i.reservation)).toEqual([reservation])
  })

  it('should lay out timed entries into lanes as it splits', () => {
    const result = splitDay(date, [
      makeTraining({ date, startTime: '10:00', endTime: '11:00' }),
      makeTraining({ date, startTime: '10:00', endTime: '11:00' }),
      makeTraining({ date, startTime: null, endTime: null }),
    ], [], [])
    expect(result.timed).toHaveLength(2)
    expect(result.timed.map((i) => i.lane)).toEqual([0, 1])
    expect(result.allDayTrainings).toHaveLength(1)
  })
})
