import { describe, it, expect } from 'vitest'
import { buildDayAgenda } from './dayAgenda'
import type { EventSummary, TimeSlot } from '../types'

function slot(overrides: Partial<TimeSlot> = {}): TimeSlot {
  return {
    id: 's1',
    startTime: '17:30:00',
    endTime: '19:00:00',
    maxParticipants: 6,
    currentParticipants: 3,
    status: 'AVAILABLE',
    isUserRegistered: false,
    eventTitle: 'Trening grupowy',
    isAvailabilityWindow: false,
    isUnavailable: false,
    reservedSeats: 0,
    isReservedForUser: false,
    ...overrides,
  }
}

function event(overrides: Partial<EventSummary> = {}): EventSummary {
  return {
    id: 'e1',
    title: 'Kurs skałkowy',
    description: null,
    location: null,
    eventType: 'COURSE',
    startDate: '2030-06-10',
    endDate: '2030-06-12',
    startTime: '09:00:00',
    endTime: '16:00:00',
    isMultiDay: true,
    maxParticipants: 8,
    currentParticipants: 5,
    isUserRegistered: false,
    enrollmentOpen: true,
    courseId: null,
    coursePublished: false,
    userWaitlistStatus: null,
    waitlistEntryId: null,
    confirmationDeadline: null,
    userWaitlistPosition: 0,
    userParticipants: 0,
    reservedSeats: 0,
    isReservedForUser: false,
    ...overrides,
  }
}

const day = (date: string, slots: TimeSlot[] = [], events: EventSummary[] = []) => ({ date, slots, events })

describe('buildDayAgenda — what stands in the way of the proposed hours', () => {
  it('should flag a slot sharing time with the proposal', () => {
    const [entry] = buildDayAgenda(day('2030-06-10', [slot()]), { start: '17:00', end: '19:00' })
    expect(entry).toMatchObject({ kind: 'slot', start: '17:30', end: '19:00', overlaps: true })
    expect(entry.occupancy).toEqual({ taken: 3, max: 6 })
  })

  it('should not flag entries that only touch the proposal at an edge', () => {
    const entries = buildDayAgenda(
      day('2030-06-10', [slot({ startTime: '19:00:00', endTime: '20:00:00' })]),
      { start: '17:00', end: '19:00' },
    )
    expect(entries[0].overlaps).toBe(false)
  })

  it('should read an absence slot as unavailable and a zero-seat session as closed', () => {
    const entries = buildDayAgenda(day('2030-06-10', [
      slot({ id: 'a', isUnavailable: true, status: 'UNAVAILABLE', maxParticipants: 0 }),
      slot({ id: 'b', status: 'CLOSED', maxParticipants: 0, startTime: '08:00:00', endTime: '10:00:00' }),
    ]), { start: '17:00', end: '19:00' })
    expect(entries.map((e) => [e.kind, e.overlaps, e.occupancy])).toEqual([
      ['closed', false, null],
      ['unavailable', true, null],
    ])
  })

  it('should never call an availability window a conflict — a proposal inside one is its point', () => {
    const [entry] = buildDayAgenda(
      day('2030-06-10', [slot({ isAvailabilityWindow: true, status: 'AVAILABILITY_WINDOW' })]),
      { start: '17:00', end: '19:00' },
    )
    expect(entry).toMatchObject({ kind: 'window', overlaps: false })
  })

  it('should leave out a blocked slot, because the hour is free again', () => {
    expect(buildDayAgenda(day('2030-06-10', [slot({ status: 'BLOCKED' })]), null)).toEqual([])
  })

  it('should take a multi-day event from its start time on the first day', () => {
    const [entry] = buildDayAgenda(day('2030-06-10', [], [event()]), { start: '07:00', end: '08:00' })
    expect(entry).toMatchObject({ start: '09:00', end: '24:00', allDay: false, overlaps: false })
  })

  it('should take the whole of a day in the middle of a multi-day event', () => {
    const [entry] = buildDayAgenda(day('2030-06-11', [], [event()]), { start: '07:00', end: '08:00' })
    expect(entry).toMatchObject({ start: '00:00', end: '24:00', allDay: true, overlaps: true })
  })

  it('should end a multi-day event at its end time on the last day', () => {
    const [entry] = buildDayAgenda(day('2030-06-12', [], [event()]), { start: '17:00', end: '19:00' })
    expect(entry).toMatchObject({ start: '00:00', end: '16:00', overlaps: false })
  })

  it('should read an UNAVAILABLE event as an absence without seats', () => {
    const [entry] = buildDayAgenda(
      day('2030-06-11', [], [event({ eventType: 'UNAVAILABLE', maxParticipants: 0, startTime: null, endTime: null })]),
      { start: '17:00', end: '19:00' },
    )
    expect(entry).toMatchObject({ kind: 'unavailable', allDay: true, occupancy: null, overlaps: true })
  })

  it('should read a one-day event without hours as the whole day', () => {
    const [entry] = buildDayAgenda(
      day('2030-06-10', [], [event({ endDate: '2030-06-10', isMultiDay: false, startTime: null, endTime: null })]),
      { start: '17:00', end: '19:00' },
    )
    expect(entry).toMatchObject({ kind: 'event', start: '00:00', end: '24:00', allDay: true, overlaps: true })
  })

  it('should treat a whole-day proposal as colliding with everything on the day', () => {
    const [entry] = buildDayAgenda(day('2030-06-10', [slot({ startTime: '06:00:00', endTime: '07:00:00' })]), null)
    expect(entry.overlaps).toBe(true)
  })

  it('should list all-day entries first, then by start time', () => {
    const entries = buildDayAgenda(day('2030-06-11',
      [slot({ id: 'late', startTime: '18:00:00', endTime: '19:00:00' }), slot({ id: 'early', startTime: '08:00:00', endTime: '09:00:00' })],
      [event()],
    ), null)
    expect(entries.map((e) => e.id)).toEqual(['event-e1', 'slot-early', 'slot-late'])
  })
})
