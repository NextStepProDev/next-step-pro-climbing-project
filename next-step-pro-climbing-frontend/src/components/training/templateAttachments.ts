import { decodeHtmlEntities } from '../../utils/htmlEntities'
import type { AttachmentInput, TrainingTemplate } from '../../types'

/**
 * A template's materials as editor input. The label is decoded because the backend escapes on
 * write and would escape again on the next save; a FILE keeps its stored filename, so the
 * training made from the template shares the file instead of copying it.
 *
 * Its own module, not an export of either form, because both forms are components and
 * react-refresh/only-export-components forbids a function next to one.
 */
export function templateToInputs(tpl: TrainingTemplate): AttachmentInput[] {
  return tpl.attachments.map((a): AttachmentInput => {
    const label = a.label ? decodeHtmlEntities(a.label) : ''
    return a.kind === 'FILE'
      ? {
          kind: 'FILE',
          filename: a.filename ?? undefined,
          originalName: a.fileName ?? undefined,
          mimeType: a.mimeType ?? undefined,
          sizeBytes: a.sizeBytes ?? undefined,
          label,
        }
      : { kind: 'LINK', url: a.url ?? '', label }
  })
}
