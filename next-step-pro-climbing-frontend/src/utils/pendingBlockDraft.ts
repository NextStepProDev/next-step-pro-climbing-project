interface PendingBlockLike {
  type: string
  source?: string
  file?: File
  preview?: string
}

/**
 * The part of a CMS editor's queue of new blocks a draft can keep. An image picked from the
 * device is a File, which storage cannot hold, and its preview is a blob: URL that dies with the
 * page — so it is dropped and counted, for the banner to say how many to add again. Images from
 * the library or gallery are only a URL and survive whole.
 */
export function keepablePendingBlocks<B extends PendingBlockLike>(blocks: B[]): { kept: B[]; dropped: number } {
  const kept: B[] = []
  let dropped = 0
  for (const block of blocks) {
    if (block.type === 'IMAGE' && block.source === 'file') {
      dropped++
      continue
    }
    const copy = { ...block }
    delete copy.file
    delete copy.preview
    kept.push(copy)
  }
  return { kept, dropped }
}
