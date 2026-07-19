import { useTranslation } from 'react-i18next'
import { Link2, Plus, X } from 'lucide-react'
import type { AttachmentInput } from '../../types'

const MAX_ATTACHMENTS = 3

interface AttachmentEditorProps {
  value: AttachmentInput[]
  onChange: (next: AttachmentInput[]) => void
}

/**
 * Up to 3 material links per training (label optional + URL). Shared by the training form
 * and (later) the template form. Validation of the URLs happens on submit in the parent.
 */
export function AttachmentEditor({ value, onChange }: AttachmentEditorProps) {
  const { t } = useTranslation('training')

  const update = (index: number, patch: Partial<AttachmentInput>) => {
    onChange(value.map((a, i) => (i === index ? { ...a, ...patch } : a)))
  }
  const remove = (index: number) => onChange(value.filter((_, i) => i !== index))
  const add = () => onChange([...value, { url: '', label: '' }])

  return (
    <div>
      <label className="block text-sm text-surface-400 mb-1">{t('form.attachments')}</label>
      <p className="text-xs text-surface-500 mb-2">{t('form.attachmentsHint')}</p>

      <div className="space-y-2">
        {value.map((att, i) => (
          <div key={i} className="flex gap-2 items-start">
            <div className="flex-1 space-y-1.5">
              <input
                type="text"
                value={att.label ?? ''}
                onChange={(e) => update(i, { label: e.target.value })}
                maxLength={120}
                placeholder={t('form.attachmentLabelPlaceholder')}
                className="w-full bg-surface-800 border border-surface-700 rounded-lg px-3 py-1.5 text-sm text-surface-100"
              />
              <div className="relative">
                <Link2 className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-surface-500" />
                <input
                  type="url"
                  inputMode="url"
                  value={att.url}
                  onChange={(e) => update(i, { url: e.target.value })}
                  maxLength={2048}
                  placeholder={t('form.attachmentUrlPlaceholder')}
                  className="w-full bg-surface-800 border border-surface-700 rounded-lg pl-8 pr-3 py-1.5 text-sm text-surface-100"
                />
              </div>
            </div>
            <button
              type="button"
              onClick={() => remove(i)}
              className="p-1.5 mt-0.5 rounded-lg text-surface-400 hover:text-rose-300 hover:bg-surface-800 transition-colors"
              title={t('form.attachmentRemove')}
              aria-label={t('form.attachmentRemove')}
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>

      {value.length < MAX_ATTACHMENTS && (
        <button
          type="button"
          onClick={add}
          className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-primary-400 hover:text-primary-300 transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('form.attachmentAdd')}
        </button>
      )}
    </div>
  )
}
