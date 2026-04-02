import { useState, useEffect, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  Newspaper,
  Plus,
  Pencil,
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
} from 'lucide-react'
import { adminNewsApi } from '../../api/client'
import type {
  NewsAdmin,
  NewsDetailAdmin,
  ContentBlockAdmin,
  CreateNewsRequest,
} from '../../types'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { QueryError } from '../../components/ui/QueryError'
import clsx from 'clsx'

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
  file: File
  preview: string
  caption: string
}

type PendingBlock = PendingTextBlock | PendingImageBlock

// ---------- Main panel ----------

export function AdminNewsPanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [view, setView] = useState<View>('list')
  const [selectedNewsId, setSelectedNewsId] = useState<string | null>(null)

  const { data: articles, isLoading, error } = useQuery({
    queryKey: ['admin', 'news'],
    queryFn: () => adminNewsApi.getAll(),
  })

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['admin', 'news', selectedNewsId],
    queryFn: () => adminNewsApi.getById(selectedNewsId!),
    enabled: !!selectedNewsId && view === 'edit',
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateNewsRequest) => adminNewsApi.create(data),
    onSuccess: (news) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'news'] })
      setSelectedNewsId(news.id)
      setView('edit')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminNewsApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'news'] })
      setDeleteConfirmId(null)
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

  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)

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
        <h2 className="text-xl font-semibold text-dark-100">{t('news.title')}</h2>
        <Button
          onClick={() => createMutation.mutate({ title: t('news.newArticleDefaultTitle') })}
          disabled={createMutation.isPending}
        >
          <Plus className="h-4 w-4 mr-2" />
          {t('news.addArticle')}
        </Button>
      </div>

      {!articles || articles.content.length === 0 ? (
        <div className="text-center text-dark-400 py-12">
          {t('news.noArticles')}
        </div>
      ) : (
        <div className="space-y-3">
          {articles.content.map((article) => (
            <ArticleRow
              key={article.id}
              article={article}
              onEdit={() => {
                setSelectedNewsId(article.id)
                setView('edit')
              }}
              onDelete={() => setDeleteConfirmId(article.id)}
              onPublish={() => publishMutation.mutate(article.id)}
              onUnpublish={() => unpublishMutation.mutate(article.id)}
            />
          ))}
        </div>
      )}

      <ConfirmModal
        isOpen={!!deleteConfirmId}
        title={t('news.deleteConfirmTitle')}
        message={t('news.deleteConfirmMessage')}
        confirmText={t('news.delete')}
        onConfirm={() => deleteConfirmId && deleteMutation.mutate(deleteConfirmId)}
        onClose={() => setDeleteConfirmId(null)}
      />
    </div>
  )
}

// ==================== Wiersz artykułu w liście ====================

