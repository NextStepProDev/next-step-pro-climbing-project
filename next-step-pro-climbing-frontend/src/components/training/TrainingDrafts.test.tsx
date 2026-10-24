import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TrainingFormModal } from './TrainingFormModal'
import { CommentThread } from './CommentThread'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import { readDraft } from '../../utils/draftStorage'

/**
 * Drafts on the athlete/coach side: the training form offers one back, the thread composer
 * restores its text by itself. What these screens lose otherwise is a navbar click or iOS
 * dropping the tab — modelled here as an unmount followed by a fresh mount.
 */

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

vi.mock('../../api/client', () => ({
  adminTrainingCalendarApi: { getTemplates: vi.fn(async () => []) },
}))

vi.mock('../../context/AuthContext', () => ({ useCurrentUserId: () => 'u1' }))

const client = () => new QueryClient({ defaultOptions: { queries: { retry: false } } })

function FormHarness({ owner, onSubmit = vi.fn(), submitError = null }: {
  owner: string
  onSubmit?: () => void
  submitError?: string | null
}) {
  const [open, setOpen] = useState(true)
  return (
    <TrainingFormModal
      isOpen={open}
      onClose={() => setOpen(false)}
      initialDate="2026-09-01"
      onSubmit={onSubmit}
      saving={false}
      onUpload={vi.fn()}
      submitError={submitError}
      draftOwner={owner}
    />
  )
}

const renderForm = (owner = 'me', props: { onSubmit?: () => void; submitError?: string | null } = {}) =>
  render(<QueryClientProvider client={client()}><FormHarness owner={owner} {...props} /></QueryClientProvider>)

const titleField = () => screen.getByPlaceholderText('form.titlePlaceholder')

beforeEach(() => localStorage.clear())

describe('TrainingFormModal drafts', () => {
  it('should offer a half-written entry back after the form was torn down', async () => {
    const user = userEvent.setup()
    const first = renderForm()
    await user.type(titleField(), 'Siła — nogi')
    first.unmount()

    renderForm()
    expect(screen.getByText('draft.found')).toBeInTheDocument()
    expect(titleField()).toHaveValue('')

    await user.click(screen.getByRole('button', { name: 'draft.restore' }))

    expect(titleField()).toHaveValue('Siła — nogi')
    expect(screen.queryByText('draft.found')).toBeNull()
  })

  it('should forget the draft when it is discarded', async () => {
    const user = userEvent.setup()
    const first = renderForm()
    await user.type(titleField(), 'Siła')
    first.unmount()

    renderForm()
    await user.click(screen.getByRole('button', { name: 'draft.discard' }))

    expect(readDraft('u1', 'training:new:me')).toBeNull()
  })

  it('should never offer one athlete\'s unsaved entry on another athlete\'s calendar', async () => {
    const user = userEvent.setup()
    const first = renderForm('athlete-a')
    await user.type(titleField(), 'Plan dla A')
    first.unmount()

    renderForm('athlete-b')

    expect(screen.queryByText('draft.found')).toBeNull()
  })

  it('should drop the draft on submit and keep it again when the save fails', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    const view = renderForm('me', { onSubmit })
    await user.type(titleField(), 'Siła')
    await user.click(screen.getByRole('button', { name: 'form.save' }))

    expect(onSubmit).toHaveBeenCalled()
    expect(readDraft('u1', 'training:new:me')).toBeNull()

    view.rerender(
      <QueryClientProvider client={client()}><FormHarness owner="me" onSubmit={onSubmit} submitError="409" /></QueryClientProvider>,
    )
    await waitFor(() => expect(readDraft('u1', 'training:new:me')?.data).toMatchObject({ title: 'Siła' }))
  })
})

describe('CommentThread drafts', () => {
  const api = {
    getComments: vi.fn().mockResolvedValue([]),
    addComment: vi.fn().mockResolvedValue({ id: 'c1', body: 'x', files: [] }),
  } as unknown as TrainingCalendarAdapter

  const renderThread = (id = 't1') =>
    render(
      <QueryClientProvider client={client()}>
        <CommentThread target={{ kind: 'training', id }} api={api} />
      </QueryClientProvider>,
    )

  it('should bring a half-written message back by itself', async () => {
    const user = userEvent.setup()
    const first = renderThread()
    await user.type(screen.getByPlaceholderText('comments.placeholder'), 'Nogi dziś')
    first.unmount()

    renderThread()

    expect(screen.getByPlaceholderText('comments.placeholder')).toHaveValue('Nogi dziś')
  })

  it('should keep each conversation\'s text to itself', async () => {
    const user = userEvent.setup()
    const first = renderThread('t1')
    await user.type(screen.getByPlaceholderText('comments.placeholder'), 'Do t1')
    first.unmount()

    renderThread('t2')

    expect(screen.getByPlaceholderText('comments.placeholder')).toHaveValue('')
  })

  it('should forget the message once it is sent', async () => {
    const user = userEvent.setup()
    renderThread()
    await user.type(screen.getByPlaceholderText('comments.placeholder'), 'Nogi dziś')
    await user.click(screen.getByLabelText('comments.send'))

    await waitFor(() => expect(readDraft('u1', 'comment:training:t1')).toBeNull())
  })
})
