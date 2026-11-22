import { useState, useEffect, useCallback } from 'react'
import { useToast } from '../../context/ToastContext'
import { getErrorMessage } from '../../utils/errors'
import { dateTimeLocalChanged, toDateTimeLocalValue } from '../../utils/dateTimeLocal'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  Newspaper,
  Plus,
  Trash2,
  ChevronUp,
  ChevronDown,
  Upload,
  Eye,
  EyeOff,
  ImageIcon,
  Type,
  Save,
  X,
  ArrowLeft,
  Library,
  Images,
  Send,
  Video,
  Crop,
  CalendarClock,
  GalleryHorizontal,
} from 'lucide-react'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import { adminNewsApi } from '../../api/client'
import { COURSE_CONTENT_LANGUAGES } from '../../constants/courseLanguages'
import type {
  NewsAdmin,
  NewsDetailAdmin,
  ContentBlockAdmin,
  CreateNewsRequest,
} from '../../types'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { Modal } from '../../components/ui/Modal'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { GalleryPickerModal } from '../../components/ui/GalleryPickerModal'
import { AlbumPickerModal, AdminGalleryBlockPreview } from '../../components/gallery/AdminArticleGallery'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'
import { NewsPreviewModal, type PreviewBlock } from '../../components/ui/NewsPreviewModal'
import { QueryError } from '../../components/ui/QueryError'
import { RichTextEditor } from '../../components/ui/RichTextEditor'
import clsx from 'clsx'
import { useUnsavedChangesWarning } from '../../hooks/useUnsavedChangesWarning'
import { useDraft } from '../../hooks/useDraft'
import { DraftBanner } from '../../components/ui/DraftBanner'
import { keepablePendingBlocks } from '../../utils/pendingBlockDraft'
import { CMS_IMAGE_ACCEPT } from '../../utils/imageUtils'

type View = 'list' | 'edit'

// ---------- Pending block types (not yet saved) ----------

type PendingTextBlock = {
  type: 'TEXT'
  tempId: string
  content: string
}

type PendingImageBlock = {
  type: 'IMAGE'
  tempId: string
  caption: string
  source: 'file' | 'library' | 'gallery'
  file?: File
  preview?: string
  imageUrl?: string
}

type PendingVideoBlock = {
  type: 'VIDEO_EMBED'
  tempId: string
  url: string
}

// Points at an existing album; the photos stay in the gallery and are read live.
type PendingGalleryBlock = {
  type: 'GALLERY'
  tempId: string
  albumId: string
  caption: string
}

type PendingBlock = PendingTextBlock | PendingImageBlock | PendingVideoBlock | PendingGalleryBlock

type BlockEdit = { content?: string; caption?: string; albumId?: string }

/** Where the album picker's choice goes: a new block, or the block whose album is being changed. */
type AlbumPickerTarget = { kind: 'new' } | { kind: 'existing'; blockId: string } | { kind: 'pending'; tempId: string }

function initialEdit(b: ContentBlockAdmin): BlockEdit {
  return { content: b.content ?? undefined, caption: b.caption ?? undefined, albumId: b.albumId ?? undefined }
}

function blockEditChanged(b: ContentBlockAdmin, edit: BlockEdit | undefined): boolean {
  if (!edit) return false
  if (b.blockType === 'TEXT' || b.blockType === 'VIDEO_EMBED') return edit.content !== (b.content ?? undefined)
  if (b.blockType === 'IMAGE') return edit.caption !== (b.caption ?? undefined)
  if (b.blockType === 'GALLERY') {
    return edit.caption !== (b.caption ?? undefined) || edit.albumId !== (b.albumId ?? undefined)
  }
  return false
}

interface ArticleDraft {
  title: string
  excerpt: string
  blockEdits: Record<string, BlockEdit>
  pendingBlocks: PendingBlock[]
  thumbnailFromLibrary: string | null
  focalPoint: { x: number; y: number } | null
  /** Device images (new blocks + a new thumbnail) the draft could not keep. */
  droppedFiles: number
}

function toArticleDraft(s: {
  title: string
  excerpt: string
  blockEdits: Record<string, BlockEdit>
  pendingBlocks: PendingBlock[]
  thumbnailFile: File | null
  thumbnailFromLibrary: string | null
  focalPoint: { x: number; y: number } | null
}): ArticleDraft {
  const { kept, dropped } = keepablePendingBlocks(s.pendingBlocks)
  return {
    title: s.title,
    excerpt: s.excerpt,
    blockEdits: s.blockEdits,
    pendingBlocks: kept,
    thumbnailFromLibrary: s.thumbnailFromLibrary,
    focalPoint: s.focalPoint,
    droppedFiles: dropped + (s.thumbnailFile ? 1 : 0),
  }
}

// Normalizes a raw URL (YouTube/Instagram) to an embed URL — identical logic to the backend
function normalizeVideoUrlForPreview(url: string): string | undefined {
  const s = url.trim()
  let m = s.match(/(?:youtube\.com\/watch\?(?:.*&)?v=|youtu\.be\/)([\w-]{11})/)
  if (m) return `https://www.youtube.com/embed/${m[1]}`
  m = s.match(/youtube\.com\/shorts\/([\w-]{11})/)
  if (m) return `https://www.youtube.com/embed/${m[1]}`
  m = s.match(/instagram\.com\/reel\/([\w-]+)/)
  if (m) return `https://www.instagram.com/reel/${m[1]}/embed/`
  m = s.match(/instagram\.com\/p\/([\w-]+)/)
  if (m) return `https://www.instagram.com/p/${m[1]}/embed/`
  return s || undefined
}

function LanguageBadge({ lang }: { lang: string }) {
  const colors: Record<string, string> = {
    pl: 'bg-blue-900/40 text-blue-400 border-blue-700',
    en: 'bg-green-900/40 text-green-400 border-green-700',
    es: 'bg-purple-900/40 text-purple-400 border-purple-700',
  }
  return (
    <span className={clsx('text-[10px] font-bold uppercase px-1.5 py-0.5 rounded border', colors[lang] ?? 'bg-surface-600 text-surface-400 border-surface-500')}>
      {lang}
    </span>
  )
}

// ---------- Main panel ----------

