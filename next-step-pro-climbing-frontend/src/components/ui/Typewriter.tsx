import { useEffect, useMemo, useState } from "react";

function prefersReducedMotion(): boolean {
  return (
    typeof window !== "undefined" &&
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

interface TypewriterProps {
  /** Pełny tekst do wystukania. Zmiana tekstu (np. język) restartuje efekt. */
  text: string;
  /** Bramka startu — typowanie rusza dopiero gdy `true` (chainowanie tytuł → akapit). */
  active?: boolean;
  /** ms na znak */
  speed?: number;
  /** opóźnienie przed pierwszym znakiem (ms) */
  startDelay?: number;
  /** czy zostawić migający kursor po zakończeniu (true dla ostatniego elementu) */
  keepCaretWhenDone?: boolean;
  /** wołane gdy cały tekst został wystukany */
  onDone?: () => void;
  /** fragment tekstu (np. słowo kluczowe) stylowany OD POCZĄTKU pisania, nie po zakończeniu */
  highlight?: string;
  /** klasa CSS nakładana na wyróżniony fragment */
  highlightClassName?: string;
}

/**
 * Efekt „maszyny do pisania": tekst pojawia się znak po znaku z migającym kursorem.
 * Pełny tekst jest renderowany niewidocznie pod spodem (grid 1/1), żeby zarezerwować
 * docelową wysokość — dzięki temu treść poniżej (przyciski) nie skacze podczas pisania.
 * Style (rozmiar, kolor) dziedziczy z rodzica (h1/p) — kursor używa `currentColor`.
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

  // renderuje pierwsze `upto` znaków, opakowując część należącą do `highlight` w span z klasą
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
