/**
 * ASCII slug for file names — diacritics in a filename are a needless risk on a FAT-formatted stick.
 *
 * Its own module rather than living in `ascentExport.ts`: that file embeds a font and is loaded
 * dynamically on purpose, so a static import of this helper would drag the font into every chunk
 * that names a file.
 */
export function slugify(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/ł/g, 'l')
    .replace(/Ł/g, 'L')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}
