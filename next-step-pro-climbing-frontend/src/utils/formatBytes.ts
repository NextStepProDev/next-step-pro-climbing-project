const UNITS = ['B', 'KB', 'MB', 'GB', 'TB']

/** A file size for people: one decimal at most, none when it would be ".0" ("2 KB", "1.5 MB"). */
export function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), UNITS.length - 1)
  return `${parseFloat((bytes / 1024 ** i).toFixed(1))} ${UNITS[i]}`
}
