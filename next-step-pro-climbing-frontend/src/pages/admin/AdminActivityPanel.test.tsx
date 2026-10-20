import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AdminActivityPanel } from './AdminActivityPanel'
import type { ActivityLog } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

function log(n: number): ActivityLog {
  return {
    id: `log-${n}`,
    actionType: 'RESERVATION_CREATED',
    userFullName: `Osoba ${n}`,
    userEmail: `o${n}@test.local`,
    createdAt: '2026-10-20T10:00:00Z',
  } as ActivityLog
}

const pages: Record<number, ActivityLog[]> = {}
const getActivityLogs = vi.fn((page: number) => Promise.resolve(pages[page] ?? []))

vi.mock('../../api/client', () => ({
  adminApi: { getActivityLogs: (page: number) => getActivityLogs(page) },
}))

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <AdminActivityPanel />
    </QueryClientProvider>,
  )
  return client
}

beforeEach(() => {
  pages[0] = Array.from({ length: 20 }, (_, i) => log(i))
  pages[1] = Array.from({ length: 5 }, (_, i) => log(20 + i))
  getActivityLogs.mockClear()
})

describe('AdminActivityPanel', () => {
  it('keeps the older entries it loaded when the list refreshes itself', async () => {
    const user = userEvent.setup()
    const client = renderPanel()
    await screen.findByText('Osoba 0')

    await user.click(screen.getByText('activity.loadMore'))
    await screen.findByText('Osoba 24')

    // The 30-second poll, run by hand: it used to reset the list to the first page.
    await act(() => client.refetchQueries({ queryKey: ['admin', 'activity-logs'] }))

    expect(screen.getByText('Osoba 24')).toBeTruthy()
  })

  it('shows an entry once even when new activity shifted the pages under it', async () => {
    const user = userEvent.setup()
    renderPanel()
    await screen.findByText('Osoba 0')

    // A new entry arrives on top before "load more": offsets move by one, so page 1 now starts
    // with the last entry already shown.
    pages[1] = [log(19), ...pages[1]]
    await user.click(screen.getByText('activity.loadMore'))
    await screen.findByText('Osoba 24')

    await waitFor(() => expect(screen.getAllByText('Osoba 19')).toHaveLength(1))
  })
})
