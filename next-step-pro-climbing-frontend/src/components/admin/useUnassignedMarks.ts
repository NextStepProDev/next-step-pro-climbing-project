import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminSettlementsApi } from '../../api/client'

/**
 * Where a closed session still has nobody to bill, as membership tests — the same shape and the
 * same reasoning as `useNoteMarks`, including why `dates` exists: a month cell knows its day but
 * not which slots sit on it, because the month payload carries counts rather than slot ids.
 *
 * ⚠️ Ids only, never a name. Who settles a session lives in the settlement tables, and the calendar
 * is served to anonymous visitors and cached — so the marker answers "is anything missing here"
 * and nothing more. That is also why it is a second request instead of a field on the slot.
 */
export interface UnassignedMarks {
  slots: Set<string>
  dates: Set<string>
}

const NONE: UnassignedMarks = { slots: new Set(), dates: new Set() }

/**
 * One fetch per visible range, handed to the calendar components through props.
 *
 * `enabled` is the caller's role check rather than a convenience: the endpoint is admin-only, so
 * asking as anybody else buys a guaranteed 403 on every calendar page load. Empty sets while
 * loading and while disabled, so no caller has to branch on undefined.
 *
 * The key sits under `['admin', 'settlements']`, which every settlement write already invalidates —
 * naming the payer makes its marker disappear without a manual refresh.
 */
export function useUnassignedMarks(enabled: boolean, from: string, to: string): UnassignedMarks {
  const { data } = useQuery({
    queryKey: ['admin', 'settlements', 'unassigned-markers', from, to],
    queryFn: () => adminSettlementsApi.getUnassignedMarkers(from, to),
    enabled: enabled && !!from && !!to,
  })

  return useMemo(() => {
    if (!data) return NONE
    return { slots: new Set(data.slotIds), dates: new Set(data.slotDates) }
  }, [data])
}
