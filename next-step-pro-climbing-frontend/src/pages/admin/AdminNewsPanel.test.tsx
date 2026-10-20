import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { ToastProvider } from '../../context/ToastContext'
import { AdminNewsPanel } from './AdminNewsPanel'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key}:${Object.values(opts).join(',')}` : key),
    i18n: { language: 'pl' },
  }),
}))

const article = {
  id: 'n1', title: 'Artykuł', excerpt: '', language: 'pl', translationGroupId: 'g1',
  published: false, publishedAt: null, thumbnailUrl: null, createdAt: '2026-10-01T10:00:00Z',
}
const addTextBlock = vi.fn()
const updatePublishedAt = vi.fn()
let publishedAt: string | null = null

vi.mock('../../api/client', () => ({
  adminNewsApi: {
    getAll: () => Promise.resolve({ content: [article] }),
    getById: () => Promise.resolve({
      ...article, publishedAt, published: publishedAt !== null, blocks: [], thumbnailFocalPointX: null, thumbnailFocalPointY: null,
    }),
    updatePublishedAt: (id: string, at: string) => { publishedAt = at; return updatePublishedAt(id, at) },
    addTextBlock: (id: string, data: { content: string }) => addTextBlock(id, data),
  },
  adminAssetsApi: { list: () => Promise.resolve([]) },
  galleryApi: { getAlbums: () => Promise.resolve([]), getAlbum: () => Promise.resolve({ photos: [] }) },
}))

beforeEach(() => {
  addTextBlock.mockReset()
  updatePublishedAt.mockReset().mockResolvedValue({})
  publishedAt = null
})

describe('AdminNewsPanel — a save that fails part-way through the new blocks', () => {
  it('does not add the blocks that were already saved a second time', async () => {
    addTextBlock.mockResolvedValueOnce({}).mockRejectedValueOnce(new Error('timeout')).mockResolvedValue({})
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter><ToastProvider><AdminNewsPanel /></ToastProvider></MemoryRouter>
      </QueryClientProvider>,
    )
    await user.click(await screen.findByTitle('news.edit (PL)'))
    await user.click(await screen.findByText('news.addParagraph'))
    await user.click(screen.getByText('news.addParagraph'))
    const boxes = screen.getAllByRole('textbox')
    await user.type(boxes[boxes.length - 2], 'pierwszy')
    await user.type(boxes[boxes.length - 1], 'drugi')

    await user.click(screen.getByText('news.saveDraft'))
    await waitFor(() => expect(addTextBlock).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getByText('news.saveDraft').closest('button')).toBeEnabled())

    await user.click(screen.getByText('news.saveDraft'))
    await waitFor(() => expect(addTextBlock).toHaveBeenCalledTimes(3))
    expect(addTextBlock.mock.calls.map(([, d]) => d.content)).toEqual(['pierwszy', 'drugi', 'drugi'])
  })
})

describe('AdminNewsPanel — the publication date field', () => {
  it('does not write the old date back after "now"', async () => {
    publishedAt = '2026-01-10T09:00:00Z'
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter><ToastProvider><AdminNewsPanel /></ToastProvider></MemoryRouter>
      </QueryClientProvider>,
    )
    await user.click(await screen.findByTitle('news.edit (PL)'))
    await user.click(await screen.findByTitle('news.publishedAtNow'))
    await waitFor(() => expect(updatePublishedAt).toHaveBeenCalledTimes(1))

    // A click in the field and out of it again — no edit. The stale field used to read as one.
    const field = await waitFor(() => {
      const el = document.querySelector<HTMLInputElement>('input[type="datetime-local"]')!
      expect(el.value).not.toBe('2026-01-10T10:00')
      return el
    })
    field.focus()
    field.blur()

    expect(updatePublishedAt).toHaveBeenCalledTimes(1)
  })
})

