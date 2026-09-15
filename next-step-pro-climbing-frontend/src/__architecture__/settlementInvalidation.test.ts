import { describe, it, expect } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * Whoever creates, edits or deletes a slot or an event must also mark the Settlements tab stale.
 *
 * Every one of those writes moves a figure there, and none of them look like money:
 *
 * - deleting takes the amounts and the bulk assignment by cascade, so revenue counted a session
 *   that no longer exists and the rate divided by it;
 * - editing the hours changes the rate's denominator, and the seat count decides whether the
 *   session sits on the "no payer" queue;
 * - dragging changes the DATE, which decides the month of the payer's rate;
 * - creating can land straight on the money screens — a contractor session arrives assigned, and a
 *   zero-seat slot arrives on the "no payer" queue and its calendar marker.
 *
 * The failure is silent and slow: the tab holds the global five-minute `staleTime`, so it serves a
 * cached page that quietly disagrees with the calendar, and the admin sees numbers that are simply
 * wrong for a while. It was real — none of the three delete paths invalidated anything of the kind
 * until the owner asked whether deleting a settled slot updates the tab.
 *
 * Deliberately a file-level check rather than a per-mutation one: matching a mutation to its own
 * `onSuccess` needs a parser, and a file that touches these endpoints without ever naming the key
 * is the shape that actually went wrong.
 */

const SRC = join(__dirname, '..')

/** The admin calls that create, move or destroy a session. */
const SESSION_WRITES = [
  'adminApi.createTimeSlot',
  'adminApi.updateTimeSlot',
  'adminApi.deleteTimeSlot',
  'adminApi.blockTimeSlot',
  'adminApi.unblockTimeSlot',
  'adminApi.createEvent',
  'adminApi.updateEvent',
  'adminApi.deleteEvent',
]

const INVALIDATES_SETTLEMENTS =
  /invalidateQueries\(\s*\{\s*queryKey:\s*\[\s*['"]admin['"]\s*,\s*['"]settlements['"]/

function sourceFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    if (entry === 'node_modules' || entry === '__architecture__') continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      out.push(...sourceFiles(full))
    } else if (/\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry)) {
      out.push(full)
    }
  }
  return out
}

describe('settlement cache invalidation', () => {
  it('marks the Settlements tab stale wherever a session is created, edited or deleted', () => {
    const offenders: string[] = []

    for (const file of sourceFiles(SRC)) {
      const relative = file.slice(SRC.length + 1)
      // The API client declares these calls; it is not the one that has to invalidate them.
      if (relative === join('api', 'client.ts')) continue
      const source = readFileSync(file, 'utf-8')
      const writes = SESSION_WRITES.filter((call) => source.includes(call))
      if (writes.length === 0) continue
      if (!INVALIDATES_SETTLEMENTS.test(source)) {
        offenders.push(`${relative} (${writes.join(', ')})`)
      }
    }

    expect(offenders, 'These files write to a session and leave the Settlements tab showing a '
      + "cached page — money for a session that changed or no longer exists. Add "
      + "queryClient.invalidateQueries({ queryKey: ['admin', 'settlements'] }) to the mutation.")
      .toEqual([])
  })

  it('actually watches files, or it proves nothing', () => {
    // A gate whose scan finds no call sites is green forever. Two of these files are the ones the
    // bug was reported against.
    const watched = sourceFiles(SRC).filter((file) => {
      const relative = file.slice(SRC.length + 1)
      if (relative === join('api', 'client.ts')) return false
      const source = readFileSync(file, 'utf-8')
      return SESSION_WRITES.some((call) => source.includes(call))
    })

    expect(watched.length).toBeGreaterThanOrEqual(5)
  })
})
