import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DayView } from './DayView'
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
  useAuth: () => ({ isAuthenticated: false, isAdmin: false }),
}))

function absence(overrides: Partial<EventSummary> = {}): EventSummary {
  return {
    id: 'absence-1',
    title: 'Wyjazd w Tatry',
    description: null,
    location: null,
    eventType: 'UNAVAILABLE',
    startDate: '2030-06-10',
    endDate: '2030-06-14',
    startTime: '18:00:00',
    endTime: '20:00:00',
    isMultiDay: true,
    maxParticipants: 0,
    currentParticipants: 0,
    isUserRegistered: false,
    enrollmentOpen: false,
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

function renderDay(date: string, event: EventSummary) {
  return render(
    <MemoryRouter>
      <DayView date={date} slots={[]} events={[event]} onBack={vi.fn()} onSlotClick={vi.fn()} />
    </MemoryRouter>,
  )
}

/* The first and last day of a multi-day absence are open for part of the day, and their slots are
   listed right under this card — so a flat "we are unavailable" is a lie about half the screen. */
describe('DayView — how much of the day an absence actually takes', () => {
  it('should name the hour the absence starts on its first day', () => {
    renderDay('2030-06-10', absence())

    expect(screen.getByText('unavailable.fromHour:18:00')).toBeInTheDocument()
  })

  it('should name the hour the absence ends on its last day', () => {
    renderDay('2030-06-14', absence())

    expect(screen.getByText('unavailable.untilHour:20:00')).toBeInTheDocument()
  })

  it('should claim the whole day in between, where the absence really does last all day', () => {
    renderDay('2030-06-12', absence())

    expect(screen.getByText('unavailable.message')).toBeInTheDocument()
  })

  it('should claim the whole day for an absence carrying no hours at all', () => {
    renderDay('2030-06-10', absence({ startTime: null, endTime: null }))

    expect(screen.getByText('unavailable.message')).toBeInTheDocument()
  })

  it('should name both hours when the absence lives inside one day', () => {
    renderDay('2030-06-10', absence({ endDate: '2030-06-10', isMultiDay: false }))

    expect(screen.getByText('unavailable.betweenHours:18:00,20:00')).toBeInTheDocument()
  })
})

/* The button opens the "slot or event?" chooser, so it belongs to the admin and to nobody else. */
describe('DayView — adding an entry', () => {
  const renderWithAdd = (onAddEntry?: () => void) =>
    render(
      <MemoryRouter>
        <DayView
          date="2030-06-10"
          slots={[]}
          events={[]}
          onBack={vi.fn()}
          onSlotClick={vi.fn()}
          onAddEntry={onAddEntry}
        />
      </MemoryRouter>,
    )

  it('should hand the click to the caller rather than opening a form itself', async () => {
    const user = userEvent.setup()
    const onAddEntry = vi.fn()
    renderWithAdd(onAddEntry)

    await user.click(screen.getByRole('button', { name: 'addEntry.buttonTitle' }))

    expect(onAddEntry).toHaveBeenCalledTimes(1)
  })

  it('should not offer the button at all without a handler', () => {
    renderWithAdd(undefined)

    expect(screen.queryByRole('button', { name: 'addEntry.buttonTitle' })).not.toBeInTheDocument()
  })
})
