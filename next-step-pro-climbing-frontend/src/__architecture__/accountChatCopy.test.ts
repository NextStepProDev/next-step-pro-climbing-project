import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * An account holder's "Write to us" conversation is an in-app chat: no message is ever mailed in
 * either direction (decision 2026-11-24). The one mail is a content-free reminder of an answer
 * left unseen for 48 h (V110), and copy may name it. The copy must not promise the MESSAGE by mail:
 * a client told "we'll reply by e-mail" waits in their inbox for a message that never comes, and
 * a coach told "it also goes by e-mail" stops checking whether the client ever opened the app.
 *
 * Component tests mock `t()` to return keys, so nothing else reads these strings.
 */

const LOCALES_DIR = join(__dirname, '..', 'locales')
const LANGUAGES = ['pl', 'en', 'es'] as const

/** Strings shown ONLY for an account holder's conversation, as [file, key path]. */
const ACCOUNT_ONLY_COPY: Array<[string, string]> = [
  ['common.json', 'contactForm.sentInApp'],
  ['common.json', 'contactForm.signedInAs'],
  ['reservations.json', 'messages.hint'],
  ['admin.json', 'contactMessages.replyHintUser'],
  ['admin.json', 'contactMessages.sentInAppOnly'],
  ['admin.json', 'contactMessages.pickHint'],
  ['admin.json', 'users.detail.writeHint'],
]

/**
 * "mail" in any of the three languages, but not when the sentence says the opposite
 * ("bez maila", "no e-mail", "sin correo", "nie wysyłamy jej mailem", "don't send it by e-mail",
 * "no la enviamos por correo").
 */
const MAIL_WORD = /e-?mail|mailem|maila|correo/i
// The negation must be a whole word (`no` inside "now" or "nowa" is not one) and stand right
// before the mail word: a few short words in between, never a whole clause ("No spam — we reply
// by e-mail" promises mail).
const NEGATED = /(?<!\p{L})(bez|no|sin|nie wysyłamy|don't send|no la enviamos)(?!\p{L})[^.—]{0,12}(e-?mail|mailem|maila|correo)/iu

// The 48 h reminder (V110) is the one mail an account holder can get, and it carries no content -
// a clause that announces it is honest. It does not excuse any OTHER clause.
const REMINDER = /przypom\p{L}*|remind\p{L}*|recordatorio\p{L}*|recordar\p{L}*/iu

/**
 * Judged clause by clause: one denial ("we don't send it by e-mail") must not excuse a promise
 * elsewhere in the same text ("…; the reply arrives by e-mail").
 */
function promisesMail(text: string): boolean {
  // `{{email}}` is the writer's address shown back to them, not a promise of mail.
  const prose = text.replace(/\{\{[^}]+\}\}/g, '')
  return prose
    .split(/[.;—]|\s-\s/)
    .some((clause) => MAIL_WORD.test(clause) && !NEGATED.test(clause) && !REMINDER.test(clause))
}

function lookup(file: string, lang: string, path: string): unknown {
  const json = JSON.parse(readFileSync(join(LOCALES_DIR, lang, file), 'utf8'))
  return path.split('.').reduce<unknown>((node, key) => (node as Record<string, unknown> | undefined)?.[key], json)
}

describe('account chat copy', () => {
  for (const lang of LANGUAGES) {
    for (const [file, path] of ACCOUNT_ONLY_COPY) {
      it(`${lang}/${file} ${path} does not promise an e-mail`, () => {
        const text = lookup(file, lang, path)
        expect(typeof text, `missing ${lang}/${file} ${path}`).toBe('string')
        expect(promisesMail(text as string), `"${text}"`).toBe(false)
      })
    }
  }
})

// The gate is only as good as its matcher: a negation that matched too eagerly would wave every
// promise through and the gate would stay green while checking nothing.
describe('account chat copy matcher', () => {
  it.each([
    'We will now e-mail you the answer.',
    'You will know by e-mail.',
    'Odpowiedź przyjdzie mailem.',
    'Odpowiedź nowa przyjdzie mailem.',
    'No spam — we reply by e-mail.',
    'Te responderemos por correo.',
    // A reminder elsewhere in the copy must not excuse a promise of the message itself
    'Odpowiedź przyjdzie mailem — przypomnimy też po 2 dniach.',
    // One denial must not excuse a promise in another clause of the same text
    'Nie wysyłamy jej mailem — ale odpowiedź przyjdzie mailem.',
    "We don't send it by e-mail; the reply arrives by e-mail.",
  ])('should catch a promise of mail: %s', (text) => {
    expect(promisesMail(text)).toBe(true)
  })

  it.each([
    'Saved in the chat (no e-mail).',
    'Zapisano w czacie (bez maila).',
    'Nie wysyłamy jej mailem.',
    "We don't send it by e-mail.",
    'No la enviamos por correo.',
    'Writing as {{name}} ({{email}}).',
    // The 48 h reminder (V110) carries no content - naming it is honest, not a promise of the message
    'Nie wysyłamy jej mailem — po 2 dniach bez odczytu przypomnimy mailem.',
    "We don't send it by e-mail — if it stays unread for 2 days, we send a reminder by e-mail.",
    'No la enviamos por correo — si sigue sin leer 2 días, te enviamos un recordatorio por correo.',
  ])('should let a denial of mail through: %s', (text) => {
    expect(promisesMail(text)).toBe(false)
  })
})
