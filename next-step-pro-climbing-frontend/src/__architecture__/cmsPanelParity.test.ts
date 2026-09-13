import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * The two CMS panels drift, and this is what notices.
 *
 * `AdminNewsPanel` and `AdminCoursesPanel` share 995 lines — measured, and up from 975 in August.
 * `docs/DUPLICATION.md` keeps them apart deliberately and names the price: a fix lands in one copy
 * and not the other. That has already happened once (the "Draft" badge, 2026-08-29, corrected by
 * hand in both).
 *
 * Parameterising them the way `AdminInstructorsPanel` delegates to `AdminTeamMemberPanel` is the
 * eventual answer, and it is NOT what this file does — 3021 lines of block dragging, image
 * cropping, focal points and multilingual duplication currently have **no tests at all**, and
 * rewriting untested interactive code is how a feature disappears quietly. Characterisation tests
 * come first; the refactor after them.
 *
 * Until then, this converts "remember to touch both copies" from vigilance into a gate. Not by
 * comparing the components — they legitimately differ — but by comparing what they OFFER: a
 * capability added to one module and forgotten in the other shows up as a translation key that
 * exists on one side only.
 *
 * It cannot see a fix that changes behaviour without adding a key. It catches the common case,
 * which is a new control with a new label, and that is worth more than nothing.
 */
const ADMIN_PL = join(__dirname, '..', 'locales', 'pl', 'admin.json')

/**
 * Asymmetries that are real features of one module, not forgotten work.
 *
 * ⚠️ Adding a name here is a decision that the other module is not getting this. Video embeds are
 * the clearest case: `course_content_blocks.block_type` is VARCHAR(10) and VIDEO_EMBED does not
 * fit, so courses cannot carry one without a migration.
 */
const ONLY_IN_NEWS = [
  // Video blocks: news only, and the database enforces it (see the VARCHAR(10) note above).
  'addVideo', 'blockTypeVideo', 'videoUrlHint', 'videoUrlPlaceholder',
  // Sending an article to the newsletter — courses are not mailed out.
  'newsletterSend', 'newsletterConfirmTitle', 'newsletterConfirmMessage', 'newsletterSentSuccess',
  // Thumbnail cropping, offered for articles only.
  'cropEnable', 'cropDisable',
  // An article has a lead paragraph and a publication date; a course has neither.
  'excerptLabel', 'publishedAtLabel', 'publishedAtNow',
  // The noun differs, so these three can never match by name.
  'addArticle', 'newArticleDefaultTitle', 'noArticles',
]

const ONLY_IN_COURSES = [
  // Free text on the course card. Nothing to do with the settlements feature — see docs/MONEY.md.
  'priceLabel', 'pricePlaceholder',
  // The noun again.
  'addCourse', 'newCourseDefaultTitle', 'noCourses',
]

function keysOf(module: 'news' | 'courses'): Set<string> {
  const admin = JSON.parse(readFileSync(ADMIN_PL, 'utf8')) as Record<string, Record<string, string>>
  return new Set(Object.keys(admin[module] ?? {}))
}

describe('CMS panels — news against courses', () => {
  it('offers the same things on both sides, or says why not', () => {
    const news = keysOf('news')
    const courses = keysOf('courses')

    const missingFromCourses = [...news].filter((k) => !courses.has(k) && !ONLY_IN_NEWS.includes(k))
    const missingFromNews = [...courses].filter((k) => !news.has(k) && !ONLY_IN_COURSES.includes(k))

    expect({ missingFromCourses, missingFromNews }).toEqual({
      missingFromCourses: [],
      missingFromNews: [],
    })
  })

  it('keeps the allowlist honest — an entry that no longer exists hides a real drift', () => {
    // A stale name here is worse than a missing one: it silently excuses whatever takes its place.
    const news = keysOf('news')
    const courses = keysOf('courses')

    expect(ONLY_IN_NEWS.filter((k) => !news.has(k))).toEqual([])
    expect(ONLY_IN_COURSES.filter((k) => !courses.has(k))).toEqual([])
  })
})
