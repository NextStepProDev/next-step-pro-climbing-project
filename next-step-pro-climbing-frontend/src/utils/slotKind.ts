/** A slot has exactly one shape. The wire format keeps two booleans (that is what the API and the
 *  DB store); this type is the same fact stated so that the impossible combination cannot be typed. */
export type SlotKind = 'REGULAR' | 'WINDOW' | 'UNAVAILABLE'

export function slotKindOf(flags: { isAvailabilityWindow?: boolean; isUnavailable?: boolean }): SlotKind {
  if (flags.isUnavailable) return 'UNAVAILABLE'
  if (flags.isAvailabilityWindow) return 'WINDOW'
  return 'REGULAR'
}

export function slotKindFlags(kind: SlotKind) {
  return {
    isAvailabilityWindow: kind === 'WINDOW',
    isUnavailable: kind === 'UNAVAILABLE',
  }
}
