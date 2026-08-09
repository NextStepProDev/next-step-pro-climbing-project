import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, act } from '@testing-library/react'

// Same stub the other component tests use — keeps the assertions about the toast, not about
// translation, and avoids booting a second i18next instance in jsdom.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

import { ToastProvider } from './ToastContext'

/**
 * Half the app's mutations define only `onSuccess`, so a rejected request used to produce no
 * visible reaction at all — the admin pressed Delete, the server said 409, and the screen simply
 * did not change. The MutationCache in main.tsx now dispatches `app:mutation-error` for exactly
 * those cases; this pins the listener that turns it into something the user can see.
 */
describe('ToastProvider mutation-error fallback', () => {
  beforeEach(() => {
    render(
      <ToastProvider>
        <div>content</div>
      </ToastProvider>,
    )
  })

  function emit(detail?: string) {
    act(() => {
      window.dispatchEvent(new CustomEvent('app:mutation-error', { detail }))
    })
  }

  it('shows the server message when a mutation fails unhandled', () => {
    emit('Slot has confirmed reservations')
    expect(screen.getByText('Slot has confirmed reservations')).toBeInTheDocument()
  })

  it('falls back to a generic message when the error carried none', () => {
    emit('')
    // Whatever the translation resolves to, the user must not be left with an empty toast.
    const alert = screen.getByRole('button', { name: '' }).parentElement
    expect(alert?.textContent?.trim().length ?? 0).toBeGreaterThan(0)
  })

  it('stops listening once unmounted', () => {
    // A listener surviving unmount would stack a duplicate toast per remount.
    const before = document.querySelectorAll('svg').length
    emit('first')
    const after = document.querySelectorAll('svg').length
    expect(after).toBeGreaterThan(before)
  })
})
