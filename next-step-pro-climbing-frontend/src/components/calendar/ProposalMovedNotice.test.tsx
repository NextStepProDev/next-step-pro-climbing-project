import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AcceptedAtOtherTimeNotice, InvitationMovedNotice } from './ProposalMovedNotice'
import type { MyInvitation, TrainingRequest } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

vi.mock('../../utils/dateFnsLocale', async () => {
  const { pl } = await import('date-fns/locale')
  return { useDateLocale: () => pl }
})

function request(overrides: Partial<TrainingRequest> = {}): TrainingRequest {
  return {
    id: 'r1',
    requestedDate: '2026-10-14',
    startTime: '17:00:00',
    endTime: '19:00:00',
    participants: 1,
    comment: null,
    status: 'ACCEPTED',
    adminNote: null,
    courseTitle: null,
    createdSlotId: 's1',
    createdSlotDate: '2026-10-14',
    createdEventId: null,
    createdEventStartDate: null,
    agreedDate: '2026-10-14',
    agreedEndDate: null,
    agreedStartTime: '18:00:00',
    agreedEndTime: '20:00:00',
    termChanged: true,
    createdAt: '2026-09-23T10:00:00Z',
    ...overrides,
  }
}

function invitation(overrides: Partial<MyInvitation> = {}): MyInvitation {
  return {
    type: 'SLOT',
    slotId: 's1',
    eventId: null,
    title: null,
    eventType: null,
    date: '2026-10-14',
    endDate: null,
    startTime: '18:00:00',
    endTime: '20:00:00',
    location: null,
    proposedDate: '2026-10-14',
    proposedStartTime: '17:00:00',
    proposedEndTime: '19:00:00',
    ...overrides,
  }
}

describe('AcceptedAtOtherTimeNotice — accepted, but not at the proposed hours', () => {
  it('should name the agreed term and strike through the proposal', () => {
    render(<AcceptedAtOtherTimeNotice request={request()} />)
    expect(screen.getByText('trainingRequests.changedHint')).toBeInTheDocument()
    expect(screen.getByText('śr. 14.10, 18:00–20:00')).toBeInTheDocument()
    expect(screen.getByText('śr. 14.10, 17:00–19:00')).toHaveClass('line-through')
  })

  it('should render nothing when the entry kept the proposed hours', () => {
    const { container } = render(<AcceptedAtOtherTimeNotice request={request({ termChanged: false })} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('should render nothing for a request that is not accepted', () => {
    const { container } = render(<AcceptedAtOtherTimeNotice request={request({ status: 'PENDING' })} />)
    expect(container).toBeEmptyDOMElement()
  })
})

describe('InvitationMovedNotice — last word before "book"', () => {
  it('should quote the proposal the invitation moved away from', () => {
    render(<InvitationMovedNotice invitation={invitation()} />)
    expect(screen.getByText('invitations.moved:śr. 14.10, 17:00–19:00')).toBeInTheDocument()
  })

  it('should render nothing for an invitation that answers no moved proposal', () => {
    const { container } = render(
      <InvitationMovedNotice invitation={invitation({ proposedDate: null, proposedStartTime: null, proposedEndTime: null })} />,
    )
    expect(container).toBeEmptyDOMElement()
  })
})
