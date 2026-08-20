import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { adminApi } from '../../api/client'

/**
 * Where the owner already has notes, as membership tests rather than lists — that is the only
 * question a calendar cell ever asks.
 *
 * `dates` exists because a month cell knows its day but not which slots sit on it: the month
 * payload carries counts, not slot ids. Events need no such thing — the cell already holds the
 * event objects, so `events` is matched by id there too.
 */
export interface NoteMarks {
  slots: Set<string>
  dates: Set<string>
  events: Set<string>
  trainings: Set<string>
}

const NONE: NoteMarks = { slots: new Set(), dates: new Set(), events: new Set(), trainings: new Set() }

/**
 * One fetch per visible range, shared by every calendar component through props.
 *
 * `enabled` is the caller's role check, not a convenience: the endpoint is admin-only, so asking
 * as anybody else buys a guaranteed 403 on every calendar page load. Returns empty sets while
 * loading and when disabled, so callers never branch on undefined.
 *
 * The key sits under `['admin', 'notes']`, which is what AdminPrivateNote invalidates after a
 * save or a delete — writing a note lights its marker up without a manual refresh.
 */
export function useNoteMarks(enabled: boolean, from: string, to: string): NoteMarks {
  const { data } = useQuery({
    queryKey: ['admin', 'notes', 'markers', from, to],
    queryFn: () => adminApi.getPrivateNoteMarkers(from, to),
    enabled: enabled && !!from && !!to,
  })

  return useMemo(() => {
    if (!data) return NONE
    return {
      slots: new Set(data.slotIds),
      dates: new Set(data.slotDates),
      events: new Set(data.eventIds),
      trainings: new Set(data.trainingIds),
    }
  }, [data])
}
