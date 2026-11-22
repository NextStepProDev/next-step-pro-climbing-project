import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ArrowRight, Images } from 'lucide-react'
import clsx from 'clsx'
import { galleryApi } from '../../api/client'
import type { Photo } from '../../types'
import { visibleThumbnailCount } from '../../utils/galleryThumbnails'
import { Lightbox } from './Lightbox'

const GRID_COLUMNS: Record<number, string> = {
  1: 'grid-cols-1',
  2: 'grid-cols-2',
  3: 'grid-cols-3',
}

interface GalleryGridProps {
  albumId: string
  name: string
  photos: Photo[]
  caption?: string | null
  /**
   * The editor and the article preview: no link out (it would leave unsaved work) and no
   * lightbox (it sits inside a Modal, whose document-level Escape would close both at once).
   */
  preview?: boolean
}

/** A gallery block: a few thumbnails, the whole album in the lightbox, a link to the album page. */
export function GalleryGrid({ albumId, name, photos, caption, preview = false }: GalleryGridProps) {
  const { t } = useTranslation('common')
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null)

  const shown = visibleThumbnailCount(photos.length)
  const hidden = photos.length - shown
  const single = shown === 1

  return (
    <figure className="my-6">
      <div className={clsx('grid gap-1.5', GRID_COLUMNS[shown] ?? 'grid-cols-4')}>
        {photos.slice(0, shown).map((photo, index) => {
          const isMoreTile = hidden > 0 && index === shown - 1
          const tileClass = clsx(
            'relative overflow-hidden rounded-md bg-surface-700 group',
            single ? 'aspect-video' : 'aspect-square',
          )
          const tile = (
            <>
              <img
                src={photo.url}
                alt=""
                loading="lazy"
                className={clsx('w-full h-full object-cover', !preview && 'transition-transform duration-300 group-hover:scale-105')}
                style={{
                  objectPosition: photo.focalPointX != null
                    ? `${photo.focalPointX * 100}% ${(photo.focalPointY ?? 0.5) * 100}%`
                    : 'center',
                }}
              />
              {isMoreTile && (
                <span className="absolute inset-0 flex items-center justify-center bg-black/55 text-white text-xl sm:text-2xl font-semibold">
                  +{hidden + 1}
                </span>
              )}
            </>
          )
          if (preview) {
            return <div key={photo.id} className={tileClass}>{tile}</div>
          }
          return (
            <button
              key={photo.id}
              type="button"
              onClick={() => setLightboxIndex(index)}
              aria-label={isMoreTile
                ? t('gallery.inArticle.showAll', { count: photos.length })
                : photo.caption || t('gallery.inArticle.openPhoto', { index: index + 1, total: photos.length })}
              className={clsx(tileClass, 'cursor-zoom-in focus-visible:outline-2 focus-visible:outline-primary-500')}
            >
              {tile}
            </button>
          )
        })}
      </div>

      <figcaption className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-surface-400">
        <Images className="h-4 w-4 shrink-0" aria-hidden="true" />
        <span className="text-surface-200 font-medium">{name}</span>
        <span aria-hidden="true">·</span>
        <span>{photos.length} {t('gallery.photo', { count: photos.length })}</span>
        {!preview && (
          <Link
            to={`/galeria/${albumId}`}
            className="inline-flex items-center gap-1 text-primary-400 hover:text-primary-300 sm:ml-auto"
          >
            {t('gallery.inArticle.openAlbum')}
            <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
          </Link>
        )}
      </figcaption>
      {caption && <p className="mt-1 text-sm text-surface-400 italic">{caption}</p>}

      {lightboxIndex !== null && (
        <Lightbox
          photos={photos}
          currentIndex={lightboxIndex}
          onClose={() => setLightboxIndex(null)}
          onNavigate={setLightboxIndex}
        />
      )}
    </figure>
  )
}

/**
 * The block on the public article. The album is read live, so photos added to it later appear
 * here too. An album that is unpublished, deleted or empty renders nothing — the article reads as
 * if the block was never there, rather than showing a hole or an error.
 */
export function ArticleGalleryBlock({ albumId, caption }: { albumId: string; caption?: string | null }) {
  const { data: album } = useQuery({
    // Same key as AlbumPage: following the link to the album opens it from cache.
    queryKey: ['gallery', 'album', albumId],
    queryFn: () => galleryApi.getAlbum(albumId),
    placeholderData: undefined,
    retry: false,
  })

  if (!album || album.photos.length === 0) return null
  return <GalleryGrid albumId={album.id} name={album.name} photos={album.photos} caption={caption} />
}
