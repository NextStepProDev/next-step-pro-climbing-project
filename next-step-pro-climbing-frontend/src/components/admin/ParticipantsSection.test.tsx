import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ParticipantsSection } from './ParticipantsSection'
import type { AdminUser, EventParticipants, Participant, SlotParticipants } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
  // src/i18n.ts is pulled in transitively (utils/errors) and initialises on import
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const getSlotParticipants = vi.fn()
const getEventParticipants = vi.fn()
const getAllUsers = vi.fn()
const addRegisteredParticipantToSlot = vi.fn()
const addRegisteredParticipantToEvent = vi.fn()
const addGuestParticipantToSlot = vi.fn()
const addGuestParticipantToEvent = vi.fn()
const deleteGuestParticipantFromSlot = vi.fn()
const deleteGuestParticipantFromEvent = vi.fn()
const cancelReservationByAdmin = vi.fn()
const cancelEventParticipant = vi.fn()

vi.mock('../../api/client', () => ({
  adminApi: {
    getSlotParticipants: (...a: unknown[]) => getSlotParticipants(...a),
    getEventParticipants: (...a: unknown[]) => getEventParticipants(...a),
    getAllUsers: (...a: unknown[]) => getAllUsers(...a),
    addRegisteredParticipantToSlot: (...a: unknown[]) => addRegisteredParticipantToSlot(...a),
    addRegisteredParticipantToEvent: (...a: unknown[]) => addRegisteredParticipantToEvent(...a),
    addGuestParticipantToSlot: (...a: unknown[]) => addGuestParticipantToSlot(...a),
    addGuestParticipantToEvent: (...a: unknown[]) => addGuestParticipantToEvent(...a),
    deleteGuestParticipantFromSlot: (...a: unknown[]) => deleteGuestParticipantFromSlot(...a),
    deleteGuestParticipantFromEvent: (...a: unknown[]) => deleteGuestParticipantFromEvent(...a),
    cancelReservationByAdmin: (...a: unknown[]) => cancelReservationByAdmin(...a),
    cancelEventParticipant: (...a: unknown[]) => cancelEventParticipant(...a),
  },
}))

function participant(overrides: Partial<Participant> = {}): Participant {
  return {
    reservationId: 'res-1',
    userId: 'user-1',
    fullName: 'Anna Kowalska',
    email: 'anna@example.com',
    phone: '123456789',
    comment: null,
    participants: 1,
    registeredAt: '2026-08-01T10:00:00Z',
    ...overrides,
  }
}

function slotRoster(overrides: Partial<SlotParticipants> = {}): SlotParticipants {
  return {
    slotId: 'target-1',
    date: '2026-08-14',
    startTime: '10:00:00',
    endTime: '11:00:00',
    maxParticipants: 3,
    participants: [],
    guestParticipants: [],
    ...overrides,
  }
}

function eventRoster(overrides: Partial<EventParticipants> = {}): EventParticipants {
  return {
    eventId: 'target-1',
    maxParticipants: 3,
    participants: [],
    guestParticipants: [],
    ...overrides,
  }
}

const ADMIN_USER: AdminUser = {
  id: 'user-1',
  firstName: 'Anna',
  lastName: 'Kowalska',
  email: 'anna@example.com',
  phone: '123456789',
  role: 'USER',
  createdAt: '2026-01-01T00:00:00Z',
  newsletterSubscribed: false,
  isAthlete: false,
  emailVerified: true,
}

function renderSection(target: 'slot' | 'event' = 'slot', canAdd = true) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  const view = render(
    <QueryClientProvider client={client}>
      <ParticipantsSection target={target} targetId="target-1" canAdd={canAdd} />
    </QueryClientProvider>,
  )
  return { ...view, invalidate }
}

/** Open the add form and pick the one account the user list offers. */
async function pickTheAccount(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByText('slots.addParticipant'))
  await user.click(await screen.findByPlaceholderText(/Szukaj/))
  // The picker's option is the only BUTTON carrying the address: the same person may already be
  // on the roster above, where the name is plain text and the only button is a bare trash icon.
  const option = await waitFor(() => {
    const found = screen.getAllByRole('button').find((b) => b.textContent?.includes('anna@example.com'))
    if (!found) throw new Error('the account has not been offered yet')
    return found
  })
  await user.click(option)
}

