import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AdminHubPanel } from './AdminHubPanel'
import type { AdminNotifications } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getNotifications = vi.fn()

vi.mock('../../api/client', () => ({
  adminApi: {
    getNotifications: () => getNotifications(),
  },
}))

const NOTHING_PENDING: AdminNotifications = {
  pendingRequests: 0,
  newReservations: 0,
  newWaitlistEntries: 0,
  athleteActivity: 0,
  newUsers: 0,
}

async function renderHub(notifications: AdminNotifications) {
  getNotifications.mockResolvedValue(notifications)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <AdminHubPanel />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  // The tiles render immediately; only the counters wait on the request.
  await screen.findByRole('link', { name: /tabs.settlements/ })
}

describe('AdminHubPanel', () => {
  beforeEach(() => {
    getNotifications.mockReset()
  })

  it('lays the whole panel out as tiles, grouped', async () => {
    await renderHub(NOTHING_PENDING)

    expect(screen.getAllByRole('link')).toHaveLength(18)
    expect(screen.getByText('tabGroups.calendar')).toBeInTheDocument()
    expect(screen.getByText('tabGroups.content')).toBeInTheDocument()
    expect(screen.getByText('tabGroups.team')).toBeInTheDocument()
    expect(screen.getByText('tabGroups.system')).toBeInTheDocument()
  })

  it('explains what each tab is for', async () => {
    // The hint is the half of this screen that answers "where do I set the hero image?".
    await renderHub(NOTHING_PENDING)

    expect(screen.getByRole('link', { name: /tabs.site/ })).toHaveTextContent('hub.hints.site')
  })

  it('says so plainly when nothing is waiting', async () => {
    // A quiet line, not a vanishing section: a block that comes and goes shifts everything below.
    await renderHub(NOTHING_PENDING)

    expect(await screen.findByText('hub.allClear')).toBeInTheDocument()
  })

  it('raises only the counters that are actually non-zero', async () => {
    await renderHub({
      pendingRequests: 2,
      newReservations: 1,
      newWaitlistEntries: 0,
      athleteActivity: 0,
      newUsers: 0,
    })

    const attention = (await screen.findByText('hub.attention')).parentElement as HTMLElement
    const raised = within(attention).getAllByRole('link')

    expect(raised).toHaveLength(2)
    expect(raised.map((link) => link.getAttribute('href'))).toEqual([
      '/admin/reservations',
      '/admin/requests',
    ])
    expect(screen.queryByText('hub.allClear')).not.toBeInTheDocument()
  })

  it('counts waitlist joins together with new reservations', async () => {
    await renderHub({ ...NOTHING_PENDING, newReservations: 2, newWaitlistEntries: 3 })

    const attention = (await screen.findByText('hub.attention')).parentElement as HTMLElement
    expect(within(attention).getByRole('link')).toHaveTextContent('5')
  })

  it('sends Slots to its own route, not to the hub it replaced', async () => {
    await renderHub(NOTHING_PENDING)

    expect(screen.getByRole('link', { name: /tabs.slots/ })).toHaveAttribute('href', '/admin/slots')
  })
})
