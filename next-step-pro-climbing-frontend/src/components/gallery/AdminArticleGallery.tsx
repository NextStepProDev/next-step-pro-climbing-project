import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, Check, Images } from 'lucide-react'
import clsx from 'clsx'
import { adminGalleryApi } from '../../api/client'
import type { AlbumAdmin } from '../../types'
import { Modal } from '../ui/Modal'
import { LoadingSpinner } from '../ui/LoadingSpinner'
import { GalleryGrid } from './ArticleGallery'

const ALBUMS_KEY = ['admin', 'gallery', 'albums'] as const

function useAdminAlbums(enabled = true) {
  return useQuery({
    queryKey: ALBUMS_KEY,
    queryFn: () => adminGalleryApi.getAllAlbums(),
    enabled,
  })
}

/**
 * Picks a whole album for a gallery block. Drafts are listed too, marked as such: an article can
 * be written while its photos are still being sorted, and the block stays hidden until then.
 */
export function AlbumPickerModal({ isOpen, onClose, onSelect, selectedId }: {
  isOpen: boolean
  onClose: () => void
  onSelect: (album: AlbumAdmin) => void
  selectedId?: string | null
}) {
  const { t } = useTranslation('admin')
  const { t: tc } = useTranslation('common')
  const { data: albums, isLoading } = useAdminAlbums(isOpen)

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('news.albumPicker.title')} size="xl">
      <p className="text-sm text-surface-400 mb-4">{t('news.albumPicker.hint')}</p>
      {isLoading ? (
        <div className="flex justify-center py-8"><LoadingSpinner /></div>
      ) : !albums || albums.length === 0 ? (
        <p className="text-center text-surface-400 py-8">{t('news.albumPicker.noAlbums')}</p>
      ) : (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {albums.map((album) => (
            <button
              key={album.id}
              type="button"
              onClick={() => { onSelect(album); onClose() }}
              aria-pressed={album.id === selectedId}
              className={clsx(
                'text-left rounded-lg overflow-hidden border-2 transition-colors bg-surface-800',
                album.id === selectedId ? 'border-primary-500' : 'border-surface-700 hover:border-surface-500',
              )}
            >
              <div className="relative aspect-video bg-surface-700">
                {album.thumbnailUrl ? (
                  <img src={album.thumbnailUrl} alt="" loading="lazy" className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center">
                    <Images className="h-8 w-8 text-surface-500" aria-hidden="true" />
                  </div>
                )}
                {!album.published && (
                  <span className="absolute top-1.5 left-1.5 text-[11px] px-1.5 py-0.5 rounded bg-amber-500/90 text-black font-medium">
                    {t('news.albumPicker.draft')}
                  </span>
                )}
                {album.id === selectedId && (
                  <span className="absolute top-1.5 right-1.5 rounded-full bg-primary-500 p-0.5 text-white">
                    <Check className="h-3.5 w-3.5" aria-hidden="true" />
                  </span>
                )}
              </div>
              <div className="px-2 py-1.5">
                <div className="text-sm font-medium text-surface-100 truncate">{album.name}</div>
                <div className="text-xs text-surface-400">
                  {album.photoCount} {tc('gallery.photo', { count: album.photoCount })}
                </div>
              </div>
            </button>
          ))}
        </div>
      )}
    </Modal>
  )
}

/**
 * The block as the editor and the article preview show it: the same grid the reader gets, read
 * through the admin API so a draft album still shows, with a line saying why a reader would see
 * nothing (draft, empty, deleted).
 */
export function AdminGalleryBlockPreview({ albumId, caption }: { albumId: string | null | undefined; caption?: string | null }) {
  const { t } = useTranslation('admin')
  const { data: albums } = useAdminAlbums()
  const summary = albums?.find((a) => a.id === albumId)

  const { data: album, isLoading, isError } = useQuery({
    queryKey: ['admin', 'gallery', 'album', albumId],
    queryFn: () => adminGalleryApi.getAlbum(albumId!),
    enabled: !!albumId,
    placeholderData: undefined,
    retry: false,
  })

  if (!albumId || isError) {
    return <Notice tone="red">{t('news.albumMissing')}</Notice>
  }
  if (isLoading || !album) {
    return <div className="flex justify-center py-6"><LoadingSpinner /></div>
  }

  return (
    <div className="space-y-2">
      {summary && !summary.published && <Notice tone="amber">{t('news.albumDraftWarning')}</Notice>}
      {album.photos.length === 0 ? (
        <Notice tone="amber">{t('news.albumEmptyWarning', { name: album.name })}</Notice>
      ) : (
        <GalleryGrid
          albumId={album.id}
          name={album.name}
          photos={album.photos}
          caption={caption}
          preview
        />
      )}
    </div>
  )
}

/**
 * For the album delete confirmation: the articles that embed the album and will lose it. The
 * delete is still allowed (the articles stay, the block stops showing) — this only makes sure it
 * does not happen without the admin knowing where.
 */
export function AlbumUsageNotice({ albumId }: { albumId: string }) {
  const { t } = useTranslation('admin')
  const { data: articles, isLoading, isError } = useQuery({
    queryKey: ['admin', 'gallery', 'album', albumId, 'articles'],
    queryFn: () => adminGalleryApi.getAlbumArticles(albumId),
    placeholderData: undefined,
    retry: false,
  })

  if (isLoading) {
    return <p className="text-xs text-surface-500 mt-2">{t('galleryAdmin.albumUsage.checking')}</p>
  }
  if (isError) {
    return <p className="text-xs text-amber-400 mt-2">{t('galleryAdmin.albumUsage.checkFailed')}</p>
  }
  if (!articles || articles.length === 0) return null

  return (
    <div className="mt-3 rounded border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-400">
      <p>{t('galleryAdmin.albumUsage.usedIn', { count: articles.length })}</p>
      <ul className="mt-1 space-y-0.5 text-surface-200">
        {articles.map((a) => (
          <li key={a.newsId} className="truncate">
            • {a.title}{' '}
            <span className="text-surface-400 text-xs uppercase">{a.language}</span>
            {!a.published && <span className="text-surface-400 text-xs"> · {t('news.albumPicker.draft')}</span>}
          </li>
        ))}
      </ul>
    </div>
  )
}

function Notice({ tone, children }: { tone: 'amber' | 'red'; children: React.ReactNode }) {
  return (
    <p className={clsx(
      'flex items-start gap-2 text-sm rounded px-3 py-2 border',
      tone === 'amber'
        ? 'text-amber-400 bg-amber-500/10 border-amber-500/30'
        : 'text-red-400 bg-red-500/10 border-red-500/30',
    )}>
      <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5" aria-hidden="true" />
      <span>{children}</span>
    </p>
  )
}