function ArticleRow({
  article,
  onEdit,
  onDelete,
  onPublish,
  onUnpublish,
}: {
  article: NewsAdmin
  onEdit: () => void
  onDelete: () => void
  onPublish: () => void
  onUnpublish: () => void
}) {
  const { t } = useTranslation('admin')

  return (
    <div className="flex items-center gap-4 bg-dark-800 border border-dark-700 rounded-lg p-4">
      <div className="flex-shrink-0 w-16 h-16 bg-dark-700 rounded overflow-hidden">
        {article.thumbnailUrl ? (
          <img src={article.thumbnailUrl} alt={article.title} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Newspaper className="h-6 w-6 text-dark-500" />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <span
            className={clsx(
              'text-xs px-2 py-0.5 rounded-full',
              article.published
                ? 'bg-green-900/40 text-green-400'
                : 'bg-dark-600 text-dark-400'
            )}
          >
            {article.published ? t('news.published') : t('news.draft')}
          </span>
          {article.publishedAt && (
            <span className="text-xs text-dark-400">
              {new Date(article.publishedAt).toLocaleDateString('pl-PL')}
            </span>
          )}
        </div>
        <p className="font-medium text-dark-100 truncate">{article.title}</p>
        {article.excerpt && (
          <p className="text-sm text-dark-400 truncate">{article.excerpt}</p>
        )}
      </div>

      <div className="flex items-center gap-2 flex-shrink-0">
        <button
          onClick={article.published ? onUnpublish : onPublish}
          title={article.published ? t('news.unpublish') : t('news.publish')}
          className="p-2 text-dark-400 hover:text-dark-100 transition-colors"
        >
          {article.published ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
        <button
          onClick={onEdit}
          title={t('news.edit')}
          className="p-2 text-dark-400 hover:text-dark-100 transition-colors"
        >
          <Pencil className="h-4 w-4" />
        </button>
        <button
          onClick={onDelete}
          title={t('news.delete')}
          className="p-2 text-dark-400 hover:text-red-400 transition-colors"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>
    </div>
  )
}

// ==================== Widok edycji ====================

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

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'news'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'news', newsId] })
  }, [queryClient, newsId])

  // ---------- Meta state ----------
  const [title, setTitle] = useState(detail.title)
  const [excerpt, setExcerpt] = useState(detail.excerpt ?? '')

  // ---------- Thumbnail state ----------
  const [thumbnailFile, setThumbnailFile] = useState<File | null>(null)
  const [thumbnailPreview, setThumbnailPreview] = useState<string | null>(null)

  useEffect(() => {
    return () => {
      if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
    }
  }, [thumbnailPreview])

  // ---------- Block edits (existing blocks, by id) ----------
  const [blockEdits, setBlockEdits] = useState<Record<string, { content?: string; caption?: string }>>(() => {
    const map: Record<string, { content?: string; caption?: string }> = {}
    detail.blocks.forEach((b) => {
      map[b.id] = { content: b.content ?? undefined, caption: b.caption ?? undefined }
    })
    return map
  })

  // Sync new blocks added via invalidate (e.g. after immediate ops) without resetting user edits
  useEffect(() => {
    setBlockEdits((prev) => {
      const next = { ...prev }
      detail.blocks.forEach((b) => {
        if (!(b.id in next)) {
          next[b.id] = { content: b.content ?? undefined, caption: b.caption ?? undefined }
        }
      })
      return next
    })
  }, [detail.blocks])

  // ---------- Pending new blocks ----------
  const [pendingBlocks, setPendingBlocks] = useState<PendingBlock[]>([])

  // Cleanup blob URLs on unmount
  useEffect(() => {
    return () => {
      pendingBlocks.forEach((b) => {
        if (b.type === 'IMAGE') URL.revokeObjectURL(b.preview)
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // ---------- Dirty detection ----------
  const metaDirty = title !== detail.title || excerpt !== (detail.excerpt ?? '')
  const thumbnailDirty = thumbnailFile !== null
  const blockEditsDirty = detail.blocks.some((b) => {
    const edit = blockEdits[b.id]
    if (!edit) return false
    if (b.blockType === 'TEXT') return edit.content !== (b.content ?? undefined)
    if (b.blockType === 'IMAGE') return edit.caption !== (b.caption ?? undefined)
    return false
  })
  const isDirty = metaDirty || thumbnailDirty || blockEditsDirty || pendingBlocks.length > 0

  // ---------- Save all ----------
  const [isSaving, setIsSaving] = useState(false)

  const handleSave = async () => {
    setIsSaving(true)
    try {
      // 1. Meta
      if (metaDirty) {
        await adminNewsApi.updateMeta(newsId, { title, excerpt: excerpt || undefined })
      }

      // 2. Thumbnail
      if (thumbnailFile) {
        await adminNewsApi.uploadThumbnail(newsId, thumbnailFile)
        setThumbnailFile(null)
        if (thumbnailPreview) {
          URL.revokeObjectURL(thumbnailPreview)
          setThumbnailPreview(null)
        }
      }

      // 3. Existing block edits
      await Promise.all(
        detail.blocks
          .filter((b) => {
            const edit = blockEdits[b.id]
            if (!edit) return false
            if (b.blockType === 'TEXT') return edit.content !== (b.content ?? undefined)
            if (b.blockType === 'IMAGE') return edit.caption !== (b.caption ?? undefined)
            return false
          })
          .map((b) => {
            const edit = blockEdits[b.id]
            if (b.blockType === 'TEXT') {
              return adminNewsApi.updateTextBlock(b.id, { content: edit.content ?? '' })
            }
            return adminNewsApi.updateImageBlock(b.id, { caption: edit.caption || undefined })
          })
      )

      // 4. New blocks (sequential to preserve order)
      for (const pending of pendingBlocks) {
        if (pending.type === 'TEXT') {
          await adminNewsApi.addTextBlock(newsId, { content: pending.content })
        } else {
          await adminNewsApi.addImageBlock(newsId, pending.file, pending.caption || undefined)
          URL.revokeObjectURL(pending.preview)
        }
      }
      setPendingBlocks([])
    } finally {
      setIsSaving(false)
      invalidate()
    }
  }

  // ---------- Publish (save if dirty, then publish) ----------
  const handlePublish = async () => {
    if (isDirty) await handleSave()
    publishMutation.mutate()
  }

  // ---------- Cancel (discard local changes) ----------
  const handleCancel = () => {
    setTitle(detail.title)
    setExcerpt(detail.excerpt ?? '')

    if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
    setThumbnailFile(null)
    setThumbnailPreview(null)

    const reset: Record<string, { content?: string; caption?: string }> = {}
    detail.blocks.forEach((b) => {
      reset[b.id] = { content: b.content ?? undefined, caption: b.caption ?? undefined }
    })
    setBlockEdits(reset)

    pendingBlocks.forEach((b) => {
      if (b.type === 'IMAGE') URL.revokeObjectURL(b.preview)
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

  const published = detail.published

  // ---------- Render ----------
  return (
    <div className="pb-24 space-y-8">
      {/* Powrót do listy */}
      <button
        onClick={handleExit}
        className="inline-flex items-center gap-2 text-dark-300 hover:text-dark-100 transition-colors text-sm"
      >
        <ArrowLeft className="h-4 w-4" />
        {t('news.backToList')}
      </button>

      {/* Sekcja 1: Metadane */}
      <section className="bg-dark-800 border border-dark-700 rounded-lg p-6">
        <div className="flex items-center gap-3 mb-4">
          <h3 className="text-lg font-semibold text-dark-100">{t('news.sectionMeta')}</h3>
          <span className={clsx(
            'text-xs px-2.5 py-1 rounded-full',
            published ? 'bg-green-900/40 text-green-400' : 'bg-dark-600 text-dark-400'
          )}>
            {published ? t('news.published') : t('news.draft')}
          </span>
        </div>
        <div className="space-y-4">
          <div>
            <label className="block text-sm text-dark-300 mb-1">{t('news.titleLabel')}</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-300 mb-1">{t('news.excerptLabel')}</label>
            <textarea
              value={excerpt}
              onChange={(e) => setExcerpt(e.target.value)}
              rows={3}
              className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 resize-none"
            />
          </div>
        </div>
      </section>

      {/* Sekcja 2: Miniaturka */}
      <section className="bg-dark-800 border border-dark-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-dark-100 mb-4">{t('news.sectionThumbnail')}</h3>

        {(thumbnailPreview || detail.thumbnailUrl) && (
          <div className="mb-4 relative inline-block">
            <img
              src={thumbnailPreview ?? detail.thumbnailUrl ?? ''}
              alt="thumbnail"
              className={clsx(
                'w-48 h-32 object-cover rounded-lg border',
                thumbnailPreview ? 'border-primary-500' : 'border-dark-600'
              )}
            />
            {thumbnailPreview && (
              <span className="absolute top-1 left-1 text-xs px-1.5 py-0.5 bg-primary-600 text-white rounded">
                {t('news.pendingBadge')}
              </span>
            )}
          </div>
        )}

        <div className="flex items-center gap-3">
          <label className="cursor-pointer">
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (!file) return
                setThumbnailFile(file)
                if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
                setThumbnailPreview(URL.createObjectURL(file))
              }}
            />
            <span className="inline-flex items-center gap-2 px-3 py-2 bg-dark-700 border border-dark-600 rounded text-sm text-dark-200 hover:border-primary-500 transition-colors cursor-pointer">
              <Upload className="h-4 w-4" />
              {t('news.chooseThumbnail')}
            </span>
          </label>

          {thumbnailFile && (
            <button
              onClick={() => {
                if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
                setThumbnailFile(null)
                setThumbnailPreview(null)
              }}
              className="text-sm text-dark-400 hover:text-dark-100 transition-colors"
            >
              {t('news.cancelThumbnail')}
            </button>
          )}

          {detail.thumbnailUrl && !thumbnailFile && (
            <button
              onClick={() => deleteThumbnailMutation.mutate()}
              disabled={deleteThumbnailMutation.isPending}
              className="text-sm text-red-400 hover:text-red-300 transition-colors"
            >
              {t('news.deleteThumbnail')}
            </button>
          )}
        </div>
      </section>

      {/* Sekcja 3: Bloki treści */}
      <section className="bg-dark-800 border border-dark-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-dark-100 mb-4">{t('news.sectionContent')}</h3>

        {/* Istniejące bloki */}
        {detail.blocks.length > 0 || pendingBlocks.length > 0 ? (
          <div className="space-y-4 mb-6">
            {detail.blocks.map((block, index) => (
              <BlockEditor
                key={block.id}
                block={block}
                editState={blockEdits[block.id] ?? { content: block.content ?? undefined, caption: block.caption ?? undefined }}
                onChange={(edit) => setBlockEdits((prev) => ({ ...prev, [block.id]: edit }))}
                isFirst={index === 0}
                isLast={index === detail.blocks.length - 1 && pendingBlocks.length === 0}
                onMoveUp={() => moveBlockMutation.mutate({ blockId: block.id, direction: 'UP' })}
                onMoveDown={() => moveBlockMutation.mutate({ blockId: block.id, direction: 'DOWN' })}
                onDelete={() => setDeleteBlockConfirmId(block.id)}
              />
            ))}

            {/* Oczekujące nowe bloki */}
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
                  if (pending.type === 'IMAGE') URL.revokeObjectURL(pending.preview)
                  setPendingBlocks((prev) => prev.filter((b) => b.tempId !== pending.tempId))
                }}
                isOnlyPending={pendingBlocks.length === 1}
                pendingIndex={index}
              />
            ))}
          </div>
        ) : (
          <p className="text-dark-400 text-sm mb-6">{t('news.noBlocks')}</p>
        )}

        {/* Dodaj blok */}
        <div className="flex items-center gap-3 pt-2">
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
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (!file) return
                const preview = URL.createObjectURL(file)
                setPendingBlocks((prev) => [
                  ...prev,
                  { type: 'IMAGE', tempId: crypto.randomUUID(), file, preview, caption: '' },
                ])
                e.target.value = ''
              }}
            />
            <span className="inline-flex items-center gap-2 px-3 py-2 bg-dark-800 border border-dark-600 rounded text-sm text-dark-200 hover:border-dark-400 transition-colors cursor-pointer font-medium">
              <ImageIcon className="h-4 w-4" />
              {t('news.addImage')}
            </span>
          </label>
        </div>
      </section>

      {/* Sticky bottom bar */}
      <div className="fixed bottom-0 left-0 right-0 z-40 bg-dark-900/95 backdrop-blur border-t border-dark-700 px-6 py-4">
        <div className="max-w-5xl mx-auto flex items-center justify-end gap-4">
          <div className="flex items-center gap-3">
            {isDirty && (
              <span className="text-xs text-amber-400 hidden sm:block">
                {t('news.unsavedChanges')}
              </span>
            )}
            <Button
              variant="ghost"
              onClick={handleCancel}
              disabled={!isDirty || isSaving}
            >
              <X className="h-4 w-4 mr-1.5" />
              {t('news.cancel')}
            </Button>
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

      {/* Confirm: usuń blok */}
      <ConfirmModal
        isOpen={!!deleteBlockConfirmId}
        title={t('news.deleteBlockConfirmTitle')}
        message={t('news.deleteBlockConfirmMessage')}
        confirmText={t('news.delete')}
        onConfirm={() => deleteBlockConfirmId && deleteBlockMutation.mutate(deleteBlockConfirmId)}
        onClose={() => setDeleteBlockConfirmId(null)}
      />

      {/* Confirm: wyjdź bez zapisu */}
      <ConfirmModal
        isOpen={showExitConfirm}
        title={t('news.exitConfirmTitle')}
        message={t('news.exitConfirmMessage')}
        confirmText={t('news.exitConfirm')}
        onConfirm={() => onBack()}
        onClose={() => setShowExitConfirm(false)}
      />
    </div>
  )
}

