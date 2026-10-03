import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { EventListItem } from './EventListItem'
import type { EventSummary } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

const base: EventSummary = {
  id: 'e1',
  title: 'Kurs skałkowy',
  description: null,
  location: null,
  eventType: 'COURSE',
  startDate: '2026-10-10',
  endDate: '2026-10-10',
  startTime: null,
  endTime: null,
  isMultiDay: false,
  maxParticipants: 8,
  currentParticipants: 6,
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
}

function renderItem(event: EventSummary) {
  render(
    <MemoryRouter>
      <EventListItem event={event} dotClass="bg-primary-400" courseReturnTo="/calendar" onSelect={() => {}} />
    </MemoryRouter>,
  )
}

describe('EventListItem', () => {
  it('should offer the waitlist when other people\'s invitations hold the remaining seats', () => {
    renderItem({ ...base, reservedSeats: 2 })
    expect(screen.getByText('event.waitlist.join')).toBeInTheDocument()
    expect(screen.queryByText('signUp')).not.toBeInTheDocument()
  })

  it('should offer sign-up to the invited person, whose own held seat is free for them', () => {
    renderItem({ ...base, currentParticipants: 7, reservedSeats: 1, isReservedForUser: true })
    expect(screen.getByText('signUp')).toBeInTheDocument()
  })

  it('should show the phone pill on a contact day, not the sign-up button', () => {
    renderItem({ ...base, eventType: 'CONTACT_DAY', currentParticipants: 0 })
    expect(screen.getByText('common:callPhone')).toBeInTheDocument()
    expect(screen.queryByText('signUp')).not.toBeInTheDocument()
  })
})