describe('ParticipantsSection', () => {
  beforeEach(() => {
    getSlotParticipants.mockReset().mockResolvedValue(slotRoster())
    getEventParticipants.mockReset().mockResolvedValue(eventRoster())
    getAllUsers.mockReset().mockResolvedValue([ADMIN_USER])
    addRegisteredParticipantToSlot.mockReset().mockResolvedValue(undefined)
    addRegisteredParticipantToEvent.mockReset().mockResolvedValue(undefined)
    addGuestParticipantToSlot.mockReset().mockResolvedValue(undefined)
    addGuestParticipantToEvent.mockReset().mockResolvedValue(undefined)
    deleteGuestParticipantFromSlot.mockReset().mockResolvedValue(undefined)
    deleteGuestParticipantFromEvent.mockReset().mockResolvedValue(undefined)
    cancelReservationByAdmin.mockReset().mockResolvedValue(undefined)
    cancelEventParticipant.mockReset().mockResolvedValue(undefined)
  })

  it('should count held seats, not rows, in the heading', async () => {
    getSlotParticipants.mockResolvedValue(slotRoster({
      participants: [participant({ participants: 2 })],
      guestParticipants: [{ id: 'g-1', note: 'Jan — tel. 987', participants: 1, createdAt: '2026-08-01T10:00:00Z' }],
    }))

    renderSection()

    // slots.registeredOf gets {count, max}: three seats taken out of three, from two rows.
    expect(await screen.findByText('slots.registeredOf:3,3')).toBeInTheDocument()
    expect(screen.getByText('Jan — tel. 987')).toBeInTheDocument()
  })

  it('should write a registered person down against the slot', async () => {
    const user = userEvent.setup()
    const { invalidate } = renderSection('slot')
    await screen.findByText('slots.noRegisteredShort')

    await pickTheAccount(user)
    await user.click(screen.getByText('slots.addParticipantConfirm'))

    await waitFor(() =>
      expect(addRegisteredParticipantToSlot).toHaveBeenCalledWith('target-1', 'user-1', 1, undefined))
    expect(addRegisteredParticipantToEvent).not.toHaveBeenCalled()
    // The settlement section sits directly below and must learn about the new payer.
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['admin', 'settlements'] })
  })

  it('should write a registered person down against the event instead', async () => {
    const user = userEvent.setup()
    renderSection('event')
    await screen.findByText('slots.noRegisteredShort')

    await pickTheAccount(user)
    await user.click(screen.getByText('slots.addParticipantConfirm'))

    await waitFor(() =>
      expect(addRegisteredParticipantToEvent).toHaveBeenCalledWith('target-1', 'user-1', 1, undefined))
    expect(addRegisteredParticipantToSlot).not.toHaveBeenCalled()
  })

  /* An event ADDS the requested seats to a reservation the person already has, where a slot
     refuses outright. Without saying so, "2" means two to the admin and four to the server. */
  it('should warn that an event adds seats to a booking the person already has', async () => {
    const user = userEvent.setup()
    getEventParticipants.mockResolvedValue(eventRoster({ participants: [participant({ participants: 2 })] }))
    renderSection('event')
    await screen.findByText('Anna Kowalska')

    await pickTheAccount(user)

    expect(await screen.findByText('events.alreadyRegistered:2')).toBeInTheDocument()
  })

  it('should keep that warning off the slot, where the same click is refused', async () => {
    const user = userEvent.setup()
    getSlotParticipants.mockResolvedValue(slotRoster({ participants: [participant({ participants: 2 })] }))
    renderSection('slot')
    await screen.findByText('Anna Kowalska')

    await pickTheAccount(user)

    expect(screen.queryByText('events.alreadyRegistered:2')).not.toBeInTheDocument()
  })

  it('should write a guest down without touching the registered route', async () => {
    const user = userEvent.setup()
    renderSection('slot')
    await screen.findByText('slots.noRegisteredShort')

    await user.click(screen.getByText('slots.addParticipant'))
    await user.click(screen.getByText('slots.addGuest'))
    await user.type(screen.getByPlaceholderText('slots.guestNotePlaceholder'), 'Jan — tel. 987')
    await user.click(screen.getByText('slots.addParticipantConfirm'))

    await waitFor(() =>
      expect(addGuestParticipantToSlot).toHaveBeenCalledWith('target-1', 'Jan — tel. 987', 1))
    expect(addRegisteredParticipantToSlot).not.toHaveBeenCalled()
  })

  /* Removing a booking mails the client that their session is off, so it cannot be one stray tap. */
  it('should ask before cancelling somebody, then cancel their reservation row', async () => {
    const user = userEvent.setup()
    getSlotParticipants.mockResolvedValue(slotRoster({ participants: [participant()] }))
    renderSection('slot')

    await user.click(await screen.findByRole('button', { name: /slots.cancelReservation/ }))
    expect(cancelReservationByAdmin).not.toHaveBeenCalled()
    expect(screen.getByText('slots.confirmCancelReservation')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'slots.cancelReservation' }))

    await waitFor(() => expect(cancelReservationByAdmin).toHaveBeenCalledWith('res-1'))
  })

  /* An event books one row per DAY, so removing somebody is addressed by person, not by row. */
  it('should remove somebody from an event by user, not by reservation', async () => {
    const user = userEvent.setup()
    getEventParticipants.mockResolvedValue(eventRoster({ participants: [participant()] }))
    renderSection('event')

    await user.click(await screen.findByRole('button', { name: /events.cancelParticipant/ }))
    await user.click(screen.getByRole('button', { name: 'events.cancelParticipant' }))

    await waitFor(() => expect(cancelEventParticipant).toHaveBeenCalledWith('target-1', 'user-1'))
    expect(cancelReservationByAdmin).not.toHaveBeenCalled()
  })

  /* A blocked slot has a past worth reading and a write the server refuses. */
  it('should keep the roster but drop the form when nothing can be added', async () => {
    getSlotParticipants.mockResolvedValue(slotRoster({ participants: [participant()] }))

    renderSection('slot', false)

    expect(await screen.findByText('Anna Kowalska')).toBeInTheDocument()
    expect(screen.queryByText('slots.addParticipant')).not.toBeInTheDocument()
  })

  it('should not ask the server for the whole user list until an account is being looked for', async () => {
    const user = userEvent.setup()
    renderSection('slot')
    await screen.findByText('slots.noRegisteredShort')

    expect(getAllUsers).not.toHaveBeenCalled()

    await user.click(screen.getByText('slots.addParticipant'))

    await waitFor(() => expect(getAllUsers).toHaveBeenCalled())
  })
})
