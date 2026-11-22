import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import type { AlbumDetail, Photo } from '../../types'
import { ArticleGalleryBlock, GalleryGrid } from './ArticleGallery'

vi.mock('react-i18next', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-i18next')>()),
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => (opts ? `${key}:${Object.values(opts).join(',')}` : key),
    i18n: { language: 'pl' },
  }),
}))

const getAlbum = vi.fn<(id: string) => Promise<AlbumDetail>>()
// A refusal goes around the spy: a vi.fn whose promise rejects is reported as the test's own
// failure, though React Query handles the rejection (a plain function is not).
let refuseAlbum = false
vi.mock('../../api/client', () => ({
  galleryApi: {
    getAlbum: (id: string) => {
      const result = getAlbum(id)
      return refuseAlbum ? Promise.reject(new Error('404')) : result
    },
  },
}))

function photos(n: number): Photo[] {
  return Array.from({ length: n }, (_, i) => ({
    id: `p${i}`, url: `/api/files/gallery/p${i}.jpg`, caption: null,
    focalPointX: null, focalPointY: null, createdAt: '2026-11-01T10:00:00Z',
  }))
}

function renderWithProviders(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const result = render(
    <QueryClientProvider client={client}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...result, client }
}

/**
 * An empty block is also what a block still loading looks like, so "renders nothing" means
 * something only once the album request has settled.
 */
async function albumSettled(client: QueryClient, albumId: string) {
  await waitFor(() => expect(client.getQueryState(['gallery', 'album', albumId])?.status).not.toBe('pending'))
}

describe('GalleryGrid', () => {
  it('should show two rows and say how many more photos the album holds', () => {
    renderWithProviders(<GalleryGrid albumId="a1" name="Frankenjura" photos={photos(32)} />)

    expect(screen.getAllByRole('button')).toHaveLength(8)
    // The last tile is covered, so it counts itself among the ones still to see.
    expect(screen.getByText('+25')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /gallery.inArticle.openAlbum/ })).toHaveAttribute('href', '/galeria/a1')
  })

  it('should open the whole album in the lightbox, not only the thumbnails', async () => {
    renderWithProviders(<GalleryGrid albumId="a1" name="Frankenjura" photos={photos(32)} />)

    await userEvent.click(screen.getByRole('button', { name: 'gallery.inArticle.showAll:32' }))

    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeInTheDocument()
    expect(dialog.textContent).toContain('8 / 32')
  })

  it('should neither link out nor open a lightbox in the editor preview', () => {
    renderWithProviders(<GalleryGrid albumId="a1" name="Frankenjura" photos={photos(5)} preview />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })
})

describe('ArticleGalleryBlock', () => {
  beforeEach(() => {
    getAlbum.mockReset()
    refuseAlbum = false
  })

  it('should render the album it points at', async () => {
    getAlbum.mockResolvedValue({ id: 'a1', name: 'Obóz', description: null, photos: photos(3), createdAt: '', updatedAt: '' })

    renderWithProviders(<ArticleGalleryBlock albumId="a1" caption="Dzień drugi" />)

    expect(await screen.findByText('Obóz')).toBeInTheDocument()
    expect(screen.getByText('Dzień drugi')).toBeInTheDocument()
    expect(getAlbum).toHaveBeenCalledWith('a1')
  })

  it('should leave no trace of an album the reader may not see', async () => {
    // An unpublished or deleted album answers 404 on the public endpoint.
    refuseAlbum = true

    const { container, client } = renderWithProviders(<ArticleGalleryBlock albumId="a1" />)

    await albumSettled(client, 'a1')
    expect(client.getQueryState(['gallery', 'album', 'a1'])?.status).toBe('error')
    expect(container).toBeEmptyDOMElement()
  })

  it('should leave no trace of an empty album', async () => {
    getAlbum.mockResolvedValue({ id: 'a1', name: 'Pusty', description: null, photos: [], createdAt: '', updatedAt: '' })

    const { container, client } = renderWithProviders(<ArticleGalleryBlock albumId="a1" />)

    await albumSettled(client, 'a1')
    expect(container).toBeEmptyDOMElement()
  })
})
