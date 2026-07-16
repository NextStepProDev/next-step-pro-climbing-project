// The backend HTML-escapes user text on write (XSS defense in depth). React escapes on
// render anyway, so decode for display/edit to avoid showing &amp; / &quot; literals.
export function decodeHtmlEntities(text: string): string {
  return text
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&#39;', "'")
    .replaceAll('&amp;', '&')
}
