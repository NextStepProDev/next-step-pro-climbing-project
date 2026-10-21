import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RescheduleRequestsSection } from './RescheduleRequestsSection'
import type { AdminRescheduleRequest } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts && 'count' in opts ? `${key}:${opts.count}` : key),
    i18n: { language: 'pl' },
  }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getRescheduleRequests = vi.fn()
const acceptRescheduleRequest = vi.fn()
vi.mock('../../api/client', () => ({
  adminApi: {
    getRescheduleRequests: () => getRescheduleRequests(),
    acceptRescheduleRequest: (id: string) => acceptRescheduleRequest(id),
    rejectRescheduleRequest: vi.fn(),
  },
  // The day agenda reads the public day view; an empty day keeps it out of the way here
  calendarApi: { getDayView: () => Promise.resolve({ date: '2026-11-05', slots: [], events: [] }) },
}))

const REQUEST: AdminRescheduleRequest = {
  id: 'r-1', userId: 'u-1', userFullName: 'Jan Kowalski', userEmail: 'jan@test.local', userPhone: null,
  targetType: 'SLOT', timeSlotId: 's-1', eventId: null, title: 'Trening',
  oldDate: '2026-11-03', oldEndDate: null, oldStartTime: '18:00:00', oldEndTime: '20:00:00',
  newDate: '2026-11-05', newEndDate: null, newStartTime: '18:00:00', newEndTime: '20:00:00',
  comment: 'W czwartek pasuje mi lepiej', status: 'PENDING', adminNote: null,
  createdAt: '2026-10-21T10:00:00Z', resolvedAt: null,
  otherParticipants: [], otherGuests: 0, stale: false,
}

function renderSection() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <RescheduleRequestsSection />
    </QueryClientProvider>,
  )
}

describe('RescheduleRequestsSection', () => {
  beforeEach(() => {
    getRescheduleRequests.mockReset()
    acceptRescheduleRequest.mockReset().mockResolvedValue({ request: { ...REQUEST, status: 'ACCEPTED' }, notifiedOthers: 2 })
  })

  it('names everyone else the move would take along, before and at the confirmation', async () => {
    getRescheduleRequests.mockResolvedValue({
      pending: [{ ...REQUEST, otherParticipants: ['Anna Nowak'], otherGuests: 1 }],
      recent: [],
    })
    renderSection()

    expect(await screen.findByText(/Anna Nowak/)).toBeInTheDocument()
    expect(screen.getByText('reschedule.othersHint')).toBeInTheDocument()

    await userEvent.setup().click(screen.getByRole('button', { name: 'reschedule.accept' }))

    // 1 requester + 1 other + 1 guest
    const confirm = await screen.findByRole('button', { name: 'reschedule.acceptMany:3' })
    await userEvent.setup().click(confirm)
    await waitFor(() => expect(acceptRescheduleRequest).toHaveBeenCalledWith('r-1'))
  })

  it('says plainly when the requester is alone on the term', async () => {
    getRescheduleRequests.mockResolvedValue({ pending: [REQUEST], recent: [] })
    renderSection()

    expect(await screen.findByText('reschedule.alone')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'reschedule.accept' }))
    expect(await screen.findByRole('button', { name: 'reschedule.acceptOne' })).toBeInTheDocument()
  })

  it('blocks accepting a request whose term was moved since', async () => {
    getRescheduleRequests.mockResolvedValue({ pending: [{ ...REQUEST, stale: true }], recent: [] })
    renderSection()

    expect(await screen.findByText('reschedule.stale')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'reschedule.accept' })).toBeDisabled()
  })
})
