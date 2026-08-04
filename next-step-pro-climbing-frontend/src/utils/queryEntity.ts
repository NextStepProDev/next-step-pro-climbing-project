/**
 * placeholderData that keeps the previous page, but only within the same entity.
 *
 * The global default is `keepPreviousData` (see main.tsx), which compares nothing. For a
 * calendar that is half right: paging to the next month should keep the grid on screen
 * instead of collapsing it to a spinner and snapping back one fetch later. But the same
 * rule would also keep it when the coach switches to a different athlete, rendering one
 * person's plan under another person's name until the fetch lands.
 *
 * Keys are written entity first, page last — ['trainingCalendar','range',scopeKey,from,to]
 * — so "agrees on everything but the trailing `paramCount` elements" is exactly
 * "same entity, different page".
 */
export function keepWithinEntity<T>(
  previous: T | undefined,
  previousQuery: { queryKey: readonly unknown[] } | undefined,
  key: readonly unknown[],
  paramCount: number,
): T | undefined {
  if (previous === undefined || !previousQuery) return undefined

  const previousKey = previousQuery.queryKey
  if (previousKey.length !== key.length) return undefined

  const shared = key.length - paramCount
  for (let i = 0; i < shared; i++) {
    if (!Object.is(previousKey[i], key[i])) return undefined
  }
  return previous
}
