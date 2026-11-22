import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ContactForm } from './ContactForm'
import type { ContactContext } from '../../types'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'en' } }),
  Trans: ({ i18nKey }: { i18nKey: string }) => i18nKey,
}))

let authState: { isAuthenticated: boolean; user: unknown } = { isAuthenticated: false, user: null }
vi.mock('../../context/AuthContext', () => ({
  useAuth: () => authState,
}))

const send = vi.fn()
vi.mock('../../api/client', () => ({
  contactApi: { send: (...a: unknown[]) => send(...a) },
}))

function renderForm(context: ContactContext = { type: 'GENERAL' }, onDirtyChange?: (d: boolean) => void) {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ContactForm context={context} onDirtyChange={onDirtyChange} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  send.mockReset()
  send.mockResolvedValue({ inConversation: false })
  authState = { isAuthenticated: false, user: null }
})

describe('ContactForm', () => {
  it('says what a guest still has to fill in instead of greying the button out', async () => {
    const user = userEvent.setup()
    renderForm()

    const submit = screen.getByText('contactForm.send').closest('button')!
    expect(submit).toBeEnabled()
    await user.click(submit)

    expect(screen.getByText('contactForm.errors.name')).toBeInTheDocument()
    expect(screen.getByText('contactForm.errors.email')).toBeInTheDocument()
    expect(screen.getByText('contactForm.errors.body')).toBeInTheDocument()
    expect(screen.getByText('contactForm.errors.consent')).toBeInTheDocument()
    expect(send).not.toHaveBeenCalled()
  })

  it('sends a guest message with the course it was asked from', async () => {
    const user = userEvent.setup()
    renderForm({ type: 'COURSE', courseId: 'c-1', courseTitle: 'Kurs skałkowy' })

    await user.type(screen.getByLabelText('contactForm.name'), 'Ola')
    await user.type(screen.getByLabelText('contactForm.email'), 'ola@example.com')
    await user.type(screen.getByLabelText('contactForm.message'), 'Ile osób w grupie?')
    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByText('contactForm.send'))

    expect(send).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Ola',
      email: 'ola@example.com',
      body: 'Ile osób w grupie?',
      consent: true,
      contextType: 'COURSE',
      courseId: 'c-1',
      date: undefined,
      language: 'en',
    }))
    expect(await screen.findByText('contactForm.sentGuest')).toBeInTheDocument()
  })

  it('asks a signed-in person for the message only, and points to the conversation after', async () => {
    authState = { isAuthenticated: true, user: { firstName: 'Jan', lastName: 'Kowalski', email: 'jan@example.com' } }
    send.mockResolvedValue({ inConversation: true })
    const user = userEvent.setup()
    renderForm({ type: 'CALENDAR', date: '2026-12-14' })

    expect(screen.queryByLabelText('contactForm.name')).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('contactForm.message'), 'Czy ten dzień jest wolny?')
    await user.click(screen.getByText('contactForm.send'))

    expect(send).toHaveBeenCalledWith(expect.objectContaining({
      name: undefined,
      email: undefined,
      contextType: 'CALENDAR',
      date: '2026-12-14',
    }))
    expect(await screen.findByText('contactForm.openConversation')).toBeInTheDocument()
  })

  it('sends no day from the calendar when none was selected, instead of inventing today', async () => {
    authState = { isAuthenticated: true, user: { firstName: 'Jan', lastName: 'K', email: 'jan@example.com' } }
    const user = userEvent.setup()
    renderForm({ type: 'CALENDAR', date: null })

    expect(screen.getByText(/contactForm.about.calendarAny/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('contactForm.message'), 'Wolne terminy w grudniu?')
    await user.click(screen.getByText('contactForm.send'))

    expect(send).toHaveBeenCalledWith(expect.objectContaining({ contextType: 'CALENDAR', date: undefined }))
  })

  it('reports itself dirty to the modal as soon as something is typed', async () => {
    const reports: boolean[] = []
    const user = userEvent.setup()
    renderForm({ type: 'GENERAL' }, (d) => reports.push(d))

    expect(reports.at(-1)).toBe(false)
    await user.type(screen.getByLabelText('contactForm.message'), 'H')
    expect(reports.at(-1)).toBe(true)
  })

  it('keeps the honeypot out of reach of people', () => {
    renderForm()
    const trap = screen.getByLabelText('contactForm.honeypot', { selector: 'input' })
    expect(trap).toHaveAttribute('tabindex', '-1')
    expect(trap.closest('[aria-hidden="true"]')).not.toBeNull()
  })
})
