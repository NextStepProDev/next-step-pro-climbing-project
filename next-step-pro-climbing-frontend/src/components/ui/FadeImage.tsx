import { useRef, useState, useEffect, type ImgHTMLAttributes } from 'react'
import clsx from 'clsx'

export function FadeImage({ className, onLoad, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  const [loaded, setLoaded] = useState(false)
  const imgRef = useRef<HTMLImageElement>(null)

  useEffect(() => {
    if (imgRef.current?.complete) setLoaded(true)
  }, [])

  return (
    <img
      ref={imgRef}
      {...props}
      className={clsx(className, 'transition-opacity duration-300', loaded ? 'opacity-100' : 'opacity-0')}
      onLoad={(e) => {
        setLoaded(true)
        onLoad?.(e)
      }}
    />
  )
}
