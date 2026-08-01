import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { addDays, format, subDays } from 'date-fns'
import { WeightPanel } from './WeightPanel'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { WeightSeries } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  // src/i18n.ts is pulled in transitively (utils/errors) and initialises on import
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

function makeSeries(overrides: Partial<WeightSeries> = {}): WeightSeries {
  return {
    entries: [
      { measuredOn: '2026-07-30', weightKg: 71.2, trendKg: 71.2 },
      { measuredOn: '2026-07-31', weightKg: 70.8, trendKg: 71.0 },
      { measuredOn: '2026-08-01', weightKg: 70.6, trendKg: 70.87 },
    ],
    currentTrendKg: 70.87,
    trendSampleCount: 3,
    trendConfirmed: true,
    weeklyChangePercent: -0.4,
    rapidLoss: false,
    latestWeightKg: 70.6,
    latestMeasuredOn: '2026-08-01',
    backfillDays: 120,
    ...overrides,
  }
}

const today = format(new Date(), 'yyyy-MM-dd')
const missedDay = format(subDays(new Date(), 3), 'yyyy-MM-dd')

const saveWeight = vi.fn()
const deleteWeight = vi.fn()

function makeApi(series: WeightSeries, withMutations = true): TrainingCalendarAdapter {
  return {
    getWeights: vi.fn().mockResolvedValue(series),
    ...(withMutations
      ? { weightMutations: { save: saveWeight, remove: deleteWeight } }
      : {}),
  } as unknown as TrainingCalendarAdapter
}

function renderPanel(api: TrainingCalendarAdapter, isCoachView = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <WeightPanel api={api} scopeKey="me" isCoachView={isCoachView} />
    </QueryClientProvider>,
  )
}