export function AdminNewsPanel() {
  const { t } = useTranslation('admin')
  const { showToast } = useToast()
  const queryClient = useQueryClient()
  const [view, setView] = useState<View>('list')
  const [selectedNewsId, setSelectedNewsId] = useState<string | null>(null)

  const { data: articles, isLoading, error } = useQuery({
    queryKey: ['admin', 'news'],
    queryFn: () => adminNewsApi.getAll(0, 500),
  })

  // Group articles by translationGroupId
  const groups = (() => {
    const allArticles = articles?.content ?? []
    const map = new Map<string, NewsAdmin[]>()
    for (const article of allArticles) {
      const group = map.get(article.translationGroupId) ?? []
      group.push(article)
      map.set(article.translationGroupId, group)
    }
    return [...map.entries()]
      .sort((a, b) => {
        const dateA = a[1].reduce((max, art) => art.createdAt > max ? art.createdAt : max, '')
        const dateB = b[1].reduce((max, art) => art.createdAt > max ? art.createdAt : max, '')
        return dateB.localeCompare(dateA)
      })
      .map(([groupId, groupArticles]) => ({ groupId, articles: groupArticles }))
  })()

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['admin', 'news', selectedNewsId],
    queryFn: () => adminNewsApi.getById(selectedNewsId!),
    enabled: !!selectedNewsId && view === 'edit',
    // Detail-by-id feeding the edit form: opening a different article must not
    // flash the previous one's content. Opt out of the global keepPreviousData.
    placeholderData: undefined,
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateNewsRequest) => adminNewsApi.create(data),
    onSuccess: (news) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'news'] })
      setSelectedNewsId(news.id)
      setView('edit')
    },
  })

  const deleteGroupMutation = useMutation({
    mutationFn: async (articleIds: string[]) => {
      for (const id of articleIds) {
        await adminNewsApi.delete(id)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'news'] })
      setDeleteGroupArticles(null)
    },
  })

  const publishMutation = useMutation({
    mutationFn: (id: string) => adminNewsApi.publish(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'news'] }),
  })

  const unpublishMutation = useMutation({
    mutationFn: (id: string) => adminNewsApi.unpublish(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'news'] }),
  })

  const duplicateFromListMutation = useMutation({
    mutationFn: ({ sourceId, targetLanguage }: { sourceId: string; targetLanguage: string }) =>
      adminNewsApi.duplicateAsTranslation(sourceId, targetLanguage),
    onSuccess: (newDetail) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'news'] })
      setSelectedNewsId(newDetail.id)
      setView('edit')
    },
  })

  const [deleteGroupArticles, setDeleteGroupArticles] = useState<string[] | null>(null)
  const [newsletterConfirmId, setNewsletterConfirmId] = useState<string | null>(null)

  const sendNewsletterMutation = useMutation({
    mutationFn: (id: string) => adminNewsApi.sendNewsletter(id),
    onSuccess: (result) => {
      setNewsletterConfirmId(null)
      showToast(t('news.newsletterSentSuccess', { count: result.subscriberCount }))
    },
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12">
        <LoadingSpinner />
      </div>
    )
  }

  if (error) {
    return <QueryError error={error} />
  }

  if (view === 'edit' && selectedNewsId) {
    if (detailLoading || !detail) {
      return (
        <div className="flex items-center justify-center py-12">
          <LoadingSpinner />
        </div>
      )
    }
    return (
      <EditView
        key={detail.id}
        newsId={selectedNewsId}
        detail={detail}
        onBack={() => {
          setView('list')
          setSelectedNewsId(null)
        }}
      />
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-surface-100">{t('news.title')}</h2>
        <Button
          onClick={() => createMutation.mutate({ title: t('news.newArticleDefaultTitle'), language: 'pl' })}
          disabled={createMutation.isPending}
        >
          <Plus className="h-4 w-4 mr-2" />
          {t('news.addArticle')}
        </Button>
      </div>

      {groups.length === 0 ? (
        <div className="text-center text-surface-400 py-12">
          {t('news.noArticles')}
        </div>
      ) : (
        <div className="space-y-3">
          {groups.map(({ groupId, articles: groupArticles }) => (
            <TranslationGroupRow
              key={groupId}
              groupArticles={groupArticles}
              onEdit={(articleId) => {
                setSelectedNewsId(articleId)
                setView('edit')
              }}
              onDelete={(articleIds) => setDeleteGroupArticles(articleIds)}
              onPublish={(id) => publishMutation.mutate(id)}
              onUnpublish={(id) => unpublishMutation.mutate(id)}
              onSendNewsletter={(id) => setNewsletterConfirmId(id)}
              onCreateTranslation={(sourceId, targetLang) =>
                duplicateFromListMutation.mutate({ sourceId, targetLanguage: targetLang })
              }
              isCreatingTranslation={duplicateFromListMutation.isPending}
            />
          ))}
        </div>
      )}

      <ConfirmModal
        isOpen={!!deleteGroupArticles}
        title={t('news.deleteConfirmTitle')}
        message={t('news.deleteGroupConfirmMessage')}
        confirmText={t('news.delete')}
        onConfirm={() => deleteGroupArticles && deleteGroupMutation.mutate(deleteGroupArticles)}
        onClose={() => setDeleteGroupArticles(null)}
      />

      <ConfirmModal
        isOpen={!!newsletterConfirmId}
        title={t('news.newsletterConfirmTitle')}
        message={t('news.newsletterConfirmMessage')}
        confirmText={t('news.newsletterSend')}
        onConfirm={() => newsletterConfirmId && sendNewsletterMutation.mutate(newsletterConfirmId)}
        onClose={() => setNewsletterConfirmId(null)}
      />

    </div>
  )
}

// ==================== Translation group row in the list ====================

const LANG_ORDER = ['pl', 'en', 'es'] as const

