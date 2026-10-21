import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RescheduleRequestModal, type RescheduleTarget } from './RescheduleRequestModal'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

const create = vi.fn()
vi.mock('../../api/client', () => ({
  rescheduleApi: { create: (...args: unknown[]) => create(...args) },
}))

// A fixed "today" far before the fixtures, so no test depends on the real clock.
vi.mock('../../utils/calendarDate', async (importOriginal) => {
  const real = await importOriginal<typeof import('../../utils/calendarDate')>()
  return {
    ...real,
    todayInWarsaw: () => '2026-01-01',
    nowInWarsaw: () => new Date(2026, 0, 1, 8, 0),
  }
})

const SLOT: RescheduleTarget = {
  kind: 'slot', reservationId: 'res-1', title: null, date: '2026-11-03', startTime: '18:00:00', endTime: '20:00:00',
}
const COURSE: RescheduleTarget = {
  kind: 'event', eventId: 'ev-1', title: 'Kurs skałkowy',
  startDate: '2026-11-07', endDate: '2026-11-08', startTime: null, endTime: null,
}

function renderModal(target: RescheduleTarget) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <RescheduleRequestModal target={target} onClose={() => {}} />
    </QueryClientProvider>,
  )
}

describe('RescheduleRequestModal', () => {
  beforeEach(() => {
    create.mockReset().mockResolvedValue({ id: 'r-1', message: 'ok' })
  })

  it('starts at the current term and refuses to send it unchanged', () => {
    renderModal(SLOT)

    expect(screen.getByLabelText('reschedule.newDate')).toHaveValue('2026-11-03')
    expect(screen.getByRole('button', { name: 'reschedule.submit' })).toBeDisabled()
    // The booking stands until the coach answers — the form has to say so before anything is sent
    expect(screen.getByText('reschedule.hint')).toBeInTheDocument()
  })

  it('sends the new day with the same hours for a training', async () => {
    renderModal(SLOT)

    fireEvent.change(screen.getByLabelText('reschedule.newDate'), { target: { value: '2026-11-05' } })
    await userEvent.setup().click(screen.getByRole('button', { name: 'reschedule.submit' }))

    await waitFor(() => expect(create).toHaveBeenCalled())
    expect(create.mock.calls[0][0]).toEqual({
      reservationId: 'res-1',
      newDate: '2026-11-05',
      newStartTime: '18:00',
      newEndTime: '20:00',
      comment: undefined,
    })
  })

  it('carries the last day of a course along with the first, keeping its length', async () => {
    renderModal(COURSE)

    fireEvent.change(screen.getByLabelText('reschedule.fromDay'), { target: { value: '2026-11-14' } })
    expect(screen.getByLabelText('reschedule.toDay')).toHaveValue('2026-11-15')
    await userEvent.setup().click(screen.getByRole('button', { name: 'reschedule.submit' }))

    await waitFor(() => expect(create).toHaveBeenCalled())
    // An all-day course sends no hours at all: the server keeps the event's shape
    expect(create.mock.calls[0][0]).toEqual({
      eventId: 'ev-1',
      newDate: '2026-11-14',
      newEndDate: '2026-11-15',
      newStartTime: undefined,
      newEndTime: undefined,
      comment: undefined,
    })
  })
})
