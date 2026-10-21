import { useQuery } from '@tanstack/react-query'
import { rescheduleApi } from '../api/client'

/**
 * The client's open requests to move a booked term. Keyed under ['reservations', …] on purpose:
 * every mutation of a booking already invalidates that prefix, and cancelling a booking withdraws
 * its request on the server — the pending bar must disappear with it.
 */
export function useMyPendingReschedules() {
  return useQuery({
    queryKey: ['reservations', 'reschedule'],
    queryFn: rescheduleApi.getMyPending,
  })
}
