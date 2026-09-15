/** A slot has exactly one shape. The wire format keeps two booleans (that is what the API and the
 *  DB store); this type is the same fact stated so that the impossible combination cannot be typed. */
export type SlotKind = 'REGULAR' | 'WINDOW' | 'UNAVAILABLE'

/**
 * What the admin is CREATING, which is not the same question as what shape the slot has.
 *
 * ⚠️ `CONTRACTOR` is deliberately NOT a `SlotKind`. A session run for a school is stored as an
 * ordinary slot with zero seats plus an assignment that lives in another table entirely
 * (`session_payouts` — it cannot ride on the slot, because slot payloads are served to anonymous
 * visitors and cached, and the school's name has no business being there). So it does not come back
 * from the wire: `slotKindOf` on such a slot answers `REGULAR`, correctly. Adding it to `SlotKind`
 * would break that round trip and hand the edit forms a value they cannot render.
 */
export type CreateSlotKind = SlotKind | 'CONTRACTOR'

/** Seats on a contractor session. Zero is what makes it unbookable — and, once it is over, the only
 *  signal separating work done for somebody else from an hour nobody took up. */
export const CONTRACTOR_SEATS = 0

export function slotKindOf(flags: { isAvailabilityWindow?: boolean; isUnavailable?: boolean }): SlotKind {
  if (flags.isUnavailable) return 'UNAVAILABLE'
  if (flags.isAvailabilityWindow) return 'WINDOW'
  return 'REGULAR'
}

export function slotKindFlags(kind: CreateSlotKind) {
  return {
    isAvailabilityWindow: kind === 'WINDOW',
    isUnavailable: kind === 'UNAVAILABLE',
  }
}
