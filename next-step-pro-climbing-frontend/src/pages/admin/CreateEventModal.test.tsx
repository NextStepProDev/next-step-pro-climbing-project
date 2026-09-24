import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../context/ToastContext'
import { CreateEventModal } from './AdminEventsPanel'

// The time pickers scroll their columns on mount; jsdom has no Element.scrollTo.
Element.prototype.scrollTo = vi.fn()

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

const createEvent = vi.fn()
const notifyEventInvites = vi.fn()

vi.mock('../../api/client', () => ({
  adminApi: {
    createEvent: (data: unknown) => createEvent(data),
    notifyEventInvites: (eventId: string) => notifyEventInvites(eventId),
    getAllUsers: () => Promise.resolve([]),
    getAllEvents: () => Promise.resolve([]),
  },
  adminCoursesApi: { getAll: () => Promise.resolve([]) },
  calendarApi: { getDayView: (date: string) => Promise.resolve({ date, slots: [], events: [] }) },
}))

const requester = { userId: 'user-1', fullName: 'Ala Nowak', email: 'ala@example.com' }
const proposal = {
  startDate: '2030-06-10',
  endDate: '2030-06-10',
  startTime: '17:00',
  endTime: '19:00',
  maxParticipants: 4,
  invited: [requester],
  trainingRequestId: 'request-1',
}

function renderModal(initial: Parameters<typeof CreateEventModal>[0]['initial'] = proposal) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ToastProvider>
          <CreateEventModal isOpen onClose={vi.fn()} initial={initial} />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/* The twin of CreateSlotModal's guard: answering a proposal with an event at other hours says so,
   and the invitation can go out in the same click. */
describe('CreateEventModal — answering a proposal at other hours', () => {
  beforeEach(() => {
    createEvent.mockReset()
    createEvent.mockResolvedValue({ id: 'event-new' })
    notifyEventInvites.mockReset()
    notifyEventInvites.mockResolvedValue({ notifiedCount: 1, skippedNotificationsOff: 0 })
  })

  const titleField = () => document.querySelector<HTMLInputElement>('input[type="text"][required]')!

  it('should stay quiet while the event keeps the proposed hours', () => {
    renderModal()
    expect(screen.queryByText(/events.proposalMoved/)).not.toBeInTheDocument()
  })

  it('should name the proposal once the event stops being those hours', async () => {
    const user = userEvent.setup()
    renderModal()
    await user.click(screen.getByLabelText('events.allDayCheckbox'))
    expect(screen.getByText(/events.proposalMoved:.*17:00–19:00/)).toBeInTheDocument()
  })

  it('should create the event first and then mail the invitation, in one click', async () => {
    const user = userEvent.setup()
    renderModal()
    fireEvent.change(titleField(), { target: { value: 'Trening' } })

    await user.click(screen.getByRole('button', { name: 'events.createAndSend' }))

    await waitFor(() => expect(notifyEventInvites).toHaveBeenCalledWith('event-new'))
    expect(createEvent.mock.invocationCallOrder[0]).toBeLessThan(notifyEventInvites.mock.invocationCallOrder[0])
  })

  it('should not mail anything on a plain create', async () => {
    const user = userEvent.setup()
    renderModal()
    fireEvent.change(titleField(), { target: { value: 'Trening' } })

    await user.click(screen.getByRole('button', { name: 'events.createEvent' }))

    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(1))
    expect(notifyEventInvites).not.toHaveBeenCalled()
  })

  it('should not offer the mail outside a proposal', () => {
    renderModal({ startDate: '2030-06-10', endDate: '2030-06-10', invited: [requester] })
    expect(screen.queryByRole('button', { name: 'events.createAndSend' })).not.toBeInTheDocument()
  })
})
