import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { siteSettingsApi } from "../api/client";

export interface ResolvedCalendarPromo {
  /** Czy promocja jest aktywna (wybrany szablon). */
  enabled: boolean;
  /** Plakietka — opcjonalna (puste = brak plakietki). */
  badge: string;
  title: string;
  description: string;
  /** Etykieta przycisku CTA — opcjonalna. */
  ctaLabel: string;
  /** Link przycisku CTA — opcjonalny. */
  ctaUrl: string;
}

/**
 * Treść promocji nad kalendarzem dla aktualnego języka.
 * Fallback krzyżowy między językami (bieżący → EN → PL → ES); gdy pole puste
 * we wszystkich językach — zostaje puste. Tytuł i opis są obowiązkowe (admin
 * nie zapisze szablonu bez nich), więc dla aktywnej promocji zawsze są obecne.
 */
export function useCalendarPromo(): ResolvedCalendarPromo {
  const { i18n } = useTranslation("calendar");

  const { data: promo } = useQuery({
    queryKey: ["calendarPromo"],
    queryFn: siteSettingsApi.getCalendarPromo,
    staleTime: 30 * 60 * 1000,
  });

  const lang = i18n.language?.slice(0, 2) ?? "pl";
  const tr = promo?.translations ?? {};
  const order = [lang, "en", "pl", "es"];

  const pick = (field: "badge" | "title" | "description" | "ctaLabel" | "ctaUrl") => {
    for (const l of order) {
      const v = tr[l]?.[field]?.trim();
      if (v) return v;
    }
    return "";
  };

  return {
    enabled: promo?.enabled === true,
    badge: pick("badge"),
    title: pick("title"),
    description: pick("description"),
    ctaLabel: pick("ctaLabel"),
    ctaUrl: pick("ctaUrl"),
  };
}
