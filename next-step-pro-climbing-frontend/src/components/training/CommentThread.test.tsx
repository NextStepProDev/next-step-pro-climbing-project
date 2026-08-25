import { describe, it, expect, vi, beforeEach } from 'vitest'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CommentThread } from './CommentThread'
import type { TrainingCalendarAdapter } from './trainingCalendarAdapter'
import type { TrainingCommentItem } from '../../types'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'pl' } }),
  initReactI18next: { type: '3rdParty', init: () => {} },
}))

// The picker converts every image through a canvas; jsdom has no 2d context or codecs, so the
// conversion itself is stubbed. What matters here is which transport the thread picks and what it
// refuses to send at all.
vi.mock('../../utils/imageUtils', async () => {
  const actual = await vi.importActual<typeof import('../../utils/imageUtils')>('../../utils/imageUtils')
  return {
    ...actual,
    toUploadableImage: vi.fn(async () => new File(['jpeg'], 'converted.jpg', { type: 'image/jpeg' })),
  }
})

const addComment = vi.fn()
const addCommentWithFiles = vi.fn()
const deleteCommentFile = vi.fn()
const editComment = vi.fn()

function makeApi(comments: TrainingCommentItem[] = []): TrainingCalendarAdapter {
  return {
    getComments: vi.fn().mockResolvedValue(comments),
    addComment,
    addCommentWithFiles,
    deleteCommentFile,
    editComment,
  } as unknown as TrainingCalendarAdapter
}

function makeComment(overrides: Partial<TrainingCommentItem> = {}): TrainingCommentItem {
  return {
    id: 'c1',
    body: 'Nogi dziś martwe',
    authorIsAdmin: false,
    authorName: 'Anna',
    authorAvatarUrl: null,
    createdAt: '2026-08-01T10:00:00Z',
    editedAt: null,
    mine: true,
    files: [],
    ...overrides,
  }
}

const attachment = {
  id: 'f1',
  url: '/api/training-calendar/comment-files/f1',
  mimeType: 'image/jpeg',
  fileName: 'droga.jpg',
  sizeBytes: 1024,
  width: 400,
  height: 300,
  expiresAt: '2027-08-01T10:00:00Z',
  canDelete: true,
}

function renderThread(api: TrainingCalendarAdapter) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <CommentThread trainingId="t1" api={api} />
    </QueryClientProvider>,
  )
}

const jpegFile = () => new File(['x'], 'droga.jpg', { type: 'image/jpeg' })

beforeEach(() => {
  vi.clearAllMocks()
  addComment.mockResolvedValue({ id: 'c1', body: 'x', files: [] })
  addCommentWithFiles.mockResolvedValue({ id: 'c1', body: null, files: [] })
  deleteCommentFile.mockResolvedValue(undefined)
  editComment.mockResolvedValue(makeComment({ body: 'poprawione' }))
})

