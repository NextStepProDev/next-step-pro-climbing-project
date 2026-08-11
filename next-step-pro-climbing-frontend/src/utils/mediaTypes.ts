/** MIME types the UI renders inline; everything else is offered as a download. */
export const isImageType = (mimeType: string | null | undefined) => !!mimeType?.startsWith('image/')