function TranslationGroupRow({
  groupArticles,
  onEdit,
  onDelete,
  onPublish,
  onUnpublish,
  onSendNewsletter,
  onCreateTranslation,
  isCreatingTranslation,
}: {
  groupArticles: NewsAdmin[]
  onEdit: (articleId: string) => void
  onDelete: (articleIds: string[]) => void
  onPublish: (articleId: string) => void
  onUnpublish: (articleId: string) => void
  onSendNewsletter: (articleId: string) => void
  onCreateTranslation: (sourceId: string, targetLang: string) => void
  isCreatingTranslation: boolean
}) {
  const { t, i18n } = useTranslation('admin')

  const langMap = new Map(groupArticles.map(a => [a.language, a]))

  const displayArticle = langMap.get('pl') ?? langMap.get('en') ?? langMap.get('es') ?? groupArticles[0]

  const firstExistingArticle = groupArticles[0]

  return (
    <div className="flex items-center gap-4 bg-surface-800 border border-surface-700 rounded-lg p-4">
      <div className="flex-shrink-0 w-16 h-16 bg-surface-700 rounded overflow-hidden">
        {displayArticle.thumbnailUrl ? (
          <img src={displayArticle.thumbnailUrl} alt={displayArticle.title} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Newspaper className="h-6 w-6 text-surface-500" />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <span
            className={clsx(
              'text-xs px-2 py-0.5 rounded-full',
              displayArticle.published
                ? 'bg-green-900/40 text-green-400'
                : 'bg-surface-700 text-surface-100'
            )}
          >
            {displayArticle.published ? t('news.published') : t('news.draft')}
          </span>
          {displayArticle.publishedAt && (
            <span className="text-xs text-surface-400">
              {new Date(displayArticle.publishedAt).toLocaleDateString(i18n.language)}
            </span>
          )}
        </div>
        <p className="font-medium text-surface-100 truncate">{displayArticle.title}</p>
        <div className="flex items-center gap-1.5 mt-1">
          {LANG_ORDER.map((lang) => {
            const article = langMap.get(lang)
            if (article) {
              return (
                <button
                  key={lang}
                  onClick={() => onEdit(article.id)}
                  title={`${t('news.edit')} (${lang.toUpperCase()})`}
                  className={clsx(
                    'text-[10px] font-bold uppercase px-2 py-0.5 rounded border cursor-pointer transition-colors',
                    lang === 'pl' && 'bg-blue-900/40 text-blue-400 border-blue-700 hover:bg-blue-900/60',
                    lang === 'en' && 'bg-green-900/40 text-green-400 border-green-700 hover:bg-green-900/60',
                    lang === 'es' && 'bg-purple-900/40 text-purple-400 border-purple-700 hover:bg-purple-900/60',
                  )}
                >
                  {lang}
                </button>
              )
            }
            return (
              <button
                key={lang}
                onClick={() => onCreateTranslation(firstExistingArticle.id, lang)}
                disabled={isCreatingTranslation}
                title={t('news.createTranslation', { lang: lang.toUpperCase() })}
                className={clsx(
                  'text-[10px] font-bold uppercase px-2 py-0.5 rounded border border-dashed cursor-pointer transition-colors',
                  'border-surface-500 text-surface-400 hover:border-surface-300 hover:text-surface-200',
                  isCreatingTranslation && 'opacity-50 cursor-wait',
                )}
              >
                +{lang}
              </button>
            )
          })}
        </div>
      </div>

      <div className="flex items-center gap-2 flex-shrink-0">
        {displayArticle.published && (
          <button
            onClick={() => onSendNewsletter(displayArticle.id)}
            title={t('news.newsletterSend')}
            className="p-2 text-surface-400 hover:text-primary-400 transition-colors"
          >
            <Send className="h-4 w-4" />
          </button>
        )}
        <button
          onClick={() => displayArticle.published ? onUnpublish(displayArticle.id) : onPublish(displayArticle.id)}
          title={(displayArticle.published ? t('news.unpublish') : t('news.publish')) + ' ' + t('editorUi.allLanguages')}
          className={`p-2 transition-colors ${displayArticle.published ? 'text-green-400 hover:text-orange-400' : 'text-surface-400 hover:text-surface-100'}`}
        >
          {displayArticle.published ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
        </button>
        <button
          onClick={() => onDelete(groupArticles.map(a => a.id))}
          title={t('news.delete')}
          className="p-2 text-surface-400 hover:text-red-400 transition-colors"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}

// ==================== Edit view ====================

function EditView({
  newsId,
  detail,
  onBack,
}: {
  newsId: string
  detail: NewsDetailAdmin
  onBack: () => void
}) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const invalidate = useCallback(() =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['admin', 'news'] }),
      queryClient.invalidateQueries({ queryKey: ['admin', 'news', newsId] }),
      queryClient.invalidateQueries({ queryKey: ['news'] }),
    ])
  , [queryClient, newsId])

  // ---------- Translation duplication ----------
  const { data: allArticles } = useQuery({
    queryKey: ['admin', 'news', 'translations'],
    queryFn: () => adminNewsApi.getAll(0, 500),
  })

  const existingLanguages = (allArticles?.content ?? [])
    .filter(a => a.translationGroupId === detail.translationGroupId)
    .map(a => a.language)

  const availableTargetLanguages = COURSE_CONTENT_LANGUAGES
    .filter(lang => !existingLanguages.includes(lang.code))

  const duplicateMutation = useMutation({
    mutationFn: (targetLanguage: string) => adminNewsApi.duplicateAsTranslation(newsId, targetLanguage),
    onSuccess: () => {
      invalidate()
      onBack()
    },
  })

  const hasTranslationSiblings = existingLanguages.length > 1

  const syncMediaMutation = useMutation({
    mutationFn: () => adminNewsApi.syncMediaToTranslations(newsId),
    onSuccess: () => {
      invalidate()
    },
  })

  const [showSyncMediaModal, setShowSyncMediaModal] = useState(false)
  const [pendingSyncAction, setPendingSyncAction] = useState<'save' | 'publish' | null>(null)

  const [deleteSingleConfirm, setDeleteSingleConfirm] = useState(false)

  const deleteSingleMutation = useMutation({
    mutationFn: () => adminNewsApi.delete(newsId),
    onSuccess: () => {
      draft.discard()
      invalidate()
      onBack()
    },
  })

  // ---------- Meta state ----------
  const [title, setTitle] = useState(detail.title)
  const [excerpt, setExcerpt] = useState(detail.excerpt ?? '')

  // ---------- Thumbnail state ----------
  const [thumbnailFile, setThumbnailFile] = useState<File | null>(null)
  const [thumbnailPreview, setThumbnailPreview] = useState<string | null>(null)
  const [thumbnailFromLibrary, setThumbnailFromLibrary] = useState<string | null>(null)
  // null = "full image with blurred background" mode, {x,y} = crop mode (object-cover)
  const [focalPoint, setFocalPoint] = useState<{ x: number; y: number } | null>(
    detail.thumbnailFocalPointX != null
      ? { x: detail.thumbnailFocalPointX, y: detail.thumbnailFocalPointY ?? 0.5 }
      : null
  )
  const cropEnabled = focalPoint !== null

  // ---------- Media picker ----------
  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [pickerTarget, setPickerTarget] = useState<'thumbnail' | 'block'>('block')
  const [showGalleryPicker, setShowGalleryPicker] = useState(false)
  const [albumPickerTarget, setAlbumPickerTarget] = useState<AlbumPickerTarget | null>(null)

  // A function, not a value: pendingBlocks is declared further down this component.
  const albumPickerSelectedId = (): string | null => {
    if (!albumPickerTarget || albumPickerTarget.kind === 'new') return null
    if (albumPickerTarget.kind === 'existing') return blockEdits[albumPickerTarget.blockId]?.albumId ?? null
    const { tempId } = albumPickerTarget
    const pending = pendingBlocks.find((b) => b.tempId === tempId)
    return pending?.type === 'GALLERY' ? pending.albumId : null
  }

  const applyPickedAlbum = (albumId: string) => {
    if (!albumPickerTarget) return
    if (albumPickerTarget.kind === 'new') {
      setPendingBlocks((prev) => [...prev, { type: 'GALLERY', tempId: crypto.randomUUID(), albumId, caption: '' }])
    } else if (albumPickerTarget.kind === 'existing') {
      const { blockId } = albumPickerTarget
      setBlockEdits((prev) => ({ ...prev, [blockId]: { ...prev[blockId], albumId } }))
    } else {
      const { tempId } = albumPickerTarget
      setPendingBlocks((prev) => prev.map((b) => (b.tempId === tempId && b.type === 'GALLERY' ? { ...b, albumId } : b)))
    }
  }

  useEffect(() => {
    return () => {
      if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
    }
  }, [thumbnailPreview])

  // ---------- Block edits (existing blocks, by id) ----------
  const [blockEdits, setBlockEdits] = useState<Record<string, BlockEdit>>(() => {
    const map: Record<string, BlockEdit> = {}
    detail.blocks.forEach((b) => {
      map[b.id] = initialEdit(b)
    })
    return map
  })

  // Sync new blocks added via invalidate (e.g. after immediate ops) without resetting user edits.
  // Done as a guarded state update during render rather than in an effect, so it can't trigger
  // cascading re-renders; the guard makes it run only when a genuinely new block id appears.
  if (detail.blocks.some((b) => !(b.id in blockEdits))) {
    setBlockEdits((prev) => {
      const next = { ...prev }
      detail.blocks.forEach((b) => {
        if (!(b.id in next)) {
          next[b.id] = initialEdit(b)
        }
      })
      return next
    })
  }

  // ---------- Pending new blocks ----------
  const [pendingBlocks, setPendingBlocks] = useState<PendingBlock[]>([])

  // Cleanup blob URLs on unmount. Empty deps is intentional — we want a single
  // cleanup registered at mount that captures pendingBlocks via closure on unmount.
  useEffect(() => {
    return () => {
      pendingBlocks.forEach((b) => {
        if (b.type === 'IMAGE' && b.source === 'file' && b.preview) URL.revokeObjectURL(b.preview)
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ---------- Dirty detection ----------
  const metaDirty = title !== detail.title || excerpt !== (detail.excerpt ?? '')
  const thumbnailDirty = thumbnailFile !== null || thumbnailFromLibrary !== null
  const focalPointDirty = (() => {
    if (!detail.thumbnailUrl && !thumbnailDirty) return false
    const savedX = detail.thumbnailFocalPointX
    if (focalPoint === null && savedX == null) return false
    if (focalPoint === null && savedX != null) return true
    if (focalPoint !== null && savedX == null) return true
    return focalPoint!.x !== savedX || focalPoint!.y !== (detail.thumbnailFocalPointY ?? 0.5)
  })()
  const blockEditsDirty = detail.blocks.some((b) => blockEditChanged(b, blockEdits[b.id]))
  const isDirty = metaDirty || thumbnailDirty || focalPointDirty || blockEditsDirty || pendingBlocks.length > 0

  // The editor's own "back to list" already confirms; this covers a refresh or a closed tab
  useUnsavedChangesWarning(isDirty)

  // ---------- Draft (survives a navbar click, a back gesture, a crash) ----------
  const draft = useDraft<ArticleDraft>({
    scope: `news:${newsId}`,
    value: toArticleDraft({ title, excerpt, blockEdits, pendingBlocks, thumbnailFile, thumbnailFromLibrary, focalPoint }),
    dirty: isDirty,
    baseline: detail.updatedAt,
  })
  const restoreDraft = () => {
    const data = draft.restore()
    if (!data) return
    setTitle(data.title)
    setExcerpt(data.excerpt)
    // Only blocks the article still has: one deleted since the draft has nothing to edit.
    setBlockEdits((prev) => {
      const next = { ...prev }
      for (const [id, edit] of Object.entries(data.blockEdits)) if (id in next) next[id] = edit
      return next
    })
    setPendingBlocks(data.pendingBlocks)
    setThumbnailFromLibrary(data.thumbnailFromLibrary)
    setFocalPoint(data.focalPoint)
  }

  // ---------- Preview ----------
  const [showPreview, setShowPreview] = useState(false)

  const previewBlocks: PreviewBlock[] = [
    ...detail.blocks.map((b) => ({
      tempId: b.id,
      type: b.blockType,
      content: blockEdits[b.id]?.content ?? b.content ?? undefined,
      imageUrl: b.imageUrl ?? undefined,
      caption: blockEdits[b.id]?.caption ?? b.caption ?? undefined,
      albumId: b.blockType === 'GALLERY' ? (blockEdits[b.id]?.albumId ?? b.albumId ?? null) : undefined,
    })),
    ...pendingBlocks.map((b) => ({
      tempId: b.tempId,
      type: b.type,
      content: b.type === 'TEXT' ? b.content : b.type === 'VIDEO_EMBED' ? normalizeVideoUrlForPreview(b.url) : undefined,
      imageUrl: b.type === 'IMAGE' ? (b.imageUrl ?? b.preview) : undefined,
      caption: b.type === 'IMAGE' || b.type === 'GALLERY' ? b.caption || undefined : undefined,
      albumId: b.type === 'GALLERY' ? b.albumId : undefined,
    })),
  ]

  // ---------- Save all ----------
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const performSave = async (syncMedia: boolean) => {
    // 1. Meta
    if (metaDirty) {
      await adminNewsApi.updateMeta(newsId, { title, excerpt })
    }

    // 2. Thumbnail + focal point
    const fpX = focalPoint?.x ?? null
    const fpY = focalPoint?.y ?? null
    if (thumbnailFromLibrary) {
      await adminNewsApi.setThumbnailUrl(newsId, thumbnailFromLibrary)
      await adminNewsApi.updateThumbnailFocalPoint(newsId, fpX, fpY)
      setThumbnailFromLibrary(null)
    } else if (thumbnailFile) {
      await adminNewsApi.uploadThumbnail(newsId, thumbnailFile)
      await adminNewsApi.updateThumbnailFocalPoint(newsId, fpX, fpY)
      setThumbnailFile(null)
      if (thumbnailPreview) {
        URL.revokeObjectURL(thumbnailPreview)
        setThumbnailPreview(null)
      }
    } else if (focalPointDirty) {
      await adminNewsApi.updateThumbnailFocalPoint(newsId, fpX, fpY)
    }

    // 3. Existing block edits
    await Promise.all(
      detail.blocks
        .filter((b) => blockEditChanged(b, blockEdits[b.id]))
        .map((b) => {
          const edit = blockEdits[b.id]
          if (b.blockType === 'TEXT') {
            return adminNewsApi.updateTextBlock(b.id, { content: edit.content ?? '' })
          }
          if (b.blockType === 'VIDEO_EMBED') {
            return adminNewsApi.updateVideoEmbedBlock(b.id, edit.content ?? '')
          }
          if (b.blockType === 'GALLERY') {
            // Unreachable from the editor (the caption field is disabled without an album); kept
            // so a restored draft can never send a request the endpoint would refuse.
            if (!edit.albumId) return Promise.resolve()
            return adminNewsApi.updateGalleryBlock(b.id, edit.albumId, edit.caption || undefined)
          }
          return adminNewsApi.updateImageBlock(b.id, { caption: edit.caption || undefined })
        })
    )

    // 4. New blocks (sequential to preserve order, skip empty text blocks)
    const blocksToSave = pendingBlocks.filter(
      (b) => b.type !== 'TEXT' || b.content.trim().length > 0
    )
    for (const pending of blocksToSave) {
      if (pending.type === 'TEXT') {
        await adminNewsApi.addTextBlock(newsId, { content: pending.content })
      } else if (pending.type === 'VIDEO_EMBED') {
        await adminNewsApi.addVideoEmbedBlock(newsId, pending.url)
      } else if (pending.type === 'GALLERY') {
        await adminNewsApi.addGalleryBlock(newsId, pending.albumId, pending.caption || undefined)
      } else if ((pending.source === 'library' || pending.source === 'gallery') && pending.imageUrl) {
        await adminNewsApi.addImageBlockFromUrl(newsId, pending.imageUrl, pending.caption || undefined)
      } else if (pending.source === 'file' && pending.file) {
        await adminNewsApi.addImageBlock(newsId, pending.file, pending.caption || undefined)
        if (pending.preview) URL.revokeObjectURL(pending.preview)
      }
      // Out of the queue as soon as it is saved: a failure on a later block used to leave the
      // earlier ones queued, and the next "Save" added them to the article a second time.
      setPendingBlocks((prev) => prev.filter((b) => b.tempId !== pending.tempId))
    }

    // 5. Sync media to translations if requested
    if (syncMedia) {
      await syncMediaMutation.mutateAsync()
    }

    const emptyTextBlocks = pendingBlocks.filter(
      (b) => b.type === 'TEXT' && b.content.trim().length === 0
    )
    setPendingBlocks(emptyTextBlocks)
    await invalidate()
  }

  const executeSave = async (syncMedia: boolean) => {
    setIsSaving(true)
    setSaveError(null)
    try {
      await performSave(syncMedia)
      draft.clear()
      if (pendingSyncAction === 'publish') {
        publishMutation.mutate()
      }
      setShowSyncMediaModal(false)
      setPendingSyncAction(null)
      onBack()
    } catch (err) {
      setSaveError(getErrorMessage(err))
      invalidate()
    } finally {
      setIsSaving(false)
    }
  }

  const handleSave = async () => {
    const hasNewMedia = pendingBlocks.some(b => b.type === 'IMAGE' || b.type === 'VIDEO_EMBED' || b.type === 'GALLERY')
    if (hasNewMedia && hasTranslationSiblings) {
      setPendingSyncAction('save')
      setShowSyncMediaModal(true)
      return
    }
    await executeSave(false)
  }

  // ---------- Publish (save if dirty, then publish) ----------
  const handlePublish = async () => {
    const hasNewMedia = pendingBlocks.some(b => b.type === 'IMAGE' || b.type === 'VIDEO_EMBED' || b.type === 'GALLERY')
    if (isDirty && hasNewMedia && hasTranslationSiblings) {
      setPendingSyncAction('publish')
      setShowSyncMediaModal(true)
      return
    }
    if (isDirty) {
      setIsSaving(true)
      setSaveError(null)
      try {
        await performSave(false)
        draft.clear()
      } catch (err) {
        setSaveError(getErrorMessage(err))
        setIsSaving(false)
        invalidate()
        return
      }
      setIsSaving(false)
    }
    publishMutation.mutate()
  }

  // ---------- Cancel (discard local changes) ----------
  const handleCancel = () => {
    setTitle(detail.title)
    setExcerpt(detail.excerpt ?? '')

    if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
    setThumbnailFile(null)
    setThumbnailPreview(null)
    setThumbnailFromLibrary(null)
    setFocalPoint(
      detail.thumbnailFocalPointX != null
        ? { x: detail.thumbnailFocalPointX, y: detail.thumbnailFocalPointY ?? 0.5 }
        : null
    )

    const reset: Record<string, BlockEdit> = {}
    detail.blocks.forEach((b) => {
      reset[b.id] = initialEdit(b)
    })
    setBlockEdits(reset)

    pendingBlocks.forEach((b) => {
      if (b.type === 'IMAGE' && b.source === 'file' && b.preview) URL.revokeObjectURL(b.preview)
    })
    setPendingBlocks([])
  }

  // ---------- Exit ----------
  const [showExitConfirm, setShowExitConfirm] = useState(false)

  const handleExit = () => {
    if (isDirty) {
      setShowExitConfirm(true)
    } else {
      onBack()
    }
  }

  // ---------- Pending block reorder ----------
  const movePendingBlock = (index: number, direction: 'UP' | 'DOWN') => {
    setPendingBlocks((prev) => {
      const next = [...prev]
      const swapIdx = direction === 'UP' ? index - 1 : index + 1
      if (swapIdx < 0 || swapIdx >= next.length) return prev
      ;[next[index], next[swapIdx]] = [next[swapIdx], next[index]]
      return next
    })
  }

  // ---------- Immediate structural mutations (move/delete) ----------
  const [deleteBlockConfirmId, setDeleteBlockConfirmId] = useState<string | null>(null)

  const moveBlockMutation = useMutation({
    mutationFn: ({ blockId, direction }: { blockId: string; direction: 'UP' | 'DOWN' }) =>
      adminNewsApi.moveBlock(blockId, direction),
    onSuccess: () => invalidate(),
  })

  const deleteBlockMutation = useMutation({
    mutationFn: (blockId: string) => adminNewsApi.deleteBlock(blockId),
    onSuccess: () => {
      invalidate()
      setDeleteBlockConfirmId(null)
    },
  })

  const [deleteThumbnailConfirm, setDeleteThumbnailConfirm] = useState(false)

  const deleteThumbnailMutation = useMutation({
    mutationFn: () => adminNewsApi.deleteThumbnail(newsId),
    onSuccess: () => invalidate(),
  })

  const publishMutation = useMutation({
    mutationFn: () => adminNewsApi.publish(newsId),
    onSuccess: () => invalidate(),
  })

  const unpublishMutation = useMutation({
    mutationFn: () => adminNewsApi.unpublish(newsId),
    onSuccess: () => invalidate(),
  })

  const updatePublishedAtMutation = useMutation({
    mutationFn: (publishedAt: string) => adminNewsApi.updatePublishedAt(newsId, publishedAt),
    onSuccess: () => invalidate(),
  })

  const published = detail.published

  // ---------- Render ----------
  return (
    <div className="pb-24 space-y-8">
      {/* Back to the list */}
      <button
        onClick={handleExit}
        className="inline-flex items-center gap-2 text-surface-300 hover:text-surface-100 transition-colors text-sm"
      >
        <ArrowLeft className="h-4 w-4" />
        {t('news.backToList')}
      </button>

      <DraftBanner
        offer={draft.offer}
        onRestore={restoreDraft}
        onDiscard={draft.discard}
        droppedFiles={draft.offer?.data.droppedFiles}
      />

      {/* Section 1: Metadata */}
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <div className="flex items-center gap-3 mb-4">
          <h3 className="text-lg font-semibold text-surface-100">{t('news.sectionMeta')}</h3>
          <LanguageBadge lang={detail.language} />
          <span className={clsx(
            'text-xs px-2.5 py-1 rounded-full',
            published ? 'bg-green-900/40 text-green-400' : 'bg-surface-700 text-surface-100'
          )}>
            {published ? t('news.published') : t('news.draft')}
          </span>
        </div>
        <div className="space-y-4">
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('news.titleLabel')}</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm text-surface-300 mb-1">{t('news.excerptLabel')}</label>
            <RichTextEditor
              value={excerpt}
              onChange={setExcerpt}
              rows={3}
            />
          </div>
          {detail.publishedAt && (
            <div>
              <label className="block text-sm text-surface-300 mb-1">{t('news.publishedAtLabel')}</label>
              <div className="flex items-center gap-2">
                <input
                  // Remounted when the saved date changes: the field is uncontrolled, so after "now"
                  // it kept showing the old date — and the next blur read that as an edit and wrote
                  // the old date straight back.
                  key={detail.publishedAt}
                  type="datetime-local"
                  defaultValue={toDateTimeLocalValue(detail.publishedAt)}
                  onBlur={(e) => {
                    if (!e.target.value || !detail.publishedAt) return
                    if (dateTimeLocalChanged(e.target.value, detail.publishedAt)) {
                      updatePublishedAtMutation.mutate(new Date(e.target.value).toISOString())
                    }
                  }}
                  className="bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 text-sm focus:outline-none focus:border-primary-500"
                />
                <button
                  type="button"
                  onClick={() => updatePublishedAtMutation.mutate(new Date().toISOString())}
                  title={t('news.publishedAtNow')}
                  className="inline-flex items-center gap-1.5 px-3 py-2 text-sm rounded border border-surface-600 bg-surface-700 text-surface-300 hover:text-surface-100 hover:border-surface-500 transition-colors"
                >
                  <CalendarClock className="h-4 w-4" />
                  {t('news.publishedAtNow')}
                </button>
                {updatePublishedAtMutation.isPending && (
                  <LoadingSpinner size="sm" />
                )}
              </div>
            </div>
          )}
        </div>
      </section>

      {/* Section: Translations */}
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-surface-100 mb-3">{t('news.duplicateAsTranslation')}</h3>
        {availableTargetLanguages.length === 0 ? (
          <p className="text-sm text-surface-400">{t('news.translationExists')}</p>
        ) : (
          <div className="flex gap-2">
            {availableTargetLanguages.map((lang) => (
              <Button
                key={lang.code}
                variant="secondary"
                size="sm"
                onClick={() => duplicateMutation.mutate(lang.code)}
                disabled={duplicateMutation.isPending}
              >
                {lang.label}
              </Button>
            ))}
          </div>
        )}
        {duplicateMutation.isError && (
          <p className="text-sm text-red-400 mt-2">{getErrorMessage(duplicateMutation.error)}</p>
        )}
        {hasTranslationSiblings && (
          <div className="mt-4 pt-4 border-t border-surface-600">
            <button
              onClick={() => setDeleteSingleConfirm(true)}
              className="text-sm text-red-400 hover:text-red-300 transition-colors"
            >
              {t('news.deleteSingleVersion', { lang: detail.language.toUpperCase() })}
            </button>
          </div>
        )}
      </section>

      {/* Section 2: Thumbnail */}
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-surface-100 mb-4">{t('news.sectionThumbnail')}</h3>

        {(thumbnailPreview || thumbnailFromLibrary || detail.thumbnailUrl) && (
          <div className="mb-4 relative max-w-sm">
            {cropEnabled ? (
              <FocalPointEditor
                imageUrl={thumbnailPreview ?? thumbnailFromLibrary ?? detail.thumbnailUrl ?? ''}
                value={focalPoint!}
                onChange={setFocalPoint}
                aspectRatio="16/9"
              />
            ) : (
              <div className="relative rounded-lg overflow-hidden bg-surface-700 aspect-video">
                <div
                  className="absolute inset-0 scale-110 blur-xl"
                  style={{
                    backgroundImage: `url(${thumbnailPreview ?? thumbnailFromLibrary ?? detail.thumbnailUrl ?? ''})`,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                  }}
                />
                <img
                  src={thumbnailPreview ?? thumbnailFromLibrary ?? detail.thumbnailUrl ?? ''}
                  alt=""
                  className="relative w-full h-full object-contain"
                />
              </div>
            )}
            {(thumbnailPreview || thumbnailFromLibrary) && (
              <span className="absolute top-1 left-1 text-xs px-1.5 py-0.5 bg-primary-600 text-white rounded z-10 pointer-events-none">
                {t('news.pendingBadge')}
              </span>
            )}
          </div>
        )}

        <div className="flex items-center gap-3 flex-wrap">
          <label className="cursor-pointer">
            <input
              type="file"
              accept={CMS_IMAGE_ACCEPT}
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (!file) return
                setThumbnailFile(file)
                setThumbnailFromLibrary(null)
                if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
                setThumbnailPreview(URL.createObjectURL(file))
              }}
            />
            <span className="inline-flex items-center gap-2 px-3 py-2 bg-surface-700 border border-surface-600 rounded text-sm text-surface-200 hover:border-primary-500 transition-colors cursor-pointer">
              <Upload className="h-4 w-4" />
              {t('news.chooseThumbnail')}
            </span>
          </label>

          <button
            onClick={() => { setPickerTarget('thumbnail'); setShowMediaPicker(true) }}
            className="inline-flex items-center gap-2 px-3 py-2 bg-surface-700 border border-surface-600 rounded text-sm text-surface-200 hover:border-primary-500 transition-colors"
          >
            <Library className="h-4 w-4" />
            {t('mediaPicker.chooseThumbnailFromLibrary')}
          </button>

          <button
            onClick={() => { setPickerTarget('thumbnail'); setShowGalleryPicker(true) }}
            className="inline-flex items-center gap-2 px-3 py-2 bg-surface-700 border border-surface-600 rounded text-sm text-surface-200 hover:border-primary-500 transition-colors"
          >
            <Images className="h-4 w-4" />
            {t('galleryPicker.chooseFromGallery')}
          </button>

          {(thumbnailPreview || thumbnailFromLibrary || detail.thumbnailUrl) && (
            <button
              onClick={() =>
                setFocalPoint(cropEnabled ? null : { x: 0.5, y: 0.5 })
              }
              className={`inline-flex items-center gap-2 px-3 py-2 border rounded text-sm transition-colors ${
                cropEnabled
                  ? 'bg-primary-600/20 border-primary-500 text-primary-300 hover:bg-primary-600/30'
                  : 'bg-surface-700 border-surface-600 text-surface-200 hover:border-primary-500'
              }`}
            >
              <Crop className="h-4 w-4" />
              {cropEnabled ? t('news.cropDisable') : t('news.cropEnable')}
            </button>
          )}

          {(thumbnailFile || thumbnailFromLibrary) && (
            <button
              onClick={() => {
                if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
                setThumbnailFile(null)
                setThumbnailPreview(null)
                setThumbnailFromLibrary(null)
              }}
              className="text-sm text-surface-400 hover:text-surface-100 transition-colors"
            >
              {t('news.cancelThumbnail')}
            </button>
          )}

          {detail.thumbnailUrl && !thumbnailFile && !thumbnailFromLibrary && (
            <button
              // Asks first, like every other image delete in the panel (team photo, gallery photo,
              // home-page image) — and it takes the thumbnail off every language version at once.
              onClick={() => setDeleteThumbnailConfirm(true)}
              disabled={deleteThumbnailMutation.isPending}
              className="text-sm text-red-400 hover:text-red-300 transition-colors"
            >
              {t('news.deleteThumbnail')}
            </button>
          )}
        </div>
      </section>

      {/* Section 3: Content blocks */}
      <section className="bg-surface-800 border border-surface-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-surface-100 mb-4">{t('news.sectionContent')}</h3>

        {/* Existing blocks */}
        {detail.blocks.length > 0 || pendingBlocks.length > 0 ? (
          <div className="space-y-4 mb-6">
            {detail.blocks.map((block, index) => (
              <BlockEditor
                key={block.id}
                block={block}
                editState={blockEdits[block.id] ?? initialEdit(block)}
                onChange={(edit) => setBlockEdits((prev) => ({ ...prev, [block.id]: edit }))}
                onPickAlbum={() => setAlbumPickerTarget({ kind: 'existing', blockId: block.id })}
                isFirst={index === 0}
                isLast={index === detail.blocks.length - 1 && pendingBlocks.length === 0}
                onMoveUp={() => moveBlockMutation.mutate({ blockId: block.id, direction: 'UP' })}
                onMoveDown={() => moveBlockMutation.mutate({ blockId: block.id, direction: 'DOWN' })}
                onDelete={() => setDeleteBlockConfirmId(block.id)}
              />
            ))}

            {/* Pending new blocks */}
            {pendingBlocks.map((pending, index) => (
              <PendingBlockItem
                key={pending.tempId}
                block={pending}
                onChange={(updated) =>
                  setPendingBlocks((prev) =>
                    prev.map((b) => (b.tempId === pending.tempId ? { ...b, ...updated } as PendingBlock : b))
                  )
                }
                onDelete={() => {
                  if (pending.type === 'IMAGE' && pending.source === 'file' && pending.preview) URL.revokeObjectURL(pending.preview)
                  setPendingBlocks((prev) => prev.filter((b) => b.tempId !== pending.tempId))
                }}
                isFirst={index === 0}
                isLast={index === pendingBlocks.length - 1}
                onMoveUp={() => movePendingBlock(index, 'UP')}
                onMoveDown={() => movePendingBlock(index, 'DOWN')}
                onPickAlbum={() => setAlbumPickerTarget({ kind: 'pending', tempId: pending.tempId })}
              />
            ))}
          </div>
        ) : (
          <p className="text-surface-400 text-sm mb-6">{t('news.noBlocks')}</p>
        )}

        {/* Dodaj blok */}
        <div className="flex flex-wrap items-center gap-3 pt-2">
          <Button
            variant="secondary"
            onClick={() =>
              setPendingBlocks((prev) => [
                ...prev,
                { type: 'TEXT', tempId: crypto.randomUUID(), content: '' },
              ])
            }
          >
            <Type className="h-4 w-4 mr-1.5" />
            {t('news.addParagraph')}
          </Button>

          <label className="cursor-pointer">
            <input
              type="file"
              accept={CMS_IMAGE_ACCEPT}
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (!file) return
                const preview = URL.createObjectURL(file)
                setPendingBlocks((prev) => [
                  ...prev,
                  { type: 'IMAGE', tempId: crypto.randomUUID(), source: 'file', file, preview, caption: '' },
                ])
                e.target.value = ''
              }}
            />
            <span className="inline-flex items-center gap-2 px-3 py-2 bg-surface-800 border border-surface-600 rounded text-sm text-surface-200 hover:border-surface-400 transition-colors cursor-pointer font-medium">
              <ImageIcon className="h-4 w-4" />
              {t('news.addImage')}
            </span>
          </label>

          <button
            onClick={() => { setPickerTarget('block'); setShowMediaPicker(true) }}
            className="inline-flex items-center gap-2 px-3 py-2 bg-surface-800 border border-surface-600 rounded text-sm text-surface-200 hover:border-surface-400 transition-colors font-medium"
          >
            <Library className="h-4 w-4" />
            {t('mediaPicker.chooseFromLibrary')}
          </button>

          <button
            onClick={() => { setPickerTarget('block'); setShowGalleryPicker(true) }}
            className="inline-flex items-center gap-2 px-3 py-2 bg-surface-800 border border-surface-600 rounded text-sm text-surface-200 hover:border-surface-400 transition-colors font-medium"
          >
            <Images className="h-4 w-4" />
            {t('galleryPicker.chooseFromGallery')}
          </button>

          <button
            onClick={() =>
              setPendingBlocks((prev) => [
                ...prev,
                { type: 'VIDEO_EMBED', tempId: crypto.randomUUID(), url: '' },
              ])
            }
            className="inline-flex items-center gap-2 px-3 py-2 bg-surface-800 border border-surface-600 rounded text-sm text-surface-200 hover:border-surface-400 transition-colors font-medium"
          >
            <Video className="h-4 w-4" />
            {t('news.addVideo')}
          </button>

          <button
            onClick={() => setAlbumPickerTarget({ kind: 'new' })}
            className="inline-flex items-center gap-2 px-3 py-2 bg-surface-800 border border-surface-600 rounded text-sm text-surface-200 hover:border-surface-400 transition-colors font-medium"
          >
            <GalleryHorizontal className="h-4 w-4" />
            {t('news.addAlbum')}
          </button>
        </div>
      </section>

      {/* Sticky bottom bar */}
      <div className="fixed bottom-0 left-0 right-0 z-40 bg-surface-900/95 backdrop-blur border-t border-surface-700 px-6 py-4">
        <div className="max-w-5xl mx-auto flex items-center justify-end gap-4">
          <div className="flex items-center gap-3">
            {saveError && (
              <span className="text-sm text-red-400 max-w-xs truncate" title={saveError}>
                ⚠ {saveError}
              </span>
            )}
            {isDirty && !saveError && (
              <span className="text-xs text-amber-400 hidden sm:block">
                {t('news.unsavedChanges')}
              </span>
            )}
            <Button
              variant="ghost"
              onClick={() => setShowPreview(true)}
            >
              <Eye className="h-4 w-4 mr-1.5" />
              {t('news.preview')}
            </Button>
            <Button
              variant="ghost"
              onClick={handleCancel}
              disabled={!isDirty || isSaving}
            >
              <X className="h-4 w-4 mr-1.5" />
              {t('news.cancel')}
            </Button>
            <span className="text-[10px] text-surface-500 hidden sm:block">{t('editorUi.publishAffectsAll')}</span>
            {published ? (
              <>
                <Button
                  variant="secondary"
                  onClick={() => unpublishMutation.mutate()}
                  disabled={unpublishMutation.isPending || isSaving}
                >
                  <EyeOff className="h-4 w-4 mr-1.5" />
                  {t('news.unpublish')}
                </Button>
                <Button
                  onClick={handleSave}
                  disabled={!isDirty || isSaving}
                  loading={isSaving}
                >
                  <Save className="h-4 w-4 mr-1.5" />
                  {t('news.saveChanges')}
                </Button>
              </>
            ) : (
              <>
                <Button
                  variant="secondary"
                  onClick={handleSave}
                  disabled={!isDirty || isSaving}
                  loading={isSaving}
                >
                  <Save className="h-4 w-4 mr-1.5" />
                  {t('news.saveDraft')}
                </Button>
                <Button
                  onClick={handlePublish}
                  disabled={publishMutation.isPending || isSaving}
                  loading={publishMutation.isPending || isSaving}
                >
                  <Eye className="h-4 w-4 mr-1.5" />
                  {t('news.publish')}
                </Button>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Confirm: delete block */}
      <ConfirmModal
        isOpen={!!deleteBlockConfirmId}
        title={t('news.deleteBlockConfirmTitle')}
        message={t('news.deleteBlockConfirmMessage')}
        confirmText={t('news.delete')}
        onConfirm={() => deleteBlockConfirmId && deleteBlockMutation.mutate(deleteBlockConfirmId)}
        onClose={() => setDeleteBlockConfirmId(null)}
      />

      {/* Confirm: delete the thumbnail */}
      <ConfirmModal
        isOpen={deleteThumbnailConfirm}
        title={t('news.deleteThumbnailConfirmTitle')}
        message={t('news.deleteThumbnailConfirmMessage')}
        confirmText={t('news.deleteThumbnail')}
        onConfirm={() => deleteThumbnailMutation.mutate()}
        onClose={() => setDeleteThumbnailConfirm(false)}
      />

      {/* Confirm: leave without saving */}
      <ConfirmModal
        isOpen={showExitConfirm}
        title={t('news.exitConfirmTitle')}
        message={t('news.exitConfirmMessage')}
        confirmText={t('news.exitConfirm')}
        onConfirm={() => {
          draft.discard()
          onBack()
        }}
        onClose={() => setShowExitConfirm(false)}
      />

      {/* Confirm: delete a single language version */}
      <ConfirmModal
        isOpen={deleteSingleConfirm}
        title={t('news.deleteSingleConfirmTitle')}
        message={t('news.deleteSingleConfirmMessage', { lang: detail.language.toUpperCase() })}
        confirmText={t('news.delete')}
        onConfirm={() => deleteSingleMutation.mutate()}
        onClose={() => setDeleteSingleConfirm(false)}
      />

      {/* Modal: media sync to translations */}
      <Modal
        isOpen={showSyncMediaModal}
        onClose={() => { setShowSyncMediaModal(false); setPendingSyncAction(null) }}
        title={t('news.syncMediaModalTitle')}
      >
        <p className="text-sm text-surface-300 mb-4">{t('news.syncMediaModalMessage')}</p>
        {saveError && (
          <p className="text-sm text-red-400 mb-4">{saveError}</p>
        )}
        <div className="flex gap-3 justify-end">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => { setShowSyncMediaModal(false); setPendingSyncAction(null) }}
            disabled={isSaving}
          >
            {t('news.syncMediaCancel')}
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => executeSave(false)}
            disabled={isSaving}
            loading={isSaving && !syncMediaMutation.isPending}
          >
            {t('news.syncMediaSkip')}
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => executeSave(true)}
            disabled={isSaving}
            loading={syncMediaMutation.isPending}
          >
            {t('news.syncMediaConfirm')}
          </Button>
        </div>
      </Modal>

      {/* Article preview */}
      <NewsPreviewModal
        isOpen={showPreview}
        onClose={() => setShowPreview(false)}
        title={title}
        excerpt={excerpt}
        thumbnailUrl={thumbnailPreview ?? thumbnailFromLibrary ?? detail.thumbnailUrl ?? null}
        focalPoint={focalPoint}
        blocks={previewBlocks}
      />

      {/* Media picker */}
      <MediaPickerModal
        isOpen={showMediaPicker}
        onClose={() => setShowMediaPicker(false)}
        onSelect={(asset) => {
          if (pickerTarget === 'thumbnail') {
            if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
            setThumbnailFile(null)
            setThumbnailPreview(null)
            setThumbnailFromLibrary(asset.url)
          } else {
            setPendingBlocks((prev) => [
              ...prev,
              { type: 'IMAGE', tempId: crypto.randomUUID(), source: 'library', imageUrl: asset.url, caption: '' },
            ])
          }
        }}
      />

      {/* Album picker (gallery block) */}
      {albumPickerTarget !== null && (
        <AlbumPickerModal
          isOpen
          onClose={() => setAlbumPickerTarget(null)}
          onSelect={(album) => applyPickedAlbum(album.id)}
          selectedId={albumPickerSelectedId()}
        />
      )}

      {/* Gallery picker */}
      <GalleryPickerModal
        isOpen={showGalleryPicker}
        onClose={() => setShowGalleryPicker(false)}
        onSelect={(photoUrl) => {
          if (pickerTarget === 'thumbnail') {
            if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
            setThumbnailFile(null)
            setThumbnailPreview(null)
            setThumbnailFromLibrary(photoUrl)
          } else {
            setPendingBlocks((prev) => [
              ...prev,
              { type: 'IMAGE', tempId: crypto.randomUUID(), source: 'gallery', imageUrl: photoUrl, caption: '' },
            ])
          }
        }}
      />

    </div>
  )
}

