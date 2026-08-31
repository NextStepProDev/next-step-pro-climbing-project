import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SlotDetailModal } from './SlotDetailModal'
import type { InvitedUser, TimeSlotDetail } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ isAuthenticated: true, isAdmin: true, user: { id: 'admin-1' } }),
}))

// jsdom has no scrollTo, and TimeScrollPicker drives its columns with it on mount.
Element.prototype.scrollTo = Element.prototype.scrollTo ?? (() => {})

vi.mock('../../hooks/useEditSavedToast', () => ({ useEditSavedToast: () => vi.fn() }))
vi.mock('../admin/AdminPrivateNote', () => ({ AdminPrivateNote: () => null }))

const updateTimeSlot = vi.fn().mockResolvedValue({ notifiedCount: 0, hadParticipants: false })
let invitesPromise: Promise<InvitedUser[]>

vi.mock('../../api/client', () => ({
  adminSettlementsApi: {
    getSection: vi.fn().mockResolvedValue({
      targetDate: '2026-08-14', lines: [], payoutSourceId: null, payoutSourceName: null,
    }),
    listSources: vi.fn().mockResolvedValue([]),
    assignSource: vi.fn().mockResolvedValue(undefined),
    save: vi.fn().mockResolvedValue(undefined),
    remove: vi.fn().mockResolvedValue(undefined),
  },
  adminApi: {
    updateTimeSlot: (...args: unknown[]) => updateTimeSlot(...args),
    getSlotInvites: () => invitesPromise,
    getAllUsers: () => Promise.resolve([]),
    getSlotParticipants: () => Promise.resolve([]),
    deleteTimeSlot: () => Promise.resolve(),
    notifySlotInvites: () => Promise.resolve({ notifiedCount: 0 }),
  },
  reservationApi: { createReservation: vi.fn() },
}))

function slot(overrides: Partial<TimeSlotDetail> = {}): TimeSlotDetail {
  return {
    id: 'slot-1',
    date: '2030-06-10',
    startTime: '10:00:00',
    endTime: '11:00:00',
    maxParticipants: 3,
    currentParticipants: 0,
    status: 'AVAILABLE',
    title: 'Trening',
    isUserRegistered: false,
    isAvailabilityWindow: false,
    isUnavailable: false,
    eventId: null,
    reservedSeats: 0,
    isReservedForUser: false,
    ...overrides,
  } as TimeSlotDetail
}

function renderModal(s: TimeSlotDetail = slot()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SlotDetailModal slot={s} isOpen onClose={vi.fn()} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** Open the admin edit form and make it dirty, so "save" is enabled. */
async function openEditAndTypeTitle(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByText('slots.editSlot'))
  await user.type(screen.getByPlaceholderText('slots.titlePlaceholder'), '!')
}

beforeEach(() => {
  updateTimeSlot.mockClear()
  invitesPromise = Promise.resolve([])
})

/* The invitations picker lives here because copy/paste deliberately drops invitations: the copy
   carries the plan, not the people. Without this section the only way to invite someone onto the
   pasted slot was the separate admin panel. */
describe('SlotDetailModal — inviting people onto a slot from the calendar', () => {
  it('should send the invited list once the server baseline has arrived', async () => {
    const user = userEvent.setup()
    invitesPromise = Promise.resolve([
      { userId: 'u-1', fullName: 'Ala Kot', email: 'ala@example.com', notifiedAt: null },
    ])
    renderModal()

    await openEditAndTypeTitle(user)
    // Twice on screen: the picker chip and the "who has been mailed" list below it.
    await waitFor(() => expect(screen.getAllByText(/Ala Kot|ala@example.com/).length).toBeGreaterThan(0))
    await user.click(screen.getByText('slots.saveChanges'))

    await waitFor(() => expect(updateTimeSlot).toHaveBeenCalled())
    expect(updateTimeSlot.mock.calls[0][1]).toMatchObject({ invitedUserIds: ['u-1'] })
  })

  it('should leave the invitations alone when saved before the baseline has loaded', async () => {
    const user = userEvent.setup()
    // A query that never resolves: the admin saves while the list is still in flight.
    invitesPromise = new Promise(() => {})
    renderModal()

    await openEditAndTypeTitle(user)
    await user.click(screen.getByText('slots.saveChanges'))

    await waitFor(() => expect(updateTimeSlot).toHaveBeenCalled())
    // Omitted, not empty — the server reads a missing list as "leave them alone", while []
    // would withdraw every invitation the slot already has.
    expect(updateTimeSlot.mock.calls[0][1]).not.toHaveProperty('invitedUserIds')
  })

  it('should not offer the picker on a slot that belongs to an event', async () => {
    const user = userEvent.setup()
    renderModal(slot({ eventId: 'event-1' }))

    await user.click(screen.getByText('slots.editSlot'))

    expect(screen.queryByText('invites.label')).not.toBeInTheDocument()
    await user.type(screen.getByPlaceholderText('slots.titlePlaceholder'), '!')
    await user.click(screen.getByText('slots.saveChanges'))
    await waitFor(() => expect(updateTimeSlot).toHaveBeenCalled())
    expect(updateTimeSlot.mock.calls[0][1]).toMatchObject({ invitedUserIds: [] })
  })
})
