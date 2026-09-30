import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { RegisterPage } from './RegisterPage'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  Trans: ({ i18nKey }: { i18nKey: string }) => i18nKey,
  // src/i18n.ts is pulled in transitively and calls this at import time
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../api/auth', () => ({ registerUser: vi.fn(async () => undefined) }))
vi.mock('../hooks/useThemeLogo', () => ({ useThemeLogo: () => 'logo.png' }))
// zxcvbn loads its dictionaries lazily; the meter is decoration here, not the thing under test
vi.mock('../components/ui/PasswordStrengthMeter', () => ({ PasswordStrengthMeter: () => null }))

describe('RegisterPage', () => {
  it('lands on the "check your inbox" screen after the checkmark, not on an empty page', async () => {
    const user = userEvent.setup()
    const { container } = render(<MemoryRouter><RegisterPage /></MemoryRouter>)

    await user.type(container.querySelector('#firstName')!, 'Anna')
    await user.type(container.querySelector('#lastName')!, 'Nowak')
    await user.type(container.querySelector('#email')!, 'anna@example.com')
    await user.type(container.querySelector('#phone')!, '+48123456789')
    await user.type(container.querySelector('#password')!, 'Wspinaczka2026!')
    await user.type(container.querySelector('#confirmPassword')!, 'Wspinaczka2026!')
    await user.click(container.querySelector('form button[type="submit"]')!)

    const { registerUser } = await import('../api/auth')
    expect(registerUser).toHaveBeenCalledTimes(1)

    // The checkmark shows for 1.5 s and hands over to the success screen. It used to hand over to
    // nothing: `showCheckmark` stayed true, so the page kept rendering a checkmark that had already
    // hidden itself — every registration ended on a blank page.
    expect(await screen.findByText('register.successTitle', {}, { timeout: 3000 })).toBeInTheDocument()
  }, 10_000)
})