describe('WeightPanel', () => {
  beforeEach(() => {
    saveWeight.mockReset()
    saveWeight.mockResolvedValue(makeSeries())
    deleteWeight.mockReset()
    deleteWeight.mockResolvedValue(makeSeries({ entries: [] }))
  })

  it('lets the athlete record a weight', async () => {
    renderPanel(makeApi(makeSeries()))

    const input = await screen.findByLabelText('weight.todayLabel')
    await userEvent.type(input, '70.4')
    await userEvent.click(screen.getByRole('button', { name: 'weight.save' }))

    await waitFor(() => expect(saveWeight).toHaveBeenCalledTimes(1))
    expect(saveWeight.mock.calls[0][0]).toMatchObject({ weightKg: 70.4 })
  })

  it('accepts a comma as the decimal separator', async () => {
    renderPanel(makeApi(makeSeries()))

    const input = await screen.findByLabelText('weight.todayLabel')
    await userEvent.type(input, '70,4')
    await userEvent.click(screen.getByRole('button', { name: 'weight.save' }))

    await waitFor(() => expect(saveWeight).toHaveBeenCalledTimes(1))
    expect(saveWeight.mock.calls[0][0]).toMatchObject({ weightKg: 70.4 })
  })

  it('never offers the coach a way to record somebody else’s weight', async () => {
    renderPanel(makeApi(makeSeries(), false), true)

    await screen.findByText('weight.title')
    expect(screen.queryByLabelText('weight.todayLabel')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'weight.save' })).not.toBeInTheDocument()
  })

  it('shows the rapid-loss warning to the coach only', async () => {
    const series = makeSeries({ rapidLoss: true, weeklyChangePercent: -1.6 })

    const coach = renderPanel(makeApi(series, false), true)
    expect(await screen.findByRole('alert')).toHaveTextContent('weight.rapidLoss')
    coach.unmount()

    renderPanel(makeApi(series), false)
    await screen.findByText('weight.title')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('explains an unconfirmed trend so a met target does not look like a bug', async () => {
    renderPanel(makeApi(makeSeries({ trendSampleCount: 2, trendConfirmed: false })))

    expect(await screen.findByText('weight.trendUnconfirmed')).toBeInTheDocument()
  })

  it('stays silent for a coach looking at an athlete with no readings', async () => {
    const { container } = renderPanel(
      makeApi(makeSeries({ entries: [], currentTrendKg: null, latestWeightKg: null }), false),
      true,
    )

    await waitFor(() => expect(container).toBeEmptyDOMElement())
  })

  it('prompts the athlete when there are no readings yet', async () => {
    renderPanel(makeApi(makeSeries({ entries: [], currentTrendKg: null, latestWeightKg: null })))

    expect(await screen.findByText('weight.emptyAthlete')).toBeInTheDocument()
  })

  // ---------- backfilling a missed day ----------

  it('defaults the day to today, so the daily weigh-in stays one move', async () => {
    renderPanel(makeApi(makeSeries()))

    const date = await screen.findByLabelText('weight.dateLabel')
    expect(date).toHaveValue(today)
  })

  it('sends the chosen day rather than today', async () => {
    renderPanel(makeApi(makeSeries()))

    const date = await screen.findByLabelText('weight.dateLabel')
    await userEvent.clear(date)
    await userEvent.type(date, missedDay)
    await userEvent.type(screen.getByLabelText('weight.todayLabel'), '70.4')
    await userEvent.click(screen.getByRole('button', { name: 'weight.save' }))

    await waitFor(() => expect(saveWeight).toHaveBeenCalledTimes(1))
    expect(saveWeight.mock.calls[0][0]).toEqual({ measuredOn: missedDay, weightKg: 70.4 })
  })

  it('snaps the day back to today after a save', async () => {
    // Otherwise the next morning's weigh-in would silently overwrite last Tuesday
    renderPanel(makeApi(makeSeries()))

    const date = await screen.findByLabelText('weight.dateLabel')
    await userEvent.clear(date)
    await userEvent.type(date, missedDay)
    await userEvent.type(screen.getByLabelText('weight.todayLabel'), '70.4')
    await userEvent.click(screen.getByRole('button', { name: 'weight.save' }))

    await waitFor(() => expect(saveWeight).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(date).toHaveValue(today))
  })

  it('bounds the picker to the past and to what the chart can show', async () => {
    renderPanel(makeApi(makeSeries({ backfillDays: 30 })))

    const date = await screen.findByLabelText('weight.dateLabel')
    expect(date).toHaveAttribute('max', today)
    expect(date).toHaveAttribute('min', format(subDays(new Date(), 29), 'yyyy-MM-dd'))
  })

  it('lets the browser block a future day through the max attribute', async () => {
    renderPanel(makeApi(makeSeries()))

    const date = await screen.findByLabelText('weight.dateLabel')
    await userEvent.type(screen.getByLabelText('weight.todayLabel'), '70.4')
    fireEvent.change(date, { target: { value: format(addDays(new Date(), 1), 'yyyy-MM-dd') } })
    await userEvent.click(screen.getByRole('button', { name: 'weight.save' }))

    // Constraint validation aborts the submit before the handler ever runs
    expect(saveWeight).not.toHaveBeenCalled()
  })

  it('still refuses a future day when max is not enforced', async () => {
    // Real case, not paranoia: where `type="date"` is unsupported the field degrades to a
    // text input and `max` is ignored entirely. Submitting the form directly models that.
    // The server rejects it independently — see AthleteWeightServiceTest.
    const { container } = renderPanel(makeApi(makeSeries()))

    const date = await screen.findByLabelText('weight.dateLabel')
    await userEvent.type(screen.getByLabelText('weight.todayLabel'), '70.4')
    fireEvent.change(date, { target: { value: format(addDays(new Date(), 1), 'yyyy-MM-dd') } })
    fireEvent.submit(container.querySelector('form')!)

    expect(await screen.findByText('weight.futureDate')).toBeInTheDocument()
    expect(saveWeight).not.toHaveBeenCalled()
  })

  // ---------- ranges ----------

  it('asks the server for the default range and nothing wider', async () => {
    const getWeights = vi.fn().mockResolvedValue(makeSeries())
    renderPanel({ getWeights, weightMutations: { save: saveWeight, remove: deleteWeight } } as unknown as TrainingCalendarAdapter)

    await screen.findByText('weight.title')
    expect(getWeights).toHaveBeenCalledWith('RECENT')
  })

  it('refetches from the server when the range changes instead of filtering locally', async () => {
    // A local filter would mean we had already downloaded the wider history anyway
    const getWeights = vi.fn().mockResolvedValue(makeSeries())
    renderPanel({ getWeights, weightMutations: { save: saveWeight, remove: deleteWeight } } as unknown as TrainingCalendarAdapter)

    await userEvent.click(await screen.findByText('weight.range.year'))

    await waitFor(() => expect(getWeights).toHaveBeenCalledWith('YEAR'))
    expect(getWeights.mock.calls.map((c) => c[0])).toEqual(['RECENT', 'YEAR'])
  })

  it('does not show older readings until a wider range is picked', async () => {
    // The server trims; the panel simply shows what came back for the range it asked for
    const older = format(subDays(new Date(), 200), 'yyyy-MM-dd')
    const getWeights = vi.fn().mockImplementation((range: string) =>
      Promise.resolve(
        range === 'RECENT'
          ? makeSeries({ entries: [] })
          : makeSeries({ entries: [{ measuredOn: older, weightKg: 74, trendKg: 74 }] }),
      ),
    )
    renderPanel({ getWeights, weightMutations: { save: saveWeight, remove: deleteWeight } } as unknown as TrainingCalendarAdapter)

    expect(await screen.findByText('weight.emptyAthlete')).toBeInTheDocument()

    await userEvent.click(screen.getByText('weight.range.all'))

    await waitFor(() => expect(getWeights).toHaveBeenCalledWith('ALL'))
    await userEvent.click(await screen.findByText('weight.showTable'))
    expect(screen.getByRole('table')).toHaveTextContent('74,0')
  })

  it('keeps the backfill bound fixed when a wider range is being viewed', async () => {
    // Widening the CHART must not widen what the date picker offers
    const getWeights = vi.fn().mockResolvedValue(makeSeries({ backfillDays: 120 }))
    renderPanel({ getWeights, weightMutations: { save: saveWeight, remove: deleteWeight } } as unknown as TrainingCalendarAdapter)

    await userEvent.click(await screen.findByText('weight.range.all'))

    await waitFor(() => expect(getWeights).toHaveBeenCalledWith('ALL'))
    expect(await screen.findByLabelText('weight.dateLabel')).toHaveAttribute(
      'min',
      format(subDays(new Date(), 119), 'yyyy-MM-dd'),
    )
  })

  // ---------- deleting a phantom reading ----------

  it('lets the athlete delete a reading from the table', async () => {
    // The fix for a wrong DATE: an overwrite cannot help, because there is no real weight
    // for that day to overwrite it with
    renderPanel(makeApi(makeSeries()))

    await userEvent.click(await screen.findByText('weight.showTable'))
    const rows = screen.getAllByTitle('weight.deleteEntry')
    expect(rows).toHaveLength(3)

    await userEvent.click(rows[0])
    await userEvent.click(screen.getByRole('button', { name: /confirm|potwierd|usu|tak/i }))

    await waitFor(() => expect(deleteWeight).toHaveBeenCalledTimes(1))
    // Table renders newest first, so the first bin is the newest reading
    expect(deleteWeight).toHaveBeenCalledWith('2026-08-01')
  })

  it('asks before deleting rather than removing on the first click', async () => {
    renderPanel(makeApi(makeSeries()))

    await userEvent.click(await screen.findByText('weight.showTable'))
    await userEvent.click(screen.getAllByTitle('weight.deleteEntry')[0])

    expect(deleteWeight).not.toHaveBeenCalled()
  })

  it('never offers the coach a delete button', async () => {
    renderPanel(makeApi(makeSeries(), false), true)

    await userEvent.click(await screen.findByText('weight.showTable'))
    expect(screen.queryByTitle('weight.deleteEntry')).not.toBeInTheDocument()
  })

  it('warns that the chosen day already has a reading, so overwriting is deliberate', async () => {
    const existing = format(subDays(new Date(), 1), 'yyyy-MM-dd')
    const series = makeSeries({
      entries: [{ measuredOn: existing, weightKg: 71.2, trendKg: 71.2 }],
    })
    renderPanel(makeApi(series))

    const date = await screen.findByLabelText('weight.dateLabel')
    expect(screen.queryByText('weight.dayAlreadyHasEntry')).not.toBeInTheDocument()

    await userEvent.clear(date)
    await userEvent.type(date, existing)

    expect(await screen.findByText('weight.dayAlreadyHasEntry')).toBeInTheDocument()
  })
})
