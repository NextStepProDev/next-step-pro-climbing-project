import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CreateSlotModal } from './CreateSlotModal'

// jsdom implements no Element.scrollTo, and the time picker scrolls its columns to the selected
// value on mount. Nothing here asserts on that scroll — the stub only keeps the form mountable.
Element.prototype.scrollTo = vi.fn()

// The t mock echoes the key, so every label below is queried by its key.
vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts ? `${key}:${Object.values(opts).join(',')}` : key,
    i18n: { language: 'pl' },
  }),
}))

const createTimeSlot = vi.fn().mockResolvedValue({})
const createEvent = vi.fn().mockResolvedValue({})

vi.mock('../../api/client', () => ({
  adminApi: {
    createTimeSlot: (data: unknown) => createTimeSlot(data),
    createEvent: (data: unknown) => createEvent(data),
    getAllUsers: () => Promise.resolve([]),
  },
  adminSiteApi: {
    getSlotTemplates: () => Promise.resolve([]),
  },
}))

function renderModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CreateSlotModal isOpen onClose={vi.fn()} defaultDate="2030-06-10" />
    </QueryClientProvider>,
  )
}

/** The kind picker is a row of radios labelled by their key. */
const chooseUnavailable = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('radio', { name: 'slotKind.UNAVAILABLE' }))

const submit = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: 'createSlot.submit' }))

describe('CreateSlotModal — one form, two kinds of row', () => {
  beforeEach(() => {
    createTimeSlot.mockClear()
    createEvent.mockClear()
  })

  it('should still create a plain slot when the absence lasts hours of one day', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await submit(user)

    await waitFor(() => expect(createTimeSlot).toHaveBeenCalledTimes(1))
    expect(createEvent).not.toHaveBeenCalled()
    expect(createTimeSlot.mock.calls[0][0]).toMatchObject({
      date: '2030-06-10',
      isUnavailable: true,
      isAvailabilityWindow: false,
    })
  })

  it('should create one continuous event when the absence spans several days', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await user.click(screen.getByLabelText('createSlot.multiDay'))
    // Multi-day defaults to whole days; this test is about the timed variant.
    await user.click(screen.getByLabelText('createSlot.wholeDays'))
    const [, endDate] = screen.getAllByDisplayValue('2030-06-10')
    await user.clear(endDate)
    await user.type(endDate, '2030-06-14')
    await submit(user)

    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(1))
    expect(createTimeSlot).not.toHaveBeenCalled()
    expect(createEvent.mock.calls[0][0]).toMatchObject({
      eventType: 'UNAVAILABLE',
      startDate: '2030-06-10',
      endDate: '2030-06-14',
      // Seats and invitations are what an absence is the absence of.
      maxParticipants: 0,
      invitedUserIds: [],
      startTime: '10:00',
      endTime: '11:00',
    })
  })

  it('should drop the hours when the absence covers whole days', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await user.click(screen.getByLabelText('createSlot.multiDay'))
    const [, endDate] = screen.getAllByDisplayValue('2030-06-10')
    await user.clear(endDate)
    await user.type(endDate, '2030-06-14')
    await submit(user)

    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(1))
    const payload = createEvent.mock.calls[0][0]
    expect(payload.startTime).toBeUndefined()
    expect(payload.endTime).toBeUndefined()
  })

  it('should create an all-day event, not a slot, when one whole day is off', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await user.click(screen.getByLabelText('createSlot.wholeDay'))
    await submit(user)

    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(1))
    expect(createTimeSlot).not.toHaveBeenCalled()
    expect(createEvent.mock.calls[0][0]).toMatchObject({
      startDate: '2030-06-10',
      endDate: '2030-06-10',
    })
  })

  it('should refuse a range that ends before it starts', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await user.click(screen.getByLabelText('createSlot.multiDay'))
    const [startDate] = screen.getAllByDisplayValue('2030-06-10')
    // Moving the start past the end must not silently post an inverted range.
    await user.clear(startDate)
    await user.type(startDate, '2030-06-20')
    const [, endDate] = screen.getAllByDisplayValue('2030-06-20')
    await user.clear(endDate)
    await user.type(endDate, '2030-06-01')
    await submit(user)

    expect(screen.getByText('createSlot.endDateAfterStart')).toBeInTheDocument()
    expect(createEvent).not.toHaveBeenCalled()
    expect(createTimeSlot).not.toHaveBeenCalled()
  })

  it('should accept an end time earlier than the start time across a range', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await user.click(screen.getByLabelText('createSlot.multiDay'))
    await user.click(screen.getByLabelText('createSlot.wholeDays'))
    const [, endDate] = screen.getAllByDisplayValue('2030-06-10')
    await user.clear(endDate)
    await user.type(endDate, '2030-06-11')
    // Friday 10:00 → Saturday 08:00 is a night away, not an inverted window. The single-day
    // guard must not fire here.
    expect(screen.queryByText('createSlot.endAfterStart')).not.toBeInTheDocument()
    await submit(user)

    await waitFor(() => expect(createEvent).toHaveBeenCalledTimes(1))
  })

  it('should keep the range out of the request once the kind goes back to a regular slot', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseUnavailable(user)
    await user.click(screen.getByLabelText('createSlot.multiDay'))
    await user.click(screen.getByRole('radio', { name: 'slotKind.REGULAR' }))
    await submit(user)

    await waitFor(() => expect(createTimeSlot).toHaveBeenCalledTimes(1))
    expect(createEvent).not.toHaveBeenCalled()
    expect(createTimeSlot.mock.calls[0][0]).toMatchObject({ isUnavailable: false })
  })
})
