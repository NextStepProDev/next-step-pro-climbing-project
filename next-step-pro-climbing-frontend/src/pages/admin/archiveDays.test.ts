import { describe, it, expect } from 'vitest'
import { buildArchiveDays } from './archiveDays'
import type { EventDetail, TimeSlotAdmin } from '../../types'

const slot = (date: string, id = date): TimeSlotAdmin =>
  ({ id, date, startTime: '10:00:00', endTime: '11:00:00' }) as TimeSlotAdmin

const event = (startDate: string, endDate: string, id = startDate): EventDetail =>
  ({ id, title: 'Kurs', startDate, endDate }) as EventDetail

const TODAY = '2026-09-14'

describe('buildArchiveDays — one archive out of slots and events', () => {
  it('puts both kinds under the day they happened, newest first', () => {
    const { days, slotsByDate, eventsByDate } = buildArchiveDays(
      [slot('2026-09-10'), slot('2026-09-12')],
      [event('2026-09-11', '2026-09-11')],
      TODAY,
    )

    expect(days).toEqual(['2026-09-12', '2026-09-11', '2026-09-10'])
    expect(slotsByDate['2026-09-12']).toHaveLength(1)
    expect(eventsByDate['2026-09-11']).toHaveLength(1)
  })

  // Filing it under every day it covered is the obvious implementation and it puts one trip in
  // five places; the admin then counts five courses where there was one.
  it('lists a multi-day event once, under the day it started', () => {
    const { days, eventsByDate } = buildArchiveDays(
      [],
      [event('2026-09-08', '2026-09-11')],
      TODAY,
    )

    expect(days).toEqual(['2026-09-08'])
    expect(eventsByDate['2026-09-08']).toHaveLength(1)
    expect(eventsByDate['2026-09-11']).toBeUndefined()
  })

  // "Past" is about the LAST day. An event that ends today is still running, and an archive that
  // swallows it makes the admin think it was cancelled.
  it('keeps an event that ends today out of the archive, and takes yesterday in', () => {
    const { days } = buildArchiveDays(
      [],
      [event('2026-09-12', TODAY, 'still-running'), event('2026-09-10', '2026-09-13', 'finished')],
      TODAY,
    )

    expect(days).toEqual(['2026-09-10'])
  })

  // A day whose only entry was an event had no slot to hang a heading on, so without the union it
  // would vanish from the list entirely — and so would its page in the pager.
  it('gives a heading to a day that only ever held an event', () => {
    const { days } = buildArchiveDays([slot('2026-09-12')], [event('2026-09-09', '2026-09-09')], TODAY)

    expect(days).toEqual(['2026-09-12', '2026-09-09'])
  })

  it('reports empty only when neither list has anything', () => {
    expect(buildArchiveDays([], [], TODAY).empty).toBe(true)
    // An event on its own must not read as "no past sessions".
    expect(buildArchiveDays([], [event('2026-09-01', '2026-09-02')], TODAY).empty).toBe(false)
    expect(buildArchiveDays([slot('2026-09-01')], [], TODAY).empty).toBe(false)
  })

  // Future events share the list the events panel fills; they are not archive material.
  it('ignores events that have not happened yet', () => {
    const { days, empty } = buildArchiveDays([], [event('2026-12-01', '2026-12-03')], TODAY)

    expect(days).toEqual([])
    expect(empty).toBe(true)
  })

  it('survives both lists being absent while the queries are still disabled', () => {
    expect(buildArchiveDays(undefined, undefined, TODAY)).toMatchObject({ days: [], empty: true })
  })
})
