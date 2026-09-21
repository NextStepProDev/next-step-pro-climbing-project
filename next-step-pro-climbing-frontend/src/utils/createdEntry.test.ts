import { describe, it, expect } from 'vitest'
import { takesParticipants, type CreatedCalendarEntry } from './createdEntry'
import type { EventDetail, TimeSlotAdmin } from '../types'

const slot = (over: Partial<TimeSlotAdmin> = {}): CreatedCalendarEntry => ({
  target: 'slot',
  slot: {
    id: 'slot-1',
    date: '2030-06-10',
    startTime: '10:00:00',
    endTime: '11:00:00',
    maxParticipants: 4,
    currentParticipants: 0,
    blocked: false,
    blockReason: null,
    title: null,
    eventId: null,
    isAvailabilityWindow: false,
    isUnavailable: false,
    ...over,
  },
})

const event = (over: Partial<EventDetail> = {}): CreatedCalendarEntry => ({
  target: 'event',
  event: {
    id: 'event-1',
    title: 'Kurs',
    description: null,
    location: null,
    eventType: 'TRAINING',
    startDate: '2030-06-10',
    endDate: '2030-06-10',
    maxParticipants: 4,
    currentParticipants: 0,
    active: true,
    startTime: '10:00:00',
    endTime: '12:00:00',
    courseId: null,
    courseTitle: null,
    ...over,
  },
})

/* Every kind the two calendar create forms can produce, because the answer decides whether the
 * admin is shown a detail modal or left alone — and the wrong answer is a modal with nothing in
 * it, which reads as a glitch rather than as a deliberate silence. */
describe('takesParticipants — is there a roster to write anybody down on', () => {
  it('should say yes to an ordinary slot with seats', () => {
    expect(takesParticipants(slot())).toBe(true)
  })

  it('should say no to an availability window', () => {
    expect(takesParticipants(slot({ isAvailabilityWindow: true, maxParticipants: 1 }))).toBe(false)
  })

  /* Seats deliberately left standing. The server zeroes them (`setUnavailable(true)`), so the
   * seat count alone would answer this correctly today — and that is exactly why the flag is read
   * separately: a rule that leans on the zeroing silently stops working the day anything reaches
   * here before the server has applied it. */
  it('should say no to an absence, on the flag rather than on the seats', () => {
    expect(takesParticipants(slot({ isUnavailable: true, maxParticipants: 1 }))).toBe(false)
  })

  it('should say no to a contractor session, which is stored with no seats', () => {
    expect(takesParticipants(slot({ maxParticipants: 0 }))).toBe(false)
  })

  it('should say yes to an ordinary event', () => {
    expect(takesParticipants(event())).toBe(true)
  })

  /* Seats left standing again, and again on purpose. An absence never arrives with any — the
   * server zeroes them on create even when the request asks for four (checked) — so the seat
   * count alone would answer this today. The type is read anyway because `EventSignupModal`
   * skips the section on the TYPE, not on the seats: the day that zeroing moves, a rule leaning
   * on it opens a modal with no participant section in it. */
  it('should say no to an absence event that kept its seat count', () => {
    expect(takesParticipants(event({ eventType: 'UNAVAILABLE', maxParticipants: 4 }))).toBe(false)
  })

  /* An event with no seats renders the participants section and then has every write refused
   * ("Dostępnych miejsc: 0"), so opening it would offer a form that cannot work. */
  it('should say no to an event with no seats even though its modal would show the section', () => {
    expect(takesParticipants(event({ maxParticipants: 0 }))).toBe(false)
  })
})
