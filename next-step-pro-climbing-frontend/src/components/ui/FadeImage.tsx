import { useCallback, useEffect, useRef, useState, type ImgHTMLAttributes } from 'react'
import clsx from 'clsx'

export function FadeImage({ className, onLoad, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  const [loaded, setLoaded] = useState(false)
  const timerRef = useRef<ReturnType<typeof setTimeout>>(null)

  const refCallback = useCallback((img: HTMLImageElement | null) => {
    if (img?.complete && img.naturalWidth > 0) setLoaded(true)
  }, [])

  useEffect(() => {
    if (!loaded) {
      timerRef.current = setTimeout(() => setLoaded(true), 1000)
    }
    return () => { if (timerRef.current) clearTimeout(timerRef.current) }
  }, [loaded])

  return (
    <img
      ref={refCallback}
      {...props}
      className={clsx(className, 'transition-opacity duration-300', loaded ? 'opacity-100' : 'opacity-0')}
      onLoad={(e) => {
        setLoaded(true)
        onLoad?.(e)
      }}
    />
  )
}
