import type { SettlementSection } from '../types'

/**
 * What of a session's bulk payer may travel on the clipboard.
 *
 * A pure function rather than three lines inside the copy handler, for the reason `archiveDays` is
 * one: the rule is invisible after the fact. A pasted session looks right either way, and the copy
 * that quietly dropped its contractor is discovered a month later, as a rate divided by a
 * denominator that is short.
 *
 * ⚠️ **Only an institution travels.** A subscription covers one PERSON, and that person is not on
 * the slot about to be created — the server checks exactly that and refuses, so carrying it would
 * turn every paste of a retainer-covered session into an error on a copy that had nothing wrong
 * with it.
 *
 * A session with no coverage, and a read that failed (`null` section), both answer "nothing to
 * carry": copying must not stop working because a money read did not come back.
 */
export function travellingPayoutSource(
  section: SettlementSection | null,
): { id: string; name: string } | null {
  const coveredBy = section?.coveredBy
  if (!coveredBy || coveredBy.kind !== 'source') return null
  return { id: coveredBy.id, name: coveredBy.name }
}
