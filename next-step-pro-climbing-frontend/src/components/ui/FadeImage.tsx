import { useState, type ImgHTMLAttributes } from 'react'
import clsx from 'clsx'

export function FadeImage({ className, onLoad, ...props }: ImgHTMLAttributes<HTMLImageElement>) {
  const [loaded, setLoaded] = useState(false)

  return (
    <img
      {...props}
      className={clsx(className, 'transition-opacity duration-300', loaded ? 'opacity-100' : 'opacity-0')}
      onLoad={(e) => {
        setLoaded(true)
        onLoad?.(e)
      }}
    />
  )
}