// ==================== Existing block editor ====================

function BlockEditor({
  block,
  editState,
  onChange,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onDelete,
  onPickAlbum,
}: {
  block: ContentBlockAdmin
  editState: BlockEdit
  onChange: (edit: BlockEdit) => void
  isFirst: boolean
  isLast: boolean
  onMoveUp: () => void
  onMoveDown: () => void
  onDelete: () => void
  onPickAlbum: () => void
}) {
  const { t } = useTranslation('admin')

  const isModified = blockEditChanged(block, editState)

  return (
    <div className={clsx(
      'border rounded-lg p-4',
      isModified ? 'border-amber-500/50' : 'border-surface-600'
    )}>
      {/* Block header */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <BlockTypeBadge type={block.blockType} />
          {isModified && (
            <span className="text-xs text-amber-400">{t('news.modified')}</span>
          )}
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={onMoveUp}
            disabled={isFirst}
            className="p-1 text-surface-400 hover:text-surface-100 disabled:opacity-30 transition-colors"
            title={t('news.moveUp')}
          >
            <ChevronUp className="h-4 w-4" />
          </button>
          <button
            onClick={onMoveDown}
            disabled={isLast}
            className="p-1 text-surface-400 hover:text-surface-100 disabled:opacity-30 transition-colors"
            title={t('news.moveDown')}
          >
            <ChevronDown className="h-4 w-4" />
          </button>
          <button
            onClick={onDelete}
            className="p-1 text-surface-400 hover:text-red-400 transition-colors"
            title={t('news.deleteBlock')}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Content */}
      {block.blockType === 'TEXT' ? (
        <RichTextEditor
          value={editState.content ?? ''}
          onChange={(v) => onChange({ ...editState, content: v })}
        />
      ) : block.blockType === 'VIDEO_EMBED' ? (
        <div className="space-y-2">
          <input
            type="text"
            value={editState.content ?? ''}
            onChange={(e) => onChange({ ...editState, content: e.target.value })}
            placeholder={t('news.videoUrlPlaceholder')}
            className="w-full bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500 text-sm"
          />
          <p className="text-xs text-surface-500">{t('news.videoUrlHint')}</p>
        </div>
      ) : block.blockType === 'GALLERY' ? (
        <GalleryBlockFields
          albumId={editState.albumId}
          caption={editState.caption ?? ''}
          onCaptionChange={(caption) => onChange({ ...editState, caption })}
          onPickAlbum={onPickAlbum}
        />
      ) : (
        <div className="space-y-2">
          {block.imageUrl && (
            <div className="flex justify-center">
              <img
                src={block.imageUrl}
                alt={editState.caption ?? ''}
                className="max-h-72 max-w-full w-auto h-auto rounded border border-surface-600"
              />
            </div>
          )}
          <input
            type="text"
            value={editState.caption ?? ''}
            onChange={(e) => onChange({ ...editState, caption: e.target.value })}
            placeholder={t('news.captionPlaceholder')}
            className="w-full bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500 text-sm"
          />
        </div>
      )}
    </div>
  )
}

