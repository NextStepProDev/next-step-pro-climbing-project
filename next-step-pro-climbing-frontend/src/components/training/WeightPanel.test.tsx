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
    historyDays: 120,
    ...overrides,
  }
}

const today = format(new Date(), 'yyyy-MM-dd')
const missedDay = format(subDays(new Date(), 3), 'yyyy-MM-dd')

const saveWeight = vi.fn()

function makeApi(series: WeightSeries, withMutations = true): TrainingCalendarAdapter {
  return {
    getWeights: vi.fn().mockResolvedValue(series),
    ...(withMutations
      ? { weightMutations: { save: saveWeight, remove: vi.fn() } }
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
    renderPanel(makeApi(makeSeries({ historyDays: 30 })))

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
