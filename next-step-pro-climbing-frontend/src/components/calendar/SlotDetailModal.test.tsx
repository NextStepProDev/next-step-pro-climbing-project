import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { SlotDetailModal } from './SlotDetailModal'
import { ToastProvider } from '../../context/ToastContext'
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
vi.mock('../../hooks/useInviteSentToast', () => ({ useInviteSentToast: () => vi.fn() }))
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
    // The full DTO shape, not a bare array: the participants section reads `.participants` and
    // `.guestParticipants` off it, and an array would blow up the whole modal on render.
    getSlotParticipants: () => Promise.resolve({
      slotId: 'slot-1',
      date: '2030-06-10',
      startTime: '10:00:00',
      endTime: '11:00:00',
      maxParticipants: 3,
      participants: [],
      guestParticipants: [],
    }),
    addRegisteredParticipantToSlot: vi.fn().mockResolvedValue(undefined),
    addGuestParticipantToSlot: vi.fn().mockResolvedValue(undefined),
    deleteGuestParticipantFromSlot: vi.fn().mockResolvedValue(undefined),
    cancelReservationByAdmin: vi.fn().mockResolvedValue(undefined),
    deleteTimeSlot: () => Promise.resolve(),
    notifySlotInvites: () => Promise.resolve({ notifiedCount: 0, skippedNotificationsOff: 0 }),
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

function renderModal(s: TimeSlotDetail = slot(), onClose: () => void = vi.fn()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      {/* The app mounts this above the router (`main.tsx`), and the admin sections inside this
          modal report their results through it — `useToast` throws without it, so leaving it out
          here would only ever mean the harness is less than the app. */}
      <ToastProvider>
        <MemoryRouter>
          <SlotDetailModal slot={s} isOpen onClose={onClose} />
        </MemoryRouter>
      </ToastProvider>
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

/* Every one of these fields used to be one stray click on the backdrop away from being gone, with
   nothing said. The modal carries a booking comment, an admin edit form, a participant form, a
   price list and the owner's private note — five places to be halfway through something. */
describe('SlotDetailModal — closing on top of unfinished work', () => {
  it('asks before Escape throws away a half-typed edit', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderModal(slot(), onClose)

    await openEditAndTypeTitle(user)
    await user.keyboard('{Escape}')

    expect(screen.getByText('unsaved.title')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()

    await user.click(screen.getByText('unsaved.discard'))
    expect(onClose).toHaveBeenCalled()
  })

  it('asks before the backdrop throws away a comment written with the booking', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    const { container } = renderModal(slot(), onClose)

    await user.type(screen.getByPlaceholderText('slot.commentPlaceholder'), 'Będę 10 minut później')
    // The backdrop is the one way out with no button to hang a guard on
    await user.click(container.ownerDocument.querySelector('.absolute.inset-0')!)

    expect(screen.getByText('unsaved.title')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('closes straight away when nothing has been typed', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    renderModal(slot(), onClose)

    // Opening the edit form is not, on its own, work anybody loses — asking there would teach
    // the admin to click past the question, which is how a guard stops working.
    await user.click(screen.getByText('slots.editSlot'))
    await user.keyboard('{Escape}')

    expect(screen.queryByText('unsaved.title')).not.toBeInTheDocument()
    expect(onClose).toHaveBeenCalled()
  })
})

describe('SlotDetailModal — reopened on a different slot', () => {
  it('does not carry a half-typed edit from one slot onto the next', async () => {
    const user = userEvent.setup()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const view = (s: TimeSlotDetail, isOpen: boolean) => (
      <QueryClientProvider client={client}>
        <ToastProvider>
          <MemoryRouter>
            <SlotDetailModal slot={s} isOpen={isOpen} onClose={vi.fn()} />
          </MemoryRouter>
        </ToastProvider>
      </QueryClientProvider>
    )

    // Three of the four call sites mount this without a remount key, so the instance survives
    const { rerender } = render(view(slot(), true))
    await openEditAndTypeTitle(user)
    rerender(view(slot(), false))
    rerender(view(slot({ id: 'slot-2', title: 'Inny trening' }), true))

    // Otherwise the admin is looking at slot 2 with slot 1's title in an already-open form, and
    // Save writes the first slot's hours onto the second.
    expect(screen.queryByPlaceholderText('slots.titlePlaceholder')).not.toBeInTheDocument()
    expect(screen.getByText('slots.editSlot')).toBeInTheDocument()
  })
})
