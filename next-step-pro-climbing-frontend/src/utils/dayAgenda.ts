import type { DayView, EventType } from '../types'

/**
 * What already sits on one day, read against the hours somebody proposed.
 *
 * Answering a training request used to mean a second tab: open the calendar, find the day, come
 * back and decide. This turns the public day view into the one question that decision asks — "is
 * anything in the way of these hours?" — so it can be answered where the button is.
 *
 * Pure and clock-free: every date is a `yyyy-MM-dd` label and every time an `HH:mm` string, which
 * compare correctly as strings. Nothing here builds a `Date`, so no timezone can shift a day.
 */

export type AgendaKind = 'slot' | 'event' | 'closed' | 'unavailable' | 'window'

export interface AgendaEntry {
  id: string
  kind: AgendaKind
  /** `HH:mm`. An all-day entry runs 00:00–24:00. */
  start: string
  end: string
  allDay: boolean
  title: string | null
  eventType: EventType | null
  /** Seats taken / offered — null where there is nothing to sell (absence, window, closed session). */
  occupancy: { taken: number; max: number } | null
  /** Shares time with the proposed hours. Never true for a window: a proposal inside one is its point. */
  overlaps: boolean
}

export interface ProposedRange {
  start: string
  end: string
}

const DAY_START = '00:00'
const DAY_END = '24:00'

const hm = (time: string) => time.slice(0, 5)

/** Touching ends do not collide: 17:00–19:00 and 19:00–20:00 can both happen. */
const overlaps = (aStart: string, aEnd: string, b: ProposedRange) => aStart < b.end && b.start < aEnd

/**
 * @param proposed the hours being decided on, or `null` for a whole day (everything collides then).
 */
export function buildDayAgenda(day: DayView, proposed: ProposedRange | null): AgendaEntry[] {
  const range = proposed ?? { start: DAY_START, end: DAY_END }
  const entries: AgendaEntry[] = []

  for (const slot of day.slots) {
    // A blocked slot is a cancelled one — the hour is free again, so it is noise here.
    if (slot.status === 'BLOCKED') continue
    const kind: AgendaKind = slot.isAvailabilityWindow || slot.status === 'AVAILABILITY_WINDOW' ? 'window'
      : slot.isUnavailable || slot.status === 'UNAVAILABLE' ? 'unavailable'
      : slot.status === 'CLOSED' ? 'closed'
      : 'slot'
    const start = hm(slot.startTime)
    const end = hm(slot.endTime)
    entries.push({
      id: `slot-${slot.id}`,
      kind,
      start,
      end,
      allDay: false,
      title: slot.eventTitle,
      eventType: null,
      occupancy: kind === 'slot' ? { taken: slot.currentParticipants, max: slot.maxParticipants } : null,
      overlaps: kind !== 'window' && overlaps(start, end, range),
    })
  }

  for (const event of day.events) {
    // The day view lists every event touching the day, so the hours depend on WHICH of its days
    // this is: a multi-day event runs from its start time on the first, to its end time on the last,
    // and through the whole of every day in between.
    if (event.startDate > day.date || event.endDate < day.date) continue
    const timed = !!event.startTime && !!event.endTime
    const start = timed && event.startDate === day.date ? hm(event.startTime!) : DAY_START
    const end = timed && event.endDate === day.date ? hm(event.endTime!) : DAY_END
    const kind: AgendaKind = event.eventType === 'UNAVAILABLE' ? 'unavailable' : 'event'
    entries.push({
      id: `event-${event.id}`,
      kind,
      start,
      end,
      allDay: start === DAY_START && end === DAY_END,
      title: event.title,
      eventType: event.eventType,
      occupancy: kind === 'event' && event.maxParticipants > 0
        ? { taken: event.currentParticipants, max: event.maxParticipants }
        : null,
      overlaps: overlaps(start, end, range),
    })
  }

  // All-day first (they frame the day), then by start time.
  return entries.sort((a, b) =>
    Number(b.allDay) - Number(a.allDay) || a.start.localeCompare(b.start) || a.end.localeCompare(b.end))
}
