import { describe, it, expect, vi } from 'vitest'
import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RichTextEditor } from './RichTextEditor'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

/** The editor is controlled, so a preview of `value` is only honest with a real owner of it. */
function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return <RichTextEditor value={value} onChange={setValue} rows={4} placeholder="pisz" />
}

const previewButton = () => screen.getByRole('button', { pressed: false })
const editButton = () => screen.getByRole('button', { pressed: true })

describe('RichTextEditor — preview', () => {
  it('should start on the writing side, where the markers stay literal', () => {
    render(<Harness initial="**mocno**" />)
    expect(screen.getByPlaceholderText('pisz')).toHaveValue('**mocno**')
    expect(document.querySelector('strong')).toBeNull()
  })

  it('should render the markers once previewing, which is the whole point', async () => {
    const user = userEvent.setup()
    render(<Harness initial={'## Rozgrzewka\n- **mocno**'} />)

    await user.click(previewButton())

    expect(document.querySelector('h4')).toBeInTheDocument()
    expect(document.querySelector('li strong')).toBeInTheDocument()
    // the textarea is replaced, not merely hidden — no second copy to get out of step
    expect(screen.queryByPlaceholderText('pisz')).toBeNull()
  })

  it('should come back to the text unchanged', async () => {
    const user = userEvent.setup()
    render(<Harness initial="**mocno**" />)

    await user.click(previewButton())
    await user.click(editButton())

    expect(screen.getByPlaceholderText('pisz')).toHaveValue('**mocno**')
  })

  it('should show what was just typed, not a stale copy', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(screen.getByPlaceholderText('pisz'))
    await user.keyboard('## Nowy')
    await user.click(previewButton())

    expect(screen.getByText('Nowy')).toBeInTheDocument()
  })

  // Formatting acts on a selection in the textarea; with no textarea there is nothing to act on,
  // and a button that silently does nothing is worse than one that says it cannot
  it('should disable the formatting buttons while previewing', async () => {
    const user = userEvent.setup()
    render(<Harness initial="tekst" />)

    await user.click(previewButton())

    expect(screen.getByLabelText('richText.bold')).toBeDisabled()
    expect(screen.getByLabelText('richText.heading')).toBeDisabled()
  })

  it('should say so rather than show an empty box when there is nothing to preview', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(previewButton())

    expect(screen.getByText('richText.previewEmpty')).toBeInTheDocument()
  })
})
