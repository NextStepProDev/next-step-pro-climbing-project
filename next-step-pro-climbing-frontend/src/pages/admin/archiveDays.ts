import type { EventDetail, TimeSlotAdmin } from '../../types'

export interface ArchiveDays {
  /** Newest first — the order the archive reads in. */
  days: string[]
  slotsByDate: Record<string, TimeSlotAdmin[]>
  eventsByDate: Record<string, EventDetail[]>
  /** True when neither list has anything, so the empty state is about the archive, not the slots. */
  empty: boolean
}

/**
 * One archive out of the two rows a past session could have been recorded as.
 *
 * Kept apart from the panel because every line of it is a decision that is easy to get wrong and
 * impossible to see afterwards: the archive looks plausible whichever way these go.
 *
 * ⚠️ `today` is passed in rather than read here. The cutoff has to be Warsaw's calendar day
 * (`todayInWarsaw`), and a helper that reaches for a clock is a helper no test can pin down.
 *
 * ⚠️ Nothing de-duplicates slots against events, and nothing needs to: the server's `getPastSlots`
 * already drops rows that belong to an event. Those per-day bookkeeping slots exist for every
 * multi-day event, so without that filter a three-day course would be listed four times over.
 *
 * `active` is deliberately not filtered on. It is a visibility toggle for the public listing, not
 * a cancellation — a course that ran and was later hidden still happened, and this list is the
 * owner's own record of what happened rather than a copy of what visitors can see.
 */
export function buildArchiveDays(
  pastSlots: TimeSlotAdmin[] | undefined,
  allEvents: EventDetail[] | undefined,
  today: string,
): ArchiveDays {
  const slotsByDate = (pastSlots ?? []).reduce<Record<string, TimeSlotAdmin[]>>((acc, slot) => {
    acc[slot.date] = [...(acc[slot.date] ?? []), slot]
    return acc
  }, {})

  // An event is past once its LAST day is behind us — a trip that ends today is still running.
  const pastEvents = (allEvents ?? []).filter((event) => event.endDate < today)

  // Filed under the day it started, exactly once. Filing it under every day it covered would put
  // one trip in five places; filing it under its end date would sort it away from the days it
  // actually occupied.
  const eventsByDate = pastEvents.reduce<Record<string, EventDetail[]>>((acc, event) => {
    acc[event.startDate] = [...(acc[event.startDate] ?? []), event]
    return acc
  }, {})

  // The union, so a day that only ever held an event still gets a heading — and so the page count
  // is over the days actually shown rather than over the slots alone.
  const days = [...new Set([...Object.keys(slotsByDate), ...Object.keys(eventsByDate)])]
    .sort()
    .reverse()

  return {
    days,
    slotsByDate,
    eventsByDate,
    empty: (pastSlots?.length ?? 0) === 0 && pastEvents.length === 0,
  }
}
