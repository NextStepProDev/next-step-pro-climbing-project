import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { DayAgendaPreview, DayAgendaSummary } from './DayAgendaPreview'
import type { DayView, TimeSlot } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

const getDayView = vi.fn<(date: string) => Promise<DayView>>()

vi.mock('../../api/client', () => ({
  calendarApi: { getDayView: (date: string) => getDayView(date) },
}))

const groupSlot: TimeSlot = {
  id: 's1',
  startTime: '17:30:00',
  endTime: '19:00:00',
  maxParticipants: 6,
  currentParticipants: 3,
  status: 'AVAILABLE',
  isUserRegistered: false,
  eventTitle: 'Trening grupowy',
  isAvailabilityWindow: false,
  isUnavailable: false,
  reservedSeats: 0,
  isReservedForUser: false,
}

function wrap(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const utils = render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
  return {
    ...utils,
    rerenderWith: (next: React.ReactElement) =>
      utils.rerender(<QueryClientProvider client={client}>{next}</QueryClientProvider>),
  }
}

describe('DayAgendaPreview — the day a proposal falls on, inside the form', () => {
  beforeEach(() => {
    getDayView.mockReset()
    getDayView.mockImplementation((date) => Promise.resolve({ date, slots: [groupSlot], events: [] }))
  })

  it('should flag the entry in the way, and clear the flag once the hours move past it', async () => {
    const { rerenderWith } = wrap(
      <DayAgendaPreview date="2030-06-10" proposed={{ start: '17:00', end: '19:00' }} />,
    )
    expect(await screen.findByText('Trening grupowy')).toBeInTheDocument()
    expect(screen.getByText('dayAgenda.conflict')).toBeInTheDocument()

    rerenderWith(<DayAgendaPreview date="2030-06-10" proposed={{ start: '19:00', end: '20:00' }} />)
    expect(screen.queryByText('dayAgenda.conflict')).not.toBeInTheDocument()
    expect(screen.getByText('dayAgenda.proposed')).toBeInTheDocument()
  })

  it('should put the proposed row between the entries, where its hours fall', async () => {
    getDayView.mockImplementation((date) => Promise.resolve({
      date,
      slots: [
        { ...groupSlot, id: 'late', startTime: '18:00:00', endTime: '19:00:00', eventTitle: 'Wieczór' },
        { ...groupSlot, id: 'early', startTime: '08:00:00', endTime: '09:00:00', eventTitle: 'Rano' },
      ],
      events: [],
    }))
    wrap(<DayAgendaPreview date="2030-06-10" proposed={{ start: '17:00', end: '18:00' }} />)
    await screen.findByText('Rano')
    const rows = screen.getAllByRole('listitem').map((li) => li.textContent)
    expect(rows).toEqual([
      expect.stringContaining('Rano'),
      expect.stringContaining('dayAgenda.proposed'),
      expect.stringContaining('Wieczór'),
    ])
  })

  it('should neither query nor throw while the date field is empty', () => {
    wrap(<DayAgendaPreview date="" proposed={{ start: '17:00', end: '19:00' }} />)
    expect(getDayView).not.toHaveBeenCalled()
    expect(screen.queryByText('dayAgenda.title')).not.toBeInTheDocument()
  })

  it('should say the day could not be read, never that it is free', async () => {
    getDayView.mockRejectedValue(new Error('boom'))
    wrap(<DayAgendaPreview date="2030-06-10" proposed={{ start: '17:00', end: '19:00' }} />)
    expect(await screen.findByText('dayAgenda.error')).toBeInTheDocument()
    expect(screen.queryByText('dayAgenda.empty')).not.toBeInTheDocument()
  })

  it('should open the day in a new tab, so the half-filled form survives', async () => {
    wrap(<DayAgendaPreview date="2030-06-10" proposed={null} />)
    const link = await screen.findByRole('link', { name: /dayAgenda.openDay/ })
    expect(link).toHaveAttribute('href', '/calendar?date=2030-06-10')
    expect(link).toHaveAttribute('target', '_blank')
  })
})

describe('DayAgendaSummary — the verdict on the request card', () => {
  beforeEach(() => {
    getDayView.mockReset()
    getDayView.mockImplementation((date) => Promise.resolve({ date, slots: [groupSlot], events: [] }))
  })

  it('should name what collides', async () => {
    wrap(<DayAgendaSummary date="2030-06-10" proposed={{ start: '17:00', end: '19:00' }} />)
    expect(await screen.findByText('dayAgenda.summaryConflict:17:30–19:00 Trening grupowy (3/6)')).toBeInTheDocument()
  })

  it('should name the first collision and count the rest', async () => {
    getDayView.mockImplementation((date) => Promise.resolve({
      date,
      slots: [groupSlot, { ...groupSlot, id: 's2', startTime: '18:00:00', endTime: '20:00:00', eventTitle: 'Drugi' }],
      events: [],
    }))
    wrap(<DayAgendaSummary date="2030-06-10" proposed={{ start: '17:00', end: '19:00' }} />)
    expect(await screen.findByText('dayAgenda.summaryConflictMore:17:30–19:00 Trening grupowy (3/6),1')).toBeInTheDocument()
  })

  it('should call the hours free and still count the rest of the day', async () => {
    wrap(<DayAgendaSummary date="2030-06-10" proposed={{ start: '08:00', end: '09:00' }} />)
    expect(await screen.findByText('dayAgenda.summaryFreeOthers:1')).toBeInTheDocument()
  })
})
