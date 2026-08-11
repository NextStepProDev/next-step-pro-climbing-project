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

function makeApi(comments: TrainingCommentItem[] = []): TrainingCalendarAdapter {
  return {
    getComments: vi.fn().mockResolvedValue(comments),
    addComment,
    addCommentWithFiles,
    deleteCommentFile,
  } as unknown as TrainingCalendarAdapter
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
    const api = makeApi([
      {
        id: 'c1',
        body: null,
        authorIsAdmin: false,
        authorName: 'Anna',
        authorAvatarUrl: null,
        createdAt: '2026-08-01T10:00:00Z',
        mine: true,
        files: [
          {
            id: 'f1',
            url: '/api/training-calendar/comment-files/f1',
            mimeType: 'image/jpeg',
            fileName: 'droga.jpg',
            sizeBytes: 1024,
            width: 400,
            height: 300,
            expiresAt: '2027-08-01T10:00:00Z',
            canDelete: true,
          },
        ],
      },
    ])
    renderThread(api)

    // The expiry is shown so the file disappearing a year from now is never a surprise.
    expect(await screen.findByText('comments.fileExpires')).toBeInTheDocument()
  })
})
