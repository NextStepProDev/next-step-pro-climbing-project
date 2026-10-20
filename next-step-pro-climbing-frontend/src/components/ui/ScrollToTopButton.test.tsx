import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ScrollToTopButton } from './ScrollToTopButton'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

describe('ScrollToTopButton', () => {
  it('stays out of the Tab order until it is shown', () => {
    render(<ScrollToTopButton />)
    const button = screen.getByLabelText('a11y.scrollToTop', { selector: 'button' })
    expect(button).toHaveAttribute('tabindex', '-1')

    Object.defineProperty(window, 'scrollY', { value: 500, configurable: true })
    fireEvent.scroll(window)

    expect(button).toHaveAttribute('tabindex', '0')
  })
})