describe('CommentThread', () => {
  it('sends a plain message over the JSON endpoint', async () => {
    const user = userEvent.setup()
    renderThread(makeApi())

    await user.type(screen.getByPlaceholderText('comments.placeholder'), 'Nogi dziś martwe')
    await user.click(screen.getByLabelText('comments.send'))

    await waitFor(() => expect(addComment).toHaveBeenCalledWith('t1', 'Nogi dziś martwe'))
    expect(addCommentWithFiles).not.toHaveBeenCalled()
  })

  it('switches to multipart as soon as something is attached', async () => {
    const user = userEvent.setup()
    const { container } = renderThread(makeApi())

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, jpegFile())

    await user.type(screen.getByPlaceholderText('comments.placeholder'), 'Zobacz')
    await user.click(screen.getByLabelText('comments.send'))

    await waitFor(() => expect(addCommentWithFiles).toHaveBeenCalled())
    expect(addComment).not.toHaveBeenCalled()
    const [, body, files] = addCommentWithFiles.mock.calls[0]
    expect(body).toBe('Zobacz')
    expect(files).toHaveLength(1)
  })

  it('lets a file be the whole message', async () => {
    const user = userEvent.setup()
    const { container } = renderThread(makeApi())

    const send = screen.getByLabelText('comments.send')
    expect(send).toBeDisabled()

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, jpegFile())

    // A photo of a route says everything the message needs to say.
    await waitFor(() => expect(send).not.toBeDisabled())
    await user.click(send)
    await waitFor(() => expect(addCommentWithFiles).toHaveBeenCalledWith('t1', null, expect.any(Array)))
  })

  it('refuses a fourth file without calling the API', async () => {
    const user = userEvent.setup()
    const { container } = renderThread(makeApi())
    const input = container.querySelector('input[type="file"]') as HTMLInputElement

    await user.upload(input, [jpegFile(), jpegFile(), jpegFile(), jpegFile()])

    expect(await screen.findByRole('alert')).toHaveTextContent('comments.fileTooMany')
    expect(addCommentWithFiles).not.toHaveBeenCalled()
  })

  it('refuses a type the server would reject anyway', async () => {
    const user = userEvent.setup()
    const { container } = renderThread(makeApi())
    const input = container.querySelector('input[type="file"]') as HTMLInputElement

    // accept="image/*" on the input is a picker filter, not a guarantee — a file can still arrive
    // by drag or from a system dialog that ignores it.
    await user.upload(input, new File(['x'], 'clip.gif', { type: 'image/gif' }))

    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(addCommentWithFiles).not.toHaveBeenCalled()
  })

  it('renders a message that carries no text at all', async () => {
    renderThread(makeApi([makeComment({ body: null, files: [attachment] })]))

    // The expiry is shown so the file disappearing a year from now is never a surprise.
    expect(await screen.findByText('comments.fileExpires')).toBeInTheDocument()
  })

  it('offers the correction only on your own words', async () => {
    renderThread(
      makeApi([
        makeComment({ id: 'c1', mine: true }),
        // Somebody else's message: the coach may take an attachment down, never rewrite what was said.
        makeComment({ id: 'c2', mine: false, authorIsAdmin: true }),
        // Nothing but a photo — a caption would be a new message, not a correction.
        makeComment({ id: 'c3', mine: true, body: null, files: [attachment] }),
      ]),
    )

    await waitFor(() => expect(screen.getAllByLabelText('comments.edit')).toHaveLength(1))
  })

  it('opens the field with the decoded text and saves the correction', async () => {
    const user = userEvent.setup()
    // Stored escaped by the backend; seeding the field raw would show &quot; and escape it twice.
    renderThread(makeApi([makeComment({ body: 'Zrób &quot;3x10&quot;' })]))

    await user.click(await screen.findByLabelText('comments.edit'))

    const field = screen.getByLabelText('comments.edit') as HTMLTextAreaElement
    expect(field.value).toBe('Zrób "3x10"')

    await user.clear(field)
    await user.type(field, 'Zrób "4x8"')
    await user.click(screen.getByText('comments.editSave'))

    await waitFor(() => expect(editComment).toHaveBeenCalledWith('c1', 'Zrób "4x8"'))
  })

  it('sends nothing when the correction is cancelled or emptied', async () => {
    const user = userEvent.setup()
    renderThread(makeApi([makeComment()]))

    await user.click(await screen.findByLabelText('comments.edit'))
    const field = screen.getByLabelText('comments.edit')

    // An edit corrects the text and may never empty it — clearing a message is a different verb.
    await user.clear(field)
    expect(screen.getByText('comments.editSave')).toBeDisabled()

    await user.click(screen.getByText('comments.editCancel'))
    await waitFor(() => expect(screen.getByLabelText('comments.edit')).toBeInstanceOf(HTMLButtonElement))
    expect(editComment).not.toHaveBeenCalled()
  })

  it('keeps Escape from reaching the modal while a message is being corrected', async () => {
    const user = userEvent.setup()
    // Modal closes on a document-level Escape listener. Without stopPropagation one keypress both
    // cancels the edit and slams the whole training modal shut, losing the thread with it.
    const onDocumentEscape = vi.fn()
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') onDocumentEscape() })
    renderThread(makeApi([makeComment()]))

    await user.click(await screen.findByLabelText('comments.edit'))
    await user.keyboard('{Escape}')

    expect(onDocumentEscape).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByLabelText('comments.edit')).toBeInstanceOf(HTMLButtonElement))

    // ...but with no edit open the key must still get through and close the modal.
    await user.keyboard('{Escape}')
    expect(onDocumentEscape).toHaveBeenCalled()
  })

  it('marks a message that was rewritten', async () => {
    renderThread(makeApi([makeComment({ editedAt: '2026-08-01T11:00:00Z' })]))

    // The thread is the only record of what was agreed, so the reader has to see it changed.
    expect(await screen.findByText('comments.edited')).toBeInTheDocument()
  })
})
