/// <reference types="vite/client" />

declare const __APP_VERSION__: string

/**
 * `?inline` on a binary asset yields a data URI instead of a URL. vite/client only declares it
 * for stylesheets, so the font the PDF export embeds needs its own declaration.
 */
declare module '*.ttf?inline' {
  const dataUri: string
  export default dataUri
}
