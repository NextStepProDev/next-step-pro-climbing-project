import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { useLeaveGuard } from './useLeaveGuard'
import { UnsentLeaveDialog } from '../components/contact/UnsentMessage'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
}))

function Composer() {
  const [text, setText] = useState('')
  const [tab, setTab] = useState('chat')
  // The flag is read through a getter at click time, as in the hosts
  const guard = useLeaveGuard(() => text.trim() !== '')
  return (
    <div>
      <p>tab:{tab}</p>
      <label htmlFor="msg">msg</label>
      <textarea id="msg" value={text} onChange={(e) => setText(e.target.value)} />
      <button type="button" onClick={() => guard.guard(() => setTab('other'))}>switch</button>
      <Link to="/elsewhere">away</Link>
      <a href="/elsewhere" target="_blank" rel="noreferrer">new tab</a>
      <UnsentLeaveDialog isOpen={guard.asking} onStay={guard.stay} onLeave={guard.leave} />
    </div>
  )
}

function renderComposer() {
  return render(
    <MemoryRouter initialEntries={['/chat']}>
      <Routes>
        <Route path="/chat" element={<Composer />} />
        <Route path="/elsewhere" element={<p>elsewhere page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('useLeaveGuard', () => {
  it('should follow a link without asking when nothing is written', async () => {
    renderComposer()
    await userEvent.click(screen.getByText('away'))
    expect(screen.getByText('elsewhere page')).toBeInTheDocument()
  })

  it('should stop an in-app link while a message is unsent, and stay on "stay"', async () => {
    renderComposer()
    await userEvent.type(screen.getByLabelText('msg'), 'hej')
    await userEvent.click(screen.getByText('away'))

    expect(screen.getByText('unsentMessage.leaveTitle')).toBeInTheDocument()
    expect(screen.queryByText('elsewhere page')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'unsentMessage.stay' }))
    expect(screen.queryByText('unsentMessage.leaveTitle')).not.toBeInTheDocument()
    expect(screen.getByLabelText('msg')).toHaveValue('hej')
  })

  it('should go where the link pointed on "leave"', async () => {
    renderComposer()
    await userEvent.type(screen.getByLabelText('msg'), 'hej')
    await userEvent.click(screen.getByText('away'))
    await userEvent.click(screen.getByRole('button', { name: 'unsentMessage.leave' }))
    expect(screen.getByText('elsewhere page')).toBeInTheDocument()
  })

  it('should ask before a guarded in-page action and run it only on "leave"', async () => {
    renderComposer()
    await userEvent.type(screen.getByLabelText('msg'), 'hej')
    await userEvent.click(screen.getByText('switch'))
    expect(screen.getByText('tab:chat')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'unsentMessage.leave' }))
    expect(screen.getByText('tab:other')).toBeInTheDocument()
  })

  it('should leave a link opened in a new tab alone', async () => {
    renderComposer()
    await userEvent.type(screen.getByLabelText('msg'), 'hej')
    fireEvent.click(screen.getByText('new tab'))
    expect(screen.queryByText('unsentMessage.leaveTitle')).not.toBeInTheDocument()
  })

  it('should ask the browser before unloading only while a message is unsent', async () => {
    renderComposer()
    const clean = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(clean)
    expect(clean.defaultPrevented).toBe(false)

    await userEvent.type(screen.getByLabelText('msg'), 'hej')
    const dirty = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(dirty)
    expect(dirty.defaultPrevented).toBe(true)
  })
})
