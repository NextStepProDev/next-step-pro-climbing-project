import type { EventDetail, TimeSlotAdmin } from '../types'

/**
 * What a calendar create form just wrote, in the shape the server answered with.
 *
 * A union rather than a bare id because the caller's only question — is there anybody to write
 * down on this? — is answered by the row itself, and the create response already carries every
 * field that answers it. Asking the server again for what it just returned would be a second
 * request that can fail on its own.
 */
export type CreatedCalendarEntry =
  | { target: 'slot'; slot: TimeSlotAdmin }
  | { target: 'event'; event: EventDetail }

/**
 * Whether the detail modal for this entry has a roster to add people to.
 *
 * The create forms only offer invitations — a held seat somebody still has to take — so writing a
 * client down means finding the new entry in the calendar afterwards. Showing it straight after
 * saving removes that hunt, but only where there is something to do: an absence, an availability
 * window and a zero-seat (contractor) session all render a detail modal with no participant
 * section at all, so opening one would be a modal to dismiss and nothing else.
 *
 * ⚠️ The slot branch is the exact twin of `isBookable` in `SlotDetailModal` (seats, window,
 * absence — the same three facts, read off the admin shape the create call returned rather than
 * the calendar DTO the modal gets). Loosening one without the other opens a modal with nothing
 * in it.
 *
 * ⚠️ The event branch asks BOTH halves, and each earns its place differently. The seat count is
 * the strict half: `EventSignupModal` renders the participants section for every type but
 * UNAVAILABLE, seats or no seats, and with none every write comes back refused ("Dostępnych
 * miejsc: 0") — so judged on the type alone this would open a form that cannot work. The type is
 * the belt-and-braces half: an absence already arrives with its seats zeroed (the server does it
 * on create, and the form disables the field), so today the seat count alone would answer — which
 * is exactly why the type is read separately, the same reasoning as `isUnavailable` above. A rule
 * that leans on somebody else's zeroing stops working silently the day that zeroing moves.
 */
export function takesParticipants(created: CreatedCalendarEntry): boolean {
  if (created.target === 'slot') {
    const { slot } = created
    return slot.maxParticipants > 0 && !slot.isAvailabilityWindow && !slot.isUnavailable
  }
  const { event } = created
  return event.maxParticipants > 0 && event.eventType !== 'UNAVAILABLE'
}
