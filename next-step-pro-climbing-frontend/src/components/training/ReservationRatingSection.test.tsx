import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ReservationRatingSection } from './ReservationRatingSection'
import { makeReservation } from '../../test/factories'
import type { ReservationOverlayItem } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const rateReservation = vi.fn()
vi.mock('../../api/client', () => ({
  trainingCalendarApi: { rateReservation: (...args: unknown[]) => rateReservation(...args) },
}))

function renderSection(reservation: ReservationOverlayItem, onRated = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ReservationRatingSection reservation={reservation} onRated={onRated} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  rateReservation.mockResolvedValue(undefined)
})

/**
 * The rating decides between "summary" and "form" by reading `reservation.rpe` off its PROP, not
 * from what it just saved. That was invisible while the host closed the modal on success; now that
 * the modal stays open (a reply may be half-typed in the thread underneath), a host feeding it a
 * stale snapshot re-renders the FORM after a successful save — a rating that looks like it did
 * nothing. These pin the contract that makes the host's job clear.
 */
describe('ReservationRatingSection — the prop decides what is shown', () => {
  it('should show the form while the prop says the booking is unrated', () => {
    renderSection(makeReservation({ canRate: true, rpe: null }))

    expect(screen.getByText('rpe.rate')).toBeInTheDocument()
    expect(screen.queryByText('rpe.rated')).not.toBeInTheDocument()
  })

  it('should show the summary as soon as the prop carries the saved rating', () => {
    renderSection(makeReservation({ canRate: true, rpe: 7 }))

    expect(screen.getByText('rpe.rated')).toBeInTheDocument()
    expect(screen.queryByText('rpe.rate')).not.toBeInTheDocument()
  })

  it('should still be showing the form after a save whose prop never refreshed', async () => {
    // The failure the host has to prevent: saved on the server, unchanged on screen. Asserted
    // rather than avoided, so nobody "fixes" the host back to a snapshot without seeing this.
    const user = userEvent.setup()
    const stale = makeReservation({ canRate: true, rpe: null })
    const onRated = vi.fn()
    const { rerender } = renderSection(stale, onRated)

    await user.click(screen.getByRole('button', { name: '7' }))
    await user.click(screen.getByText('rpe.save'))

    await waitFor(() => expect(onRated).toHaveBeenCalled())
    expect(screen.getByText('rpe.rate')).toBeInTheDocument()

    // ...and resolves the moment the host hands it the refreshed row
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    rerender(
      <QueryClientProvider client={client}>
        <ReservationRatingSection reservation={{ ...stale, rpe: 7 }} onRated={onRated} />
      </QueryClientProvider>,
    )
    expect(screen.getByText('rpe.rated')).toBeInTheDocument()
  })
})
