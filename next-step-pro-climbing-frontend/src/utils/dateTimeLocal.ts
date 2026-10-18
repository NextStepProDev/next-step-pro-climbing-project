import { format } from 'date-fns'

/**
 * An instant as the value of an `<input type="datetime-local">`, in this device's zone.
 *
 * ⚠️ The first version shifted the instant by TODAY's offset (`getTime() - new Date().getTimezoneOffset()`),
 * not the offset that applied ON that date. Across a DST change the two differ by an hour, so an
 * article published in July showed an hour off in winter — and because the field saves on blur
 * when its value differs, merely clicking into it and out again moved the publication date.
 * `format` uses the date's own offset.
 */
export function toDateTimeLocalValue(iso: string): string {
  return format(new Date(iso), "yyyy-MM-dd'T'HH:mm")
}

/**
 * Whether the field's value says something other than the stored instant. Compared at the field's
 * own resolution (minutes): the stored value carries seconds the field cannot show, so a plain
 * string comparison treated every blur as an edit and quietly truncated them.
 */
export function dateTimeLocalChanged(value: string, storedIso: string): boolean {
  return value !== toDateTimeLocalValue(storedIso)
}
