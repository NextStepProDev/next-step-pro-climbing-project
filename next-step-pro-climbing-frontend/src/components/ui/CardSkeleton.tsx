import clsx from 'clsx'

function Shimmer({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded bg-surface-700', className)} />
}

export function CardSkeleton({ count = 6, columns = 3 }: { count?: number; columns?: 2 | 3 | 4 }) {
  const gridCols = {
    2: 'grid-cols-1 sm:grid-cols-2',
    3: 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3',
    4: 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4',
  }

  return (
    <div className={clsx('grid gap-6', gridCols[columns])}>
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-lg overflow-hidden border border-surface-700/50 bg-surface-800/50">
          <Shimmer className="aspect-video w-full !rounded-none" />
          <div className="p-4 space-y-3">
            <Shimmer className="h-5 w-3/4" />
            <Shimmer className="h-4 w-full" />
            <Shimmer className="h-4 w-1/2" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function AccordionSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="space-y-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-lg border border-surface-700/50 bg-surface-800/50 p-4 flex items-center gap-4">
          <Shimmer className="w-20 h-20 !rounded-lg shrink-0" />
          <div className="flex-1 space-y-2">
            <Shimmer className="h-5 w-2/3" />
            <Shimmer className="h-4 w-1/3" />
          </div>
        </div>
      ))}
    </div>
  )
}

export function TileSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
      {Array.from({ length: count }).map((_, i) => (
        <Shimmer key={i} className="aspect-[3/4] w-full !rounded-xl" />
      ))}
    </div>
  )
}
