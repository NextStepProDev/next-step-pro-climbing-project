import { useEffect, useMemo, useState } from "react";

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

interface TypewriterProps {
  /** Full text to type out. Changing the text (e.g. language) restarts the effect. */
  text: string;
  /** Start gate — typing begins only when `true` (chaining title → paragraph). */
  active?: boolean;
  /** ms per character */
  speed?: number;
  /** delay before the first character (ms) */
  startDelay?: number;
  /** whether to keep the blinking cursor after finishing (true for the last element) */
  keepCaretWhenDone?: boolean;
  /** called once the whole text has been typed out */
  onDone?: () => void;
  /** text fragment (e.g. a keyword) styled FROM THE START of typing, not after finishing */
  highlight?: string;
  /** CSS class applied to the highlighted fragment */
  highlightClassName?: string;
}

/**
 * "Typewriter" effect: text appears character by character with a blinking cursor.
 * The full text is rendered invisibly underneath (grid 1/1) to reserve the target
 * height — so the content below (buttons) does not jump while typing.
 * Styles (size, color) are inherited from the parent (h1/p) — the cursor uses `currentColor`.
 */
export function Typewriter({
  text,
  active = true,
  speed = 30,
  startDelay = 0,
  keepCaretWhenDone = false,
  onDone,
  highlight,
  highlightClassName,
}: TypewriterProps) {
  const reduced = useMemo(prefersReducedMotion, []);
  const [count, setCount] = useState(0);

  useEffect(() => {
    setCount(0);
    if (!active) return;
    if (reduced) {
      setCount(text.length);
      return;
    }
    let i = 0;
    let timer: ReturnType<typeof setTimeout>;
    const start = setTimeout(function tick() {
      i += 1;
      setCount(i);
      if (i < text.length) timer = setTimeout(tick, speed);
    }, startDelay);
    return () => {
      clearTimeout(start);
      clearTimeout(timer);
    };
  }, [text, active, reduced, speed, startDelay]);

  const done = count >= text.length;

  useEffect(() => {
    if (active && done) onDone?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, done]);

  const showCaret = active && !reduced && (!done || keepCaretWhenDone);

  // renders the first `upto` characters, wrapping the part belonging to `highlight` in a classed span
  const renderPortion = (upto: number) => {
    const shown = text.slice(0, upto);
    if (!highlight) return shown;
    const hi = text.indexOf(highlight);
    if (hi < 0) return shown;
    const hiEnd = hi + highlight.length;
    const before = shown.slice(0, Math.min(upto, hi));
    const mid = upto > hi ? shown.slice(hi, Math.min(upto, hiEnd)) : "";
    const after = upto > hiEnd ? shown.slice(hiEnd) : "";
    return (
      <>
        {before}
        {mid && <span className={highlightClassName}>{mid}</span>}
        {after}
      </>
    );
  };

  return (
    <span className="grid">
      <span aria-hidden className="invisible [grid-area:1/1]">
        {renderPortion(text.length)}
      </span>
      <span className="[grid-area:1/1]">
        {renderPortion(count)}
        {showCaret && <span className="tw-caret">|</span>}
      </span>
    </span>
  );
}
