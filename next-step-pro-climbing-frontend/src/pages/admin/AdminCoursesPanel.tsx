import { useState, useEffect, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  BookOpen,
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
  Library,
} from 'lucide-react'
import { adminCoursesApi } from '../../api/client'
import type {
  CourseAdmin,
  CourseDetailAdmin,
  ContentBlockAdmin,
  CreateCourseRequest,
} from '../../types'
import { Button } from '../../components/ui/Button'
import { ConfirmModal } from '../../components/ui/ConfirmModal'
import { CoursePreviewModal, type CoursePreviewBlock } from '../../components/ui/CoursePreviewModal'
import { FocalPointEditor } from '../../components/ui/FocalPointEditor'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { MediaPickerModal } from '../../components/ui/MediaPickerModal'
import { QueryError } from '../../components/ui/QueryError'
import { RichTextEditor } from '../../components/ui/RichTextEditor'
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
  caption: string
  source: 'file' | 'library'
  file?: File
  preview?: string
  imageUrl?: string
}

type PendingBlock = PendingTextBlock | PendingImageBlock

// ---------- Main panel ----------

export function AdminCoursesPanel() {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()
  const [view, setView] = useState<View>('list')
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null)
  const [localOrder, setLocalOrder] = useState<CourseAdmin[] | null>(null)
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null)

  const { data: courses, isLoading, error } = useQuery({
    queryKey: ['admin', 'courses'],
    queryFn: () => adminCoursesApi.getAll(),
  })

  const orderedCourses = localOrder ?? (courses ? [...courses].sort((a, b) => a.displayOrder - b.displayOrder) : [])

  const { data: detail, isLoading: detailLoading } = useQuery({
    queryKey: ['admin', 'courses', selectedCourseId],
    queryFn: () => adminCoursesApi.getById(selectedCourseId!),
    enabled: !!selectedCourseId && view === 'edit',
  })

  const createMutation = useMutation({
    mutationFn: (data: CreateCourseRequest) => adminCoursesApi.create(data),
    onSuccess: (course) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'courses'] })
      setSelectedCourseId(course.id)
      setView('edit')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => adminCoursesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'courses'] })
      setDeleteConfirmId(null)
    },
  })

  const publishMutation = useMutation({
    mutationFn: (id: string) => adminCoursesApi.publish(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'courses'] }),
  })

  const unpublishMutation = useMutation({
    mutationFn: (id: string) => adminCoursesApi.unpublish(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'courses'] }),
  })

  const reorderMutation = useMutation({
    mutationFn: (orderedIds: string[]) => adminCoursesApi.reorder(orderedIds),
    onSuccess: () => {
      setLocalOrder(null)
      queryClient.invalidateQueries({ queryKey: ['admin', 'courses'] })
    },
  })

  const handleMove = (index: number, direction: 'up' | 'down') => {
    const newOrder = [...orderedCourses]
    const targetIndex = direction === 'up' ? index - 1 : index + 1
    if (targetIndex < 0 || targetIndex >= newOrder.length) return
    ;[newOrder[index], newOrder[targetIndex]] = [newOrder[targetIndex], newOrder[index]]
    setLocalOrder(newOrder)
    reorderMutation.mutate(newOrder.map(c => c.id))
  }

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

  if (view === 'edit' && selectedCourseId) {
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
        courseId={selectedCourseId}
        detail={detail}
        onBack={() => {
          setView('list')
          setSelectedCourseId(null)
        }}
      />
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-xl font-semibold text-dark-100">{t('courses.title')}</h2>
        <Button
          onClick={() => createMutation.mutate({ title: t('courses.newCourseDefaultTitle') })}
          disabled={createMutation.isPending}
        >
          <Plus className="h-4 w-4 mr-2" />
          {t('courses.addCourse')}
        </Button>
      </div>

      {orderedCourses.length === 0 ? (
        <div className="text-center text-dark-400 py-12">
          {t('courses.noCourses')}
        </div>
      ) : (
        <div className="space-y-3">
          {orderedCourses.map((course, index) => (
            <CourseRow
              key={course.id}
              course={course}
              isFirst={index === 0}
              isLast={index === orderedCourses.length - 1}
              moveDisabled={reorderMutation.isPending}
              onMoveUp={() => handleMove(index, 'up')}
              onMoveDown={() => handleMove(index, 'down')}
              onEdit={() => {
                setSelectedCourseId(course.id)
                setView('edit')
              }}
              onDelete={() => setDeleteConfirmId(course.id)}
              onPublish={() => publishMutation.mutate(course.id)}
              onUnpublish={() => unpublishMutation.mutate(course.id)}
            />
          ))}
        </div>
      )}

      <ConfirmModal
        isOpen={!!deleteConfirmId}
        title={t('courses.deleteConfirmTitle')}
        message={t('courses.deleteConfirmMessage')}
        confirmText={t('courses.delete')}
        onConfirm={() => deleteConfirmId && deleteMutation.mutate(deleteConfirmId)}
        onClose={() => setDeleteConfirmId(null)}
      />
    </div>
  )
}

