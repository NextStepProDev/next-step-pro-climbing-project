/**
 * Fold a string down to what a hurried admin actually types: lowercase, no accents.
 *
 * NFD decomposition handles ó/ą/ę/ś/ż/ź/ć/ń, but **not `ł`** — it is a distinct letter with no
 * combining form, so it survives the strip and "zgloszenia" would miss "zgłoszenia". It gets its
 * own replacement.
 */
export function foldForSearch(value: string): string {
  return value
    .toLowerCase()
    .replace(/ł/g, 'l')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
}

/** The fields a person search looks at; the optional ones are matched only when the row has them. */
export interface SearchablePerson {
  firstName: string
  lastName: string
  email?: string | null
  nickname?: string | null
}

/**
 * Does this person match what was typed? First name, last name, "first last", and — when the row
 * carries them — e-mail and nickname, all folded, so "lukasz" finds "Łukasz". An empty query matches
 * everyone.
 *
 * One function for every people search in the admin panel (users list, participant picker, invite
 * picker, athlete roster). The first three used to be hand-written copies, and none of them folded
 * Polish letters.
 */
export function matchesPersonQuery(person: SearchablePerson, query: string): boolean {
  const q = foldForSearch(query.trim())
  if (!q) return true
  const fields = [
    person.firstName,
    person.lastName,
    `${person.firstName} ${person.lastName}`,
    person.email ?? '',
    person.nickname ?? '',
  ]
  return fields.some((field) => foldForSearch(field).includes(q))
}