// ==================== Edytor istniejącego bloku ====================

function BlockEditor({
  block,
  editState,
  onChange,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onDelete,
}: {
  block: ContentBlockAdmin
  editState: { content?: string; caption?: string }
  onChange: (edit: { content?: string; caption?: string }) => void
  isFirst: boolean
  isLast: boolean
  onMoveUp: () => void
  onMoveDown: () => void
  onDelete: () => void
}) {
  const { t } = useTranslation('admin')

  const isModified =
    block.blockType === 'TEXT'
      ? editState.content !== (block.content ?? undefined)
      : editState.caption !== (block.caption ?? undefined)

  return (
    <div className={clsx(
      'border rounded-lg p-4',
      isModified ? 'border-amber-500/50' : 'border-dark-600'
    )}>
      {/* Header bloku */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className={clsx(
            'text-xs px-2 py-0.5 rounded-full',
            block.blockType === 'TEXT' ? 'bg-blue-900/40 text-blue-400' : 'bg-purple-900/40 text-purple-400'
          )}>
            {block.blockType === 'TEXT' ? (
              <><Type className="h-3 w-3 inline mr-1" />{t('news.blockTypeText')}</>
            ) : (
              <><ImageIcon className="h-3 w-3 inline mr-1" />{t('news.blockTypeImage')}</>
            )}
          </span>
          {isModified && (
            <span className="text-xs text-amber-400">{t('news.modified')}</span>
          )}
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={onMoveUp}
            disabled={isFirst}
            className="p-1 text-dark-400 hover:text-dark-100 disabled:opacity-30 transition-colors"
            title={t('news.moveUp')}
          >
            <ChevronUp className="h-4 w-4" />
          </button>
          <button
            onClick={onMoveDown}
            disabled={isLast}
            className="p-1 text-dark-400 hover:text-dark-100 disabled:opacity-30 transition-colors"
            title={t('news.moveDown')}
          >
            <ChevronDown className="h-4 w-4" />
          </button>
          <button
            onClick={onDelete}
            className="p-1 text-dark-400 hover:text-red-400 transition-colors"
            title={t('news.deleteBlock')}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Zawartość */}
      {block.blockType === 'TEXT' ? (
        <textarea
          value={editState.content ?? ''}
          onChange={(e) => onChange({ ...editState, content: e.target.value })}
          rows={4}
          className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 resize-none text-sm"
        />
      ) : (
        <div className="space-y-2">
          {block.imageUrl && (
            <img
              src={block.imageUrl}
              alt={editState.caption ?? ''}
              className="w-full max-h-48 object-cover rounded"
            />
          )}
          <input
            type="text"
            value={editState.caption ?? ''}
            onChange={(e) => onChange({ ...editState, caption: e.target.value })}
            placeholder={t('news.captionPlaceholder')}
            className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 text-sm"
          />
        </div>
      )}
    </div>
  )
}