// ==================== Wiersz kursu w liście ====================

function CourseRow({
  course,
  isFirst,
  isLast,
  moveDisabled,
  onMoveUp,
  onMoveDown,
  onEdit,
  onDelete,
  onPublish,
  onUnpublish,
}: {
  course: CourseAdmin
  isFirst: boolean
  isLast: boolean
  moveDisabled: boolean
  onMoveUp: () => void
  onMoveDown: () => void
  onEdit: () => void
  onDelete: () => void
  onPublish: () => void
  onUnpublish: () => void
}) {
  const { t } = useTranslation('admin')

  return (
    <div className="flex items-center gap-4 bg-dark-800 border border-dark-700 rounded-lg p-4">
      <div className="flex-shrink-0 w-16 h-16 bg-dark-700 rounded overflow-hidden">
        {course.thumbnailUrl ? (
          <img src={course.thumbnailUrl} alt={course.title} className="w-full h-full object-cover" />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <BookOpen className="h-6 w-6 text-dark-500" />
          </div>
        )}
      </div>

      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <span
            className={clsx(
              'text-xs px-2 py-0.5 rounded-full',
              course.published
                ? 'bg-green-900/40 text-green-400'
                : 'bg-dark-600 text-dark-400'
            )}
          >
            {course.published ? t('courses.published') : t('courses.draft')}
          </span>
          {course.publishedAt && (
            <span className="text-xs text-dark-400">
              {new Date(course.publishedAt).toLocaleDateString('pl-PL')}
            </span>
          )}
        </div>
        <p className="font-medium text-dark-100 truncate">{course.title}</p>
        {course.excerpt && (
          <p className="text-sm text-dark-400 truncate">{course.excerpt}</p>
        )}
      </div>

      <div className="flex items-center gap-1 flex-shrink-0">
        <button
          onClick={onMoveUp}
          disabled={isFirst || moveDisabled}
          title={t('courses.moveUp')}
          className="p-2 text-dark-400 hover:text-dark-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <ChevronUp className="h-4 w-4" />
        </button>
        <button
          onClick={onMoveDown}
          disabled={isLast || moveDisabled}
          title={t('courses.moveDown')}
          className="p-2 text-dark-400 hover:text-dark-100 transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
        >
          <ChevronDown className="h-4 w-4" />
        </button>
        <button
          onClick={course.published ? onUnpublish : onPublish}
          title={course.published ? t('courses.unpublish') : t('courses.publish')}
          className="p-2 text-dark-400 hover:text-dark-100 transition-colors"
        >
          {course.published ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
        </button>
        <button
          onClick={onEdit}
          title={t('courses.edit')}
          className="p-2 text-dark-400 hover:text-dark-100 transition-colors"
        >
          <Pencil className="h-4 w-4" />
        </button>
        <button
          onClick={onDelete}
          title={t('courses.delete')}
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
  courseId,
  detail,
  onBack,
}: {
  courseId: string
  detail: CourseDetailAdmin
  onBack: () => void
}) {
  const { t } = useTranslation('admin')
  const queryClient = useQueryClient()

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'courses'] })
    queryClient.invalidateQueries({ queryKey: ['admin', 'courses', courseId] })
  }, [queryClient, courseId])

  // ---------- Meta state ----------
  const [title, setTitle] = useState(detail.title)
  const [excerpt, setExcerpt] = useState(detail.excerpt ?? '')

  // ---------- Thumbnail state ----------
  const [thumbnailFile, setThumbnailFile] = useState<File | null>(null)
  const [thumbnailPreview, setThumbnailPreview] = useState<string | null>(null)
  const [thumbnailFromLibrary, setThumbnailFromLibrary] = useState<string | null>(null)
  const [focalPoint, setFocalPoint] = useState<{ x: number; y: number }>({
    x: detail.thumbnailFocalPointX ?? 0.5,
    y: detail.thumbnailFocalPointY ?? 0.5,
  })

  // ---------- Media picker ----------
  const [showMediaPicker, setShowMediaPicker] = useState(false)
  const [pickerTarget, setPickerTarget] = useState<'thumbnail' | 'block'>('block')

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
  const focalPointDirty = detail.thumbnailUrl != null &&
    (focalPoint.x !== (detail.thumbnailFocalPointX ?? 0.5) ||
     focalPoint.y !== (detail.thumbnailFocalPointY ?? 0.5))
  const blockEditsDirty = detail.blocks.some((b) => {
    const edit = blockEdits[b.id]
    if (!edit) return false
    if (b.blockType === 'TEXT') return edit.content !== (b.content ?? undefined)
    if (b.blockType === 'IMAGE') return edit.caption !== (b.caption ?? undefined)
    return false
  })
  const isDirty = metaDirty || thumbnailDirty || focalPointDirty || blockEditsDirty || pendingBlocks.length > 0

  // ---------- Preview ----------
  const [showPreview, setShowPreview] = useState(false)

  const previewBlocks: CoursePreviewBlock[] = [
    ...detail.blocks.map((b) => ({
      tempId: b.id,
      type: b.blockType as 'TEXT' | 'IMAGE',
      content: blockEdits[b.id]?.content ?? b.content ?? undefined,
      imageUrl: b.imageUrl ?? undefined,
      caption: blockEdits[b.id]?.caption ?? b.caption ?? undefined,
    })),
    ...pendingBlocks.map((b) => ({
      tempId: b.tempId,
      type: b.type,
      content: b.type === 'TEXT' ? b.content : undefined,
      imageUrl: b.type === 'IMAGE' ? (b.imageUrl ?? b.preview) : undefined,
      caption: b.type === 'IMAGE' ? b.caption : undefined,
    })),
  ]

  // ---------- Save all ----------
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const handleSave = async () => {
    setIsSaving(true)
    setSaveError(null)
    try {
      // 1. Meta
      if (metaDirty) {
        await adminCoursesApi.updateMeta(courseId, { title, excerpt: excerpt || undefined })
      }

      // 2. Thumbnail + focal point (zawsze razem gdy nowy plik)
      if (thumbnailFromLibrary) {
        await adminCoursesApi.setThumbnailUrl(courseId, thumbnailFromLibrary)
        await adminCoursesApi.updateThumbnailFocalPoint(courseId, focalPoint.x, focalPoint.y)
        setThumbnailFromLibrary(null)
      } else if (thumbnailFile) {
        await adminCoursesApi.uploadThumbnail(courseId, thumbnailFile)
        await adminCoursesApi.updateThumbnailFocalPoint(courseId, focalPoint.x, focalPoint.y)
        setThumbnailFile(null)
        if (thumbnailPreview) {
          URL.revokeObjectURL(thumbnailPreview)
          setThumbnailPreview(null)
        }
      } else if (focalPointDirty) {
        // 3. Focal point dla istniejącej miniaturki (zmiana bez nowego pliku)
        await adminCoursesApi.updateThumbnailFocalPoint(courseId, focalPoint.x, focalPoint.y)
      }

      // 4. Existing block edits
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
              return adminCoursesApi.updateTextBlock(b.id, { content: edit.content ?? '' })
            }
            return adminCoursesApi.updateImageBlock(b.id, { caption: edit.caption || undefined })
          })
      )

      // 4. New blocks (sequential to preserve order)
      for (const pending of pendingBlocks) {
        if (pending.type === 'TEXT') {
          await adminCoursesApi.addTextBlock(courseId, { content: pending.content })
        } else if (pending.source === 'library' && pending.imageUrl) {
          await adminCoursesApi.addImageBlockFromUrl(courseId, pending.imageUrl, pending.caption || undefined)
        } else if (pending.source === 'file' && pending.file) {
          await adminCoursesApi.addImageBlock(courseId, pending.file, pending.caption || undefined)
          if (pending.preview) URL.revokeObjectURL(pending.preview)
        }
      }
      setPendingBlocks([])
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Błąd zapisu')
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
    setThumbnailFromLibrary(null)
    setFocalPoint({ x: detail.thumbnailFocalPointX ?? 0.5, y: detail.thumbnailFocalPointY ?? 0.5 })

    const reset: Record<string, { content?: string; caption?: string }> = {}
    detail.blocks.forEach((b) => {
      reset[b.id] = { content: b.content ?? undefined, caption: b.caption ?? undefined }
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

  // ---------- Immediate structural mutations (move/delete) ----------
  const [deleteBlockConfirmId, setDeleteBlockConfirmId] = useState<string | null>(null)

  const moveBlockMutation = useMutation({
    mutationFn: ({ blockId, direction }: { blockId: string; direction: 'UP' | 'DOWN' }) =>
      adminCoursesApi.moveBlock(blockId, direction),
    onSuccess: () => invalidate(),
  })

  const deleteBlockMutation = useMutation({
    mutationFn: (blockId: string) => adminCoursesApi.deleteBlock(blockId),
    onSuccess: () => {
      invalidate()
      setDeleteBlockConfirmId(null)
    },
  })

  const deleteThumbnailMutation = useMutation({
    mutationFn: () => adminCoursesApi.deleteThumbnail(courseId),
    onSuccess: () => invalidate(),
  })

  const publishMutation = useMutation({
    mutationFn: () => adminCoursesApi.publish(courseId),
    onSuccess: () => invalidate(),
  })

  const unpublishMutation = useMutation({
    mutationFn: () => adminCoursesApi.unpublish(courseId),
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
        {t('courses.backToList')}
      </button>

      {/* Sekcja 1: Metadane */}
      <section className="bg-dark-800 border border-dark-700 rounded-lg p-6">
        <div className="flex items-center gap-3 mb-4">
          <h3 className="text-lg font-semibold text-dark-100">{t('courses.sectionMeta')}</h3>
          <span className={clsx(
            'text-xs px-2.5 py-1 rounded-full',
            published ? 'bg-green-900/40 text-green-400' : 'bg-dark-600 text-dark-400'
          )}>
            {published ? t('courses.published') : t('courses.draft')}
          </span>
        </div>
        <div className="space-y-4">
          <div>
            <label className="block text-sm text-dark-300 mb-1">{t('courses.titleLabel')}</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm text-dark-300 mb-1">{t('courses.excerptLabel')}</label>
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
        <h3 className="text-lg font-semibold text-dark-100 mb-4">{t('courses.sectionThumbnail')}</h3>

        {(thumbnailPreview || thumbnailFromLibrary || detail.thumbnailUrl) && (
          <div className="mb-4 relative">
            <FocalPointEditor
              imageUrl={thumbnailPreview ?? thumbnailFromLibrary ?? detail.thumbnailUrl ?? ''}
              value={focalPoint}
              onChange={setFocalPoint}
              aspectRatio="3/2"
              className="w-48"
            />
            {(thumbnailPreview || thumbnailFromLibrary) && (
              <span className="absolute top-1 left-1 text-xs px-1.5 py-0.5 bg-primary-600 text-white rounded z-10 pointer-events-none">
                {t('courses.pendingBadge')}
              </span>
            )}
          </div>
        )}

        <div className="flex items-center gap-3 flex-wrap">
          <label className="cursor-pointer">
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
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
            <span className="inline-flex items-center gap-2 px-3 py-2 bg-dark-700 border border-dark-600 rounded text-sm text-dark-200 hover:border-primary-500 transition-colors cursor-pointer">
              <Upload className="h-4 w-4" />
              {t('courses.chooseThumbnail')}
            </span>
          </label>

          <button
            onClick={() => { setPickerTarget('thumbnail'); setShowMediaPicker(true) }}
            className="inline-flex items-center gap-2 px-3 py-2 bg-dark-700 border border-dark-600 rounded text-sm text-dark-200 hover:border-primary-500 transition-colors"
          >
            <Library className="h-4 w-4" />
            {t('mediaPicker.chooseThumbnailFromLibrary')}
          </button>

          {(thumbnailFile || thumbnailFromLibrary) && (
            <button
              onClick={() => {
                if (thumbnailPreview) URL.revokeObjectURL(thumbnailPreview)
                setThumbnailFile(null)
                setThumbnailPreview(null)
                setThumbnailFromLibrary(null)
              }}
              className="text-sm text-dark-400 hover:text-dark-100 transition-colors"
            >
              {t('courses.cancelThumbnail')}
            </button>
          )}

          {detail.thumbnailUrl && !thumbnailFile && !thumbnailFromLibrary && (
            <button
              onClick={() => deleteThumbnailMutation.mutate()}
              disabled={deleteThumbnailMutation.isPending}
              className="text-sm text-red-400 hover:text-red-300 transition-colors"
            >
              {t('courses.deleteThumbnail')}
            </button>
          )}
        </div>
      </section>

      {/* Sekcja 3: Bloki treści */}
      <section className="bg-dark-800 border border-dark-700 rounded-lg p-6">
        <h3 className="text-lg font-semibold text-dark-100 mb-4">{t('courses.sectionContent')}</h3>

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
                  if (pending.type === 'IMAGE' && pending.source === 'file' && pending.preview) URL.revokeObjectURL(pending.preview)
                  setPendingBlocks((prev) => prev.filter((b) => b.tempId !== pending.tempId))
                }}
                isOnlyPending={pendingBlocks.length === 1}
                pendingIndex={index}
              />
            ))}
          </div>
        ) : (
          <p className="text-dark-400 text-sm mb-6">{t('courses.noBlocks')}</p>
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
            {t('courses.addParagraph')}
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
                  { type: 'IMAGE', tempId: crypto.randomUUID(), source: 'file', file, preview, caption: '' },
                ])
                e.target.value = ''
              }}
            />
            <span className="inline-flex items-center gap-2 px-3 py-2 bg-dark-800 border border-dark-600 rounded text-sm text-dark-200 hover:border-dark-400 transition-colors cursor-pointer font-medium">
              <ImageIcon className="h-4 w-4" />
              {t('courses.addImage')}
            </span>
          </label>

          <button
            onClick={() => { setPickerTarget('block'); setShowMediaPicker(true) }}
            className="inline-flex items-center gap-2 px-3 py-2 bg-dark-800 border border-dark-600 rounded text-sm text-dark-200 hover:border-dark-400 transition-colors font-medium"
          >
            <Library className="h-4 w-4" />
            {t('mediaPicker.chooseFromLibrary')}
          </button>
        </div>
      </section>

      {/* Sticky bottom bar */}
      <div className="fixed bottom-0 left-0 right-0 z-40 bg-dark-900/95 backdrop-blur border-t border-dark-700 px-6 py-4">
        <div className="max-w-5xl mx-auto flex items-center justify-end gap-4">
          <div className="flex items-center gap-3">
            {saveError && (
              <span className="text-sm text-red-400 max-w-xs truncate" title={saveError}>
                ⚠ {saveError}
              </span>
            )}
            {isDirty && !saveError && (
              <span className="text-xs text-amber-400 hidden sm:block">
                {t('courses.unsavedChanges')}
              </span>
            )}
            <Button
              variant="ghost"
              onClick={() => setShowPreview(true)}
            >
              <Eye className="h-4 w-4 mr-1.5" />
              {t('courses.preview')}
            </Button>
            <Button
              variant="ghost"
              onClick={handleCancel}
              disabled={!isDirty || isSaving}
            >
              <X className="h-4 w-4 mr-1.5" />
              {t('courses.cancel')}
            </Button>
            {published ? (
              <>
                <Button
                  variant="secondary"
                  onClick={() => unpublishMutation.mutate()}
                  disabled={unpublishMutation.isPending || isSaving}
                >
                  <EyeOff className="h-4 w-4 mr-1.5" />
                  {t('courses.unpublish')}
                </Button>
                <Button
                  onClick={handleSave}
                  disabled={!isDirty || isSaving}
                  loading={isSaving}
                >
                  <Save className="h-4 w-4 mr-1.5" />
                  {t('courses.saveChanges')}
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
                  {t('courses.saveDraft')}
                </Button>
                <Button
                  onClick={handlePublish}
                  disabled={publishMutation.isPending || isSaving}
                  loading={publishMutation.isPending || isSaving}
                >
                  <Eye className="h-4 w-4 mr-1.5" />
                  {t('courses.publish')}
                </Button>
              </>
            )}
          </div>
        </div>
      </div>

      {/* Confirm: usuń blok */}
      <ConfirmModal
        isOpen={!!deleteBlockConfirmId}
        title={t('courses.deleteBlockConfirmTitle')}
        message={t('courses.deleteBlockConfirmMessage')}
        confirmText={t('courses.delete')}
        onConfirm={() => deleteBlockConfirmId && deleteBlockMutation.mutate(deleteBlockConfirmId)}
        onClose={() => setDeleteBlockConfirmId(null)}
      />

      {/* Confirm: wyjdź bez zapisu */}
      <ConfirmModal
        isOpen={showExitConfirm}
        title={t('courses.exitConfirmTitle')}
        message={t('courses.exitConfirmMessage')}
        confirmText={t('courses.exitConfirm')}
        onConfirm={() => onBack()}
        onClose={() => setShowExitConfirm(false)}
      />

      {/* Podgląd kursu */}
      <CoursePreviewModal
        isOpen={showPreview}
        onClose={() => setShowPreview(false)}
        title={title}
        excerpt={excerpt}
        thumbnailUrl={thumbnailPreview ?? thumbnailFromLibrary ?? detail.thumbnailUrl ?? null}
        focalPoint={(!thumbnailPreview && !thumbnailFromLibrary) ? focalPoint : undefined}
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
              <><Type className="h-3 w-3 inline mr-1" />{t('courses.blockTypeText')}</>
            ) : (
              <><ImageIcon className="h-3 w-3 inline mr-1" />{t('courses.blockTypeImage')}</>
            )}
          </span>
          {isModified && (
            <span className="text-xs text-amber-400">{t('courses.modified')}</span>
          )}
        </div>

        <div className="flex items-center gap-1">
          <button
            onClick={onMoveUp}
            disabled={isFirst}
            className="p-1 text-dark-400 hover:text-dark-100 disabled:opacity-30 transition-colors"
            title={t('courses.moveUp')}
          >
            <ChevronUp className="h-4 w-4" />
          </button>
          <button
            onClick={onMoveDown}
            disabled={isLast}
            className="p-1 text-dark-400 hover:text-dark-100 disabled:opacity-30 transition-colors"
            title={t('courses.moveDown')}
          >
            <ChevronDown className="h-4 w-4" />
          </button>
          <button
            onClick={onDelete}
            className="p-1 text-dark-400 hover:text-red-400 transition-colors"
            title={t('courses.deleteBlock')}
          >
            <Trash2 className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Zawartość */}
      {block.blockType === 'TEXT' ? (
        <RichTextEditor
          value={editState.content ?? ''}
          onChange={(v) => onChange({ ...editState, content: v })}
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
            placeholder={t('courses.captionPlaceholder')}
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
              <><Type className="h-3 w-3 inline mr-1" />{t('courses.blockTypeText')}</>
            ) : (
              <><ImageIcon className="h-3 w-3 inline mr-1" />{t('courses.blockTypeImage')}</>
            )}
          </span>
          <span className="text-xs text-primary-400">{t('courses.pendingBadge')}</span>
        </div>
        <button
          onClick={onDelete}
          className="p-1 text-dark-400 hover:text-red-400 transition-colors"
          title={t('courses.deleteBlock')}
        >
          <Trash2 className="h-4 w-4" />
        </button>
      </div>

      {block.type === 'TEXT' ? (
        <RichTextEditor
          value={block.content}
          onChange={(v) => onChange({ content: v })}
        />
      ) : (
        <div className="space-y-2">
          {(block.imageUrl ?? block.preview) && (
            <img
              src={block.imageUrl ?? block.preview}
              alt={block.caption}
              className="w-full max-h-48 object-cover rounded border border-dark-600"
            />
          )}
          <input
            type="text"
            value={block.caption}
            onChange={(e) => onChange({ caption: e.target.value })}
            placeholder={t('courses.captionPlaceholder')}
            className="w-full bg-dark-700 border border-dark-600 rounded px-3 py-2 text-dark-100 focus:outline-none focus:border-primary-500 text-sm"
          />
        </div>
      )}
    </div>
  )
}