// ==================== Pending new block preview ====================

function PendingBlockItem({
  block,
  onChange,
  onDelete,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onPickAlbum,
}: {
  block: PendingBlock
  onChange: (updated: Partial<PendingBlock>) => void
  onDelete: () => void
  isFirst: boolean
  isLast: boolean
  onMoveUp: () => void
  onMoveDown: () => void
  onPickAlbum: () => void
}) {
  const { t } = useTranslation('admin')

  return (
    <div className="border border-primary-500/40 rounded-lg p-4 bg-primary-950/10">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <BlockTypeBadge type={block.type} />
          <span className="text-xs text-primary-400">{t('news.pendingBadge')}</span>
        </div>
        <div className="flex items-center gap-1">
          <button
            onClick={onMoveUp}
            disabled={isFirst}
            className="p-1 text-surface-400 hover:text-surface-100 disabled:opacity-30 transition-colors"
            title={t('news.moveUp')}
          >
            <ChevronUp className="h-4 w-4" />
          </button>
          <button
            onClick={onMoveDown}
            disabled={isLast}
            className="p-1 text-surface-400 hover:text-surface-100 disabled:opacity-30 transition-colors"
            title={t('news.moveDown')}
          >
            <ChevronDown className="h-4 w-4" />
          </button>
          <button
            onClick={onDelete}
            className="p-1 text-surface-400 hover:text-red-400 transition-colors"
            title={t('news.deleteBlock')}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      {block.type === 'TEXT' ? (
        <RichTextEditor
          value={block.content}
          onChange={(v) => onChange({ content: v })}
        />
      ) : block.type === 'VIDEO_EMBED' ? (
        <div className="space-y-2">
          <input
            type="text"
            value={block.url}
            onChange={(e) => onChange({ url: e.target.value })}
            placeholder={t('news.videoUrlPlaceholder')}
            className="w-full bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500 text-sm"
          />
          <p className="text-xs text-surface-500">{t('news.videoUrlHint')}</p>
        </div>
      ) : block.type === 'GALLERY' ? (
        <GalleryBlockFields
          albumId={block.albumId}
          caption={block.caption}
          onCaptionChange={(caption) => onChange({ caption })}
          onPickAlbum={onPickAlbum}
        />
      ) : (
        <div className="space-y-2">
          {(block.imageUrl ?? block.preview) && (
            <div className="flex justify-center">
              <img
                src={block.imageUrl ?? block.preview}
                alt={block.caption}
                className="max-h-72 max-w-full w-auto h-auto rounded border border-surface-600"
              />
            </div>
          )}
          <input
            type="text"
            value={block.caption}
            onChange={(e) => onChange({ caption: e.target.value })}
            placeholder={t('news.captionPlaceholder')}
            className="w-full bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500 text-sm"
          />
        </div>
      )}
    </div>
  )
}

