import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { EventSignupModal } from './EventSignupModal'
import { ToastProvider } from '../../context/ToastContext'
import type { EventSummary } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isAdmin: true,
    user: { id: 'admin-1', firstName: 'A', lastName: 'B', phone: '123' },
  }),
}))

// The note has its own tests; here it would only be a second source of the same signal.
vi.mock('../admin/AdminPrivateNote', () => ({ AdminPrivateNote: () => null }))

vi.mock('../../api/client', () => ({
  calendarApi: { getEventSummary: () => Promise.resolve(event()) },
  adminSettlementsApi: {
    getSection: vi.fn().mockResolvedValue({
      targetDate: '2030-06-10', lines: [], payoutSourceId: null, payoutSourceName: null,
    }),
    listSources: vi.fn().mockResolvedValue([]),
  },
  adminApi: {
    getEventParticipants: () => Promise.resolve({
      eventId: 'event-1',
      participants: [],
      guestParticipants: [],
    }),
    getAllUsers: () => Promise.resolve([]),
    deleteEvent: vi.fn(),
  },
  reservationApi: { createForEvent: vi.fn(), updateEventParticipants: vi.fn() },
}))

function event(overrides: Partial<EventSummary> = {}): EventSummary {
  return {
    id: 'event-1',
    title: 'Wyjazd w Jurę',
    description: null,
    location: null,
    eventType: 'TRAINING',
    startDate: '2030-06-10',
    endDate: '2030-06-10',
    startTime: '10:00:00',
    endTime: '17:00:00',
    isMultiDay: false,
    maxParticipants: 8,
    currentParticipants: 0,
    isUserRegistered: false,
    enrollmentOpen: true,
    courseId: null,
    coursePublished: false,
    userWaitlistStatus: null,
    waitlistEntryId: null,
    confirmationDeadline: null,
    userWaitlistPosition: 0,
    userParticipants: 0,
    reservedSeats: 0,
    isReservedForUser: false,
    ...overrides,
  }
}

function tree(ev: EventSummary, isOpen: boolean, onClose: () => void, client: QueryClient) {
  return (
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter>
          <EventSignupModal event={ev} isOpen={isOpen} onClose={onClose} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>
  )
}

function renderModal(onClose: () => void) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter>
          <EventSignupModal event={event()} isOpen onClose={onClose} />
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

/* The twin of the guard in SlotDetailModal, and the reason it is tested twice: the slot/event pair
   has form — a fix lands in one copy and the other keeps the bug until somebody reports it. */
describe('EventSignupModal — closing on top of unfinished work', () => {
  it('asks before Escape throws away a comment written with the booking', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderModal(onClose)

    await user.type(await screen.findByPlaceholderText('event.commentPlaceholder'), 'Dojadę sam')
    await user.keyboard('{Escape}')

    expect(screen.getByText('unsaved.title')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()

    await user.click(screen.getByText('unsaved.discard'))
    expect(onClose).toHaveBeenCalled()
  })

  it('closes straight away when nothing has been typed', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderModal(onClose)

    await screen.findByPlaceholderText('event.commentPlaceholder')
    await user.keyboard('{Escape}')

    expect(screen.queryByText('unsaved.title')).not.toBeInTheDocument()
    expect(onClose).toHaveBeenCalled()
  })
})

describe('EventSignupModal — reopened on a different event', () => {
  it('does not carry a comment from one event onto the next', async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const onClose = vi.fn()

    // `MyReservationsPage` mounts this with no remount key, so the instance outlives the event
    const { rerender } = render(tree(event(), true, onClose, client))
    await user.type(await screen.findByPlaceholderText('event.commentPlaceholder'), 'Dojadę sam')
    rerender(tree(event(), false, onClose, client))
    rerender(tree(event({ id: 'event-2', title: 'Inny wyjazd' }), true, onClose, client))

    expect(await screen.findByPlaceholderText('event.commentPlaceholder')).toHaveValue('')
  })
})
