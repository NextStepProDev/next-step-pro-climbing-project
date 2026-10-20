import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { CompleteProfileModal } from './CompleteProfileModal'
import { NewsletterChoiceModal } from './NewsletterChoiceModal'
import { validatePhone } from '../../utils/validation'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  Trans: ({ i18nKey }: { i18nKey: string }) => i18nKey,
}))

vi.mock('../../context/AuthContext', () => ({
  useAuth: () => ({ user: { firstName: 'Ala', lastName: 'Nowak', phone: '', nickname: 'ala' }, refreshUser: vi.fn() }),
}))

const updateProfile = vi.fn()
const updateNewsletter = vi.fn()
vi.mock('../../api/client', () => ({
  authApi: {
    updateProfile: (...a: unknown[]) => updateProfile(...a),
    updateNewsletter: (s: boolean) => updateNewsletter(s),
  },
}))

beforeEach(() => {
  updateProfile.mockReset()
  updateNewsletter.mockReset()
})

describe('CompleteProfileModal', () => {
  it('says what is wrong instead of greying the button out', async () => {
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <CompleteProfileModal onCompleted={vi.fn()} onClose={vi.fn()} />
      </QueryClientProvider>,
    )
    await user.type(screen.getByPlaceholderText('profile.phonePlaceholder'), '123')

    const submit = screen.getByText('completeProfile.submit').closest('button')!
    expect(submit).toBeEnabled()
    await user.click(submit)

    expect(screen.getByText(validatePhone('123')!)).toBeInTheDocument()
    expect(updateProfile).not.toHaveBeenCalled()
  })
})

describe('NewsletterChoiceModal', () => {
  it('says so when the choice could not be saved', async () => {
    updateNewsletter.mockRejectedValue(new Error('offline'))
    const user = userEvent.setup()
    render(<MemoryRouter><NewsletterChoiceModal onDone={vi.fn()} /></MemoryRouter>)

    await user.click(screen.getByText('newsletter.modalNo'))

    expect(await screen.findByRole('alert')).toHaveTextContent('newsletter.modalError')
  })
})