// ==================== Podgląd oczekującego nowego bloku ====================

function PendingBlockItem({
  block,
  onChange,
  onDelete,
}: {
  block: PendingBlock
  onChange: (updated: Partial<PendingBlock>) => void
  onDelete: () => void
  isOnlyPending: boolean
  pendingIndex: number
}) {
  const { t } = useTranslation('admin')

  return (
    <div className="border border-primary-500/40 rounded-lg p-4 bg-primary-950/10">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className={clsx(
            'text-xs px-2 py-0.5 rounded-full',
            block.type === 'TEXT' ? 'bg-blue-900/40 text-blue-400' : 'bg-purple-900/40 text-purple-400'
          )}>
            {block.type === 'TEXT' ? (
              <><Type className="h-3 w-3 inline mr-1" />{t('news.blockTypeText')}</>
            ) : (
              <><ImageIcon className="h-3 w-3 inline mr-1" />{t('news.blockTypeImage')}</>
            )}
          </span>
          <span className="text-xs text-primary-400">{t('news.pendingBadge')}</span>
        </div>
        <button
          onClick={onDelete}
          className="p-1 text-dark-400 hover:text-red-400 transition-colors"
          title={t('news.deleteBlock')}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>

      {block.type === 'TEXT' ? (
        <textarea
          value={block.content}
          onChange={(e) => onChange({ content: e.target.value })}
          rows={4}
          className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 resize-none text-sm"
        />
      ) : (
        <div className="space-y-2">
          <img
            src={block.preview}
            alt={block.caption}
            className="w-full max-h-48 object-cover rounded border border-dark-600"
          />
          <input
            type="text"
            value={block.caption}
            onChange={(e) => onChange({ caption: e.target.value })}
            placeholder={t('news.captionPlaceholder')}
            className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 text-sm"
          />
        </div>
      )}
    </div>
  )
}
