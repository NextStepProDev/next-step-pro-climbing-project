import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingConsentGate } from './TrainingConsentGate'
import { trainingCalendarApi } from '../../api/client'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: { returnObjects?: boolean }) =>
      (opts?.returnObjects ? ['waga', 'RPE'] : key) as never,
    i18n: { language: 'pl' },
  }),
  Trans: ({ i18nKey }: { i18nKey: string }) => <>{i18nKey}</>,
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/client', () => ({
  trainingCalendarApi: { acceptConsent: vi.fn() },
}))

const onAccepted = vi.fn()

function renderGate() {
  return render(
    <MemoryRouter>
      <TrainingConsentGate onAccepted={onAccepted} />
    </MemoryRouter>,
  )
}

describe('TrainingConsentGate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    onAccepted.mockResolvedValue(undefined)
    vi.mocked(trainingCalendarApi.acceptConsent).mockResolvedValue(undefined)
  })

  it('should keep the accept button disabled until the box is ticked', async () => {
    renderGate()

    // Explicit consent means a deliberate tick — never a pre-ticked box or a one-click accept
    expect(screen.getByRole('checkbox')).not.toBeChecked()
    expect(screen.getByRole('button', { name: 'consent.accept' })).toBeDisabled()

    await userEvent.click(screen.getByRole('checkbox'))

    expect(screen.getByRole('button', { name: 'consent.accept' })).toBeEnabled()
  })

  it('should record consent and refresh the profile when accepted', async () => {
    renderGate()

    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'consent.accept' }))

    await waitFor(() => expect(trainingCalendarApi.acceptConsent).toHaveBeenCalledTimes(1))
    expect(onAccepted).toHaveBeenCalledTimes(1)
  })

  it('should link to the training-calendar section of the privacy policy', () => {
    renderGate()

    // Consent given without a way to read what it covers is not informed consent
    const links = screen.getAllByRole('link')
    expect(links.length).toBeGreaterThan(0)
    links.forEach((link) =>
      expect(link).toHaveAttribute('href', '/polityka-prywatnosci#kalendarz-treningowy'))
  })

  it('should surface a failure instead of pretending consent was stored', async () => {
    vi.mocked(trainingCalendarApi.acceptConsent).mockRejectedValue(new Error('offline'))
    renderGate()

    await userEvent.click(screen.getByRole('checkbox'))
    await userEvent.click(screen.getByRole('button', { name: 'consent.accept' }))

    await waitFor(() => expect(screen.getByText('offline')).toBeInTheDocument())
    expect(onAccepted).not.toHaveBeenCalled()
  })
})
