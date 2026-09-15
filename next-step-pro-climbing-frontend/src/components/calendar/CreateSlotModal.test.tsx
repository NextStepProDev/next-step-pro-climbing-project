import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
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

const createTimeSlot = vi.fn().mockResolvedValue({ id: 'slot-new' })
const createEvent = vi.fn().mockResolvedValue({})
const listSources = vi.fn().mockResolvedValue([])
const assignSource = vi.fn().mockResolvedValue(undefined)

vi.mock('../../api/client', () => ({
  adminApi: {
    createTimeSlot: (data: unknown) => createTimeSlot(data),
    createEvent: (data: unknown) => createEvent(data),
    getAllUsers: () => Promise.resolve([]),
  },
  adminSiteApi: {
    getSlotTemplates: () => Promise.resolve([]),
  },
  adminSettlementsApi: {
    listSources: () => listSources(),
    assignSource: (...args: unknown[]) => assignSource(...args),
  },
}))

function renderModal() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      {/* The contractor branch offers a way out to the settlements tab, and a Link needs a router. */}
      <MemoryRouter>
        <CreateSlotModal isOpen onClose={vi.fn()} defaultDate="2030-06-10" />
      </MemoryRouter>
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
    assignSource.mockClear()
    listSources.mockResolvedValue([])
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

describe('CreateSlotModal — a session somebody else settles', () => {
  const chooseContractor = (user: ReturnType<typeof userEvent.setup>) =>
    user.click(screen.getByRole('radio', { name: 'slotKind.CONTRACTOR' }))

  beforeEach(() => {
    createTimeSlot.mockClear()
    createEvent.mockClear()
    assignSource.mockClear()
    listSources.mockResolvedValue([{ id: 'source-1', name: 'SP nr 5', archived: false }])
  })

  it('should name the payer in the same breath as creating the session', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseContractor(user)
    await user.selectOptions(await screen.findByLabelText('createSlot.contractor'), 'source-1')
    await submit(user)

    await waitFor(() => expect(createTimeSlot).toHaveBeenCalledTimes(1))
    // Zero seats: nobody can book work already sold, and once the session is over that zero is
    // the only thing separating it from an hour nobody took up.
    expect(createTimeSlot.mock.calls[0][0]).toMatchObject({
      maxParticipants: 0,
      isUnavailable: false,
      isAvailabilityWindow: false,
    })
    // ⚠️ Assigned to the row that was just created, not to some id the form guessed — this is the
    // half that keeps the session out of the "no payer" queue in the first place.
    await waitFor(() => expect(assignSource).toHaveBeenCalledWith('slot', 'slot-new', 'source-1', null))
  })

  it('should refuse to create a contractor session with nobody to bill', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseContractor(user)
    await submit(user)

    expect(createTimeSlot).not.toHaveBeenCalled()
    expect(assignSource).not.toHaveBeenCalled()
  })

  it('should forget the payer when the kind changes back', async () => {
    const user = userEvent.setup()
    renderModal()

    await chooseContractor(user)
    await user.selectOptions(await screen.findByLabelText('createSlot.contractor'), 'source-1')
    await user.click(screen.getByRole('radio', { name: 'slotKind.REGULAR' }))
    await submit(user)

    await waitFor(() => expect(createTimeSlot).toHaveBeenCalledTimes(1))
    // A payer left behind a hidden field would file an ordinary slot under a school.
    expect(assignSource).not.toHaveBeenCalled()
  })

  it('should point at where contractors are made rather than offering an empty list', async () => {
    listSources.mockResolvedValue([])
    const user = userEvent.setup()
    renderModal()

    await chooseContractor(user)

    // An empty required dropdown is a tile that cannot be used and does not say why.
    expect(await screen.findByText('createSlot.contractorNone')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'createSlot.contractorManage' }))
      .toHaveAttribute('href', '/admin/settlements')
    expect(screen.queryByLabelText('createSlot.contractor')).not.toBeInTheDocument()
  })

  it('should not offer the contractor tile while answering somebody’s proposal', () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <CreateSlotModal
            isOpen
            onClose={vi.fn()}
            defaultDate="2030-06-10"
            initial={{ trainingRequestId: 'request-1' }}
          />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    // Creating the slot marks the proposal ACCEPTED, and a contractor session has no seats and
    // drops the invitation — so the client would be told "accepted" with nowhere to sit, and the
    // request already spent.
    expect(screen.queryByRole('radio', { name: 'slotKind.CONTRACTOR' })).not.toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'slotKind.REGULAR' })).toBeInTheDocument()
  })

  it('should keep an archived contractor out of the list', async () => {
    listSources.mockResolvedValue([
      { id: 'source-1', name: 'SP nr 5', archived: false },
      { id: 'source-2', name: 'Klub, który już nie współpracuje', archived: true },
    ])
    const user = userEvent.setup()
    renderModal()

    await chooseContractor(user)

    // Archived payers exist so old money keeps a name, not so new work can be filed under a
    // collaboration that ended — the same rule the settlement section's picker follows.
    const options = await screen.findAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual([
      'createSlot.contractorPlaceholder',
      'SP nr 5',
    ])
  })
})
