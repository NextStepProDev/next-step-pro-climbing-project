import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import { UserStatsView } from './UserStatsView'
import type { UserStats } from '../../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getStats = vi.fn()
vi.mock('../../../api/client', () => ({
  adminUserStatsApi: { get: () => getStats() },
}))

function makeStats(overrides: Partial<UserStats> = {}): UserStats {
  return {
    totals: { accounts: 10, verified: 8, athletes: 2, newsletter: 4, admins: 1 },
    registrations: [
      { month: '2026-07-01', total: 0, verified: 0 },
      { month: '2026-08-01', total: 3, verified: 2 },
    ],
    funnel: { booked: 6, returning: 4 },
    cohorts: { active: 3, dormant: 3, never: 4, windowDays: 90 },
    topClients: [{ userId: 'u-1', firstName: 'Anna', lastName: 'Kowalska', attended: 12 }],
    newsletter: { subscribed: 4, unsubscribed: 3, undecided: 3 },
    athletes: { flagged: 2, consented: 1, withPlan: 1 },
    ...overrides,
  }
}

function renderView() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <UserStatsView />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('UserStatsView', () => {
  beforeEach(() => {
    getStats.mockReset()
  })

  it('links every top client through to their card', async () => {
    getStats.mockResolvedValue(makeStats())
    renderView()

    const link = await screen.findByRole('link', { name: /Anna Kowalska/ })
    expect(link).toHaveAttribute('href', '/admin/users/u-1')
  })

  /**
   * The whole base has to be visible somewhere on this screen. A cohort dropped from the split
   * would leave people the admin cannot see from any of the three numbers.
   */
  it('renders every cohort, including the one with no bookings', async () => {
    getStats.mockResolvedValue(makeStats())
    renderView()

    await screen.findByText('users.stats.cohorts.title')
    for (const key of ['active', 'dormant', 'never']) {
      expect(screen.getByText(`users.stats.cohorts.${key}`)).toBeInTheDocument()
    }
  })

  /** Three zeroes would read as a broken feature rather than one nobody has switched on. */
  it('hides the athlete breakdown when nobody carries the flag', async () => {
    getStats.mockResolvedValue(
      makeStats({
        totals: { accounts: 10, verified: 8, athletes: 0, newsletter: 4, admins: 1 },
        athletes: { flagged: 0, consented: 0, withPlan: 0 },
      }),
    )
    renderView()

    await screen.findByText('users.stats.funnel.title')
    expect(screen.queryByText('users.stats.athletes.title')).not.toBeInTheDocument()
  })

  it('shows an empty state instead of dividing by an empty base', async () => {
    getStats.mockResolvedValue(
      makeStats({ totals: { accounts: 0, verified: 0, athletes: 0, newsletter: 0, admins: 0 } }),
    )
    renderView()

    await screen.findByText('users.stats.empty')
    expect(screen.queryByText('users.stats.funnel.title')).not.toBeInTheDocument()
  })

  it('keeps an empty month on the chart rather than dropping the column', async () => {
    getStats.mockResolvedValue(makeStats())
    const { container } = renderView()

    await screen.findByText('users.stats.registrations.title')
    await waitFor(() => {
      // One column per month shipped, empty ones included — a missing column reads as missing data.
      expect(container.querySelectorAll('[role="img"]')).toHaveLength(2)
    })
  })
})
