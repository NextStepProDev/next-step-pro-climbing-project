import { Modal } from './Modal'
import { Newspaper } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { renderRichText } from '../../utils/renderRichText'

export type PreviewBlock = {
  tempId: string
  type: 'TEXT' | 'IMAGE'
  content?: string
  imageUrl?: string
  caption?: string
}

interface NewsPreviewModalProps {
  isOpen: boolean
  onClose: () => void
  title: string
  excerpt: string
  thumbnailUrl: string | null
  focalPoint: { x: number; y: number }
  blocks: PreviewBlock[]
}

export function NewsPreviewModal({
  isOpen,
  onClose,
  title,
  excerpt,
  thumbnailUrl,
  focalPoint,
  blocks,
}: NewsPreviewModalProps) {
  const { t } = useTranslation('admin')

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('news.previewTitle')} size="xl">
      <div className="max-w-2xl mx-auto">
        {/* Draft badge */}
        <div className="mb-4 inline-flex items-center gap-1.5 px-2.5 py-1 bg-amber-500/15 border border-amber-500/30 rounded text-xs text-amber-400 font-medium">
          {t('news.previewDraftBadge')}
        </div>

        {/* Thumbnail */}
        {thumbnailUrl ? (
          <div className="w-full rounded-lg overflow-hidden mb-6" style={{ aspectRatio: '5/2' }}>
            <img
              src={thumbnailUrl}
              alt={title}
              className="w-full h-full object-cover"
              style={{ objectPosition: `${focalPoint.x * 100}% ${focalPoint.y * 100}%` }}
            />
          </div>
        ) : (
          <div className="w-full h-40 bg-dark-800 rounded-lg flex items-center justify-center mb-6">
            <Newspaper className="h-12 w-12 text-dark-500" />
          </div>
        )}

        {/* Title */}
        <h1 className="text-2xl font-bold text-dark-100 mb-3">
          {title || <span className="text-dark-500 italic">{t('news.previewNoTitle')}</span>}
        </h1>

        {/* Excerpt */}
        {excerpt && (
          <p className="text-dark-300 text-sm mb-6 leading-relaxed">{excerpt}</p>
        )}

        {blocks.length > 0 && <hr className="border-dark-700 mb-6" />}

        {/* Content blocks */}
        <div className="space-y-6">
          {blocks.map((block) => {
            if (block.type === 'TEXT') {
              return (
                <div
                  key={block.tempId}
                  className="text-dark-200 leading-relaxed"
                  dangerouslySetInnerHTML={{
                    __html: block.content
                      ? renderRichText(block.content)
                      : `<span class="text-dark-500 italic">${t('news.previewEmptyBlock')}</span>`,
                  }}
                ></div>
              )
            }
            if (block.type === 'IMAGE') {
              return (
                <figure key={block.tempId} className="my-4">
                  {block.imageUrl && (
                    <img
                      src={block.imageUrl}
                      alt={block.caption ?? ''}
                      className="w-full rounded-lg"
                    />
                  )}
                  {block.caption && (
                    <figcaption className="text-sm text-dark-400 text-center mt-2">
                      {block.caption}
                    </figcaption>
                  )}
                </figure>
              )
            }
            return null
          })}
        </div>

        {blocks.length === 0 && !excerpt && (
          <p className="text-dark-500 italic text-sm">{t('news.previewNoContent')}</p>
        )}
      </div>
    </Modal>
  )
}
