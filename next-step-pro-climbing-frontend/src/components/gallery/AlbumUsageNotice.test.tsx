import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { AlbumArticle } from '../../types'
import { AlbumUsageNotice } from './AdminArticleGallery'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key}:${Object.values(opts).join(',')}` : key),
    i18n: { language: 'pl' },
  }),
}))

let articles: AlbumArticle[] = []
let refuse = false
vi.mock('../../api/client', () => ({
  adminGalleryApi: {
    // A plain function, not a vi.fn: a spy whose promise rejects fails the test by itself.
    getAlbumArticles: () => (refuse ? Promise.reject(new Error('offline')) : Promise.resolve(articles)),
  },
}))

function renderNotice() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const result = render(
    <QueryClientProvider client={client}><AlbumUsageNotice albumId="a1" /></QueryClientProvider>,
  )
  return { ...result, client }
}

describe('AlbumUsageNotice', () => {
  beforeEach(() => {
    articles = []
    refuse = false
  })

  it('should name every article that will lose the album, drafts marked', async () => {
    articles = [
      { newsId: 'n1', title: 'Camp report', language: 'en', published: true },
      { newsId: 'n2', title: 'Relacja z obozu', language: 'pl', published: false },
    ]

    renderNotice()

    expect(await screen.findByText('galleryAdmin.albumUsage.usedIn:2')).toBeInTheDocument()
    expect(screen.getByText(/Camp report/)).toBeInTheDocument()
    const draft = screen.getByText(/Relacja z obozu/).closest('li')!
    expect(draft.textContent).toContain('news.albumPicker.draft')
  })

  it('should say nothing when no article embeds the album', async () => {
    const { container, client } = renderNotice()

    await waitFor(() =>
      expect(client.getQueryState(['admin', 'gallery', 'album', 'a1', 'articles'])?.status).toBe('success'))
    expect(container).toBeEmptyDOMElement()
  })

  it('should not pass a failed check off as "used nowhere"', async () => {
    refuse = true

    renderNotice()

    expect(await screen.findByText('galleryAdmin.albumUsage.checkFailed')).toBeInTheDocument()
  })
})
