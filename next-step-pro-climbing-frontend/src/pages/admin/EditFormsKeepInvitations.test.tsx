import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../context/ToastContext'
import { AdminSlotsPanel } from './AdminSlotsPanel'
import { EditEventModal, type EditableEvent } from './AdminEventsPanel'

/*
 * `invitedUserIds` absent = "leave the invitations alone", `[]` = "withdraw them all". Both admin
 * edit forms built the list from `invitesData ?? []`, so a save made before the invitations loaded
 * — or after that request failed — sent [] and silently withdrew every held seat on the term, even
 * when only the title changed. SlotDetailModal had the guard (and the test); these two did not.
 */

Element.prototype.scrollTo = Element.prototype.scrollTo ?? (() => {})

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key}:${Object.values(opts).join(',')}` : key),
    i18n: { language: 'pl' },
  }),
}))

const updateTimeSlot = vi.fn()
const updateEvent = vi.fn()

vi.mock('../../api/client', () => ({
  adminApi: {
    getUpcomingSlots: () => Promise.resolve([{
      id: 'slot-1', date: '2030-06-10', startTime: '10:00:00', endTime: '11:00:00',
      maxParticipants: 4, currentParticipants: 0, title: 'Trening', blocked: false,
      isAvailabilityWindow: false, isUnavailable: false,
    }]),
    // Never answers: the save happens before the invitations are known
    getSlotInvites: () => new Promise(() => {}),
    getEventInvites: () => new Promise(() => {}),
    getAllUsers: () => Promise.resolve([]),
    updateTimeSlot: (id: string, data: unknown) => updateTimeSlot(id, data),
    updateEvent: (id: string, data: unknown) => updateEvent(id, data),
  },
  adminSiteApi: { getSlotTemplates: () => Promise.resolve([]), saveSlotTemplates: vi.fn() },
  adminCoursesApi: { getAll: () => Promise.resolve([]) },
  calendarApi: { getDayView: (date: string) => Promise.resolve({ date, slots: [], events: [] }) },
}))

function wrap(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ToastProvider>{ui}</ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  updateTimeSlot.mockReset().mockResolvedValue({ notifiedCount: 0, hadParticipants: false })
  updateEvent.mockReset().mockResolvedValue({ notifiedCount: 0, hadParticipants: false })
})

describe('admin edit forms — invitations not loaded yet', () => {
  it('saves a slot without touching its invitations', async () => {
    const user = userEvent.setup()
    wrap(<AdminSlotsPanel />)
    await user.click(await screen.findByLabelText('slots.editSlot'))
    await user.type(screen.getByPlaceholderText('slots.titlePlaceholder'), '!')
    await user.click(screen.getByText('slots.saveChanges'))

    await waitFor(() => expect(updateTimeSlot).toHaveBeenCalled())
    expect(updateTimeSlot.mock.calls[0][1]).not.toHaveProperty('invitedUserIds')
  })

  it('saves an event without touching its invitations', async () => {
    const user = userEvent.setup()
    const event: EditableEvent = {
      id: 'event-1', title: 'Kurs', description: '', location: '', eventType: 'COURSE',
      startDate: '2030-06-10', endDate: '2030-06-11', startTime: null, endTime: null,
      maxParticipants: 6, courseId: null,
    }
    wrap(<EditEventModal event={event} isOpen onClose={vi.fn()} />)
    const title = screen.getByDisplayValue('Kurs')
    await user.type(title, '!')
    await user.click(screen.getByText('events.saveChanges'))

    await waitFor(() => expect(updateEvent).toHaveBeenCalled())
    expect(updateEvent.mock.calls[0][1]).not.toHaveProperty('invitedUserIds')
  })
})

describe('admin event forms — their own Cancel button', () => {
  it('asks before throwing away an edited event, like the cross and Escape do', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const event: EditableEvent = {
      id: 'event-2', title: 'Kurs', description: '', location: '', eventType: 'COURSE',
      startDate: '2030-06-10', endDate: '2030-06-11', startTime: null, endTime: null,
      maxParticipants: 6, courseId: null,
    }
    wrap(<EditEventModal event={event} isOpen onClose={onClose} />)
    await user.type(screen.getByDisplayValue('Kurs'), '!')

    await user.click(screen.getByText('events.cancel'))

    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByText('unsaved.title')).toBeInTheDocument()
  })
})