// ==================== Shared block pieces ====================

function BlockTypeBadge({ type }: { type: PendingBlock['type'] | ContentBlockAdmin['blockType'] }) {
  const { t } = useTranslation('admin')
  return (
    <span className={clsx(
      'text-xs px-2 py-0.5 rounded-full',
      type === 'TEXT' ? 'bg-blue-900/40 text-blue-400'
      : type === 'VIDEO_EMBED' ? 'bg-orange-900/40 text-orange-400'
      : type === 'GALLERY' ? 'bg-emerald-900/40 text-emerald-400'
      : 'bg-purple-900/40 text-purple-400'
    )}>
      {type === 'TEXT' ? (
        <><Type className="h-3 w-3 inline mr-1" />{t('news.blockTypeText')}</>
      ) : type === 'VIDEO_EMBED' ? (
        <><Video className="h-3 w-3 inline mr-1" />{t('news.blockTypeVideo')}</>
      ) : type === 'GALLERY' ? (
        <><GalleryHorizontal className="h-3 w-3 inline mr-1" />{t('news.blockTypeGallery')}</>
      ) : (
        <><ImageIcon className="h-3 w-3 inline mr-1" />{t('news.blockTypeImage')}</>
      )}
    </span>
  )
}

/** A gallery block in the editor: the grid as readers will see it, a way to swap the album, a caption. */
function GalleryBlockFields({ albumId, caption, onCaptionChange, onPickAlbum }: {
  albumId: string | undefined
  caption: string
  onCaptionChange: (caption: string) => void
  onPickAlbum: () => void
}) {
  const { t } = useTranslation('admin')
  return (
    <div className="space-y-2">
      <AdminGalleryBlockPreview albumId={albumId} />
      <div className="flex flex-col sm:flex-row gap-2">
        <input
          type="text"
          value={caption}
          onChange={(e) => onCaptionChange(e.target.value)}
          placeholder={t('news.captionPlaceholder')}
          maxLength={1000}
          // A block whose album was deleted has nothing to save a caption against (the endpoint
          // needs an album), so a caption typed here would read as unsaved work and then vanish.
          disabled={!albumId}
          className="flex-1 bg-surface-700 border border-surface-600 rounded px-3 py-2 text-surface-100 focus:outline-none focus:border-primary-500 text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        />
        <Button variant="secondary" onClick={onPickAlbum}>
          <GalleryHorizontal className="h-4 w-4 mr-1.5" />
          {t('news.changeAlbum')}
        </Button>
      </div>
    </div>
  )
}
