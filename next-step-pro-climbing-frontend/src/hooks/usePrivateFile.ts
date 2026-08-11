import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchPrivateFile } from '../api/client'

/**
 * Loads a file that needs the session — training materials and the attachments people send in a
 * training thread. These are not reachable from `/api/files`, so a plain `<img src>` cannot show
 * them: it sends no Authorization header. The bytes are fetched instead, and a local `blob:` URL
 * is made from them.
 *
 * <p>The point of that is not ceremony. A link to a public file works for anyone who ever sees it,
 * forever — out of browser history, out of a pasted message, out of a bookmark. A `blob:` URL is
 * meaningless anywhere except the tab that made it.
 */
export function usePrivateFile(url: string | null, enabled = true) {
  const query = useQuery({
    // The BLOB is what is cached, never the object URL — see useObjectUrl below.
    queryKey: ['privateFile', url],
    queryFn: () => fetchPrivateFile(url!),
    enabled: enabled && !!url,
    staleTime: Infinity,
    // A missing file is missing (deleted, or swept by retention); retrying cannot help and would
    // multiply requests across a thread full of images.
    retry: false,
  })

  const objectUrl = useObjectUrl(query.data)

  return { objectUrl, isLoading: query.isLoading, isError: query.isError }
}

/**
 * Own object URL per component, revoked on unmount.
 *
 * Caching the object URL instead of the blob would have two bubbles rendering the same file share
 * one URL — and the first of them to unmount would revoke it out from under the other, leaving a
 * broken image with no error to explain it.
 */
function useObjectUrl(blob: Blob | undefined): string | null {
  // Render-phase adjustment when the input changes — the supported pattern, and the one Modal.tsx
  // already uses here. Creating the URL in an effect instead would render one frame with a null
  // src, which for an image is a visible flash of the empty placeholder after the bytes arrived.
  const [current, setCurrent] = useState<{ blob: Blob | undefined; url: string | null }>({
    blob: undefined,
    url: null,
  })

  if (current.blob !== blob) {
    if (current.url) URL.revokeObjectURL(current.url)
    setCurrent({ blob, url: blob ? URL.createObjectURL(blob) : null })
  }

  // Mirrored in a ref so unmount cleanup sees the latest URL without re-running on every change.
  const latest = useRef<string | null>(null)
  useEffect(() => {
    latest.current = current.url
  }, [current.url])
  useEffect(() => () => {
    if (latest.current) URL.revokeObjectURL(latest.current)
  }, [])

  return current.url
}
