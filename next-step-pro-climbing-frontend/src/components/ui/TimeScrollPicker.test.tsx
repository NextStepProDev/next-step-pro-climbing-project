import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TimeScrollPicker } from './TimeScrollPicker'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

Element.prototype.scrollTo = Element.prototype.scrollTo ?? (() => {})

describe('TimeScrollPicker', () => {
  // The columns answered clicks and scroll wheels only — no focus, no keys — so without a mouse or
  // a finger no time could be set at all, the client's "propose a time" form included.
  it('can be set from the keyboard', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<TimeScrollPicker label="Od" value="10:00" onChange={onChange} />)

    const hour = screen.getByRole('spinbutton', { name: /timePicker.hour/ })
    hour.focus()
    await user.keyboard('{ArrowUp}')
    expect(onChange).toHaveBeenLastCalledWith('11:00')

    const minute = screen.getByRole('spinbutton', { name: /timePicker.minute/ })
    minute.focus()
    await user.keyboard('{ArrowUp}')
    expect(onChange).toHaveBeenLastCalledWith('10:05')
  })

  // A stored :58 rounded up to 60, a minute the column does not have: nothing showed as selected
  // and changing the hour produced "11:60".
  it('never produces a minute past 55', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<TimeScrollPicker label="Od" value="10:58" onChange={onChange} />)

    screen.getByRole('spinbutton', { name: /timePicker.hour/ }).focus()
    await user.keyboard('{ArrowUp}')

    expect(onChange).toHaveBeenLastCalledWith('11:55')
  })
})
