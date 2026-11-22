/**
 * How many thumbnails a gallery block in an article shows. Whole rows only: 5–7 photos in four
 * columns left a ragged second row, so those albums show one row and the "+N" tile says the rest
 * exists.
 */
export function visibleThumbnailCount(total: number): number {
  if (total >= 8) return 8
  if (total >= 4) return 4
  return total
}
