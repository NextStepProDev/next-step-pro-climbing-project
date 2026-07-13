import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { siteSettingsApi } from "../api/client";

export interface ResolvedCalendarPromo {
  /** Whether the promo is active (template selected). */
  enabled: boolean;
  /** Badge — optional (empty = no badge). */
  badge: string;
  title: string;
  description: string;
  /** CTA button label — optional. */
  ctaLabel: string;
  /** CTA button link — optional. */
  ctaUrl: string;
}

/**
 * Calendar promo content for the current language.
 * Cross-language fallback (current → EN → PL → ES); a field empty in all
 * languages stays empty. Title and description are required (the admin cannot
 * save a template without them), so an active promo always has both.
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
