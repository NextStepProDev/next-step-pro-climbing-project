import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { siteSettingsApi } from "../api/client";

export interface ResolvedLocationContent {
  /** Whether the section is live on the page (template selected). */
  enabled: boolean;
  /** Badge — optional (empty = no badge). */
  badge: string;
  title: string;
  /** Subtitle — optional (empty = no line). */
  subtitle: string;
  /** Place list — optional (empty = no list). */
  places: string[];
}

/**
 * Location section content for the current language.
 * The title is fixed (i18n). Badge, subtitle and place list are optional:
 * cross-language fallback (current → EN → PL → ES); when empty in all
 * languages — the field stays empty (we do NOT show a hardcoded default text).
 * Shared by the hero badge (HomePage) and the CurrentLocationSection block.
 */
export function useLocationContent(): ResolvedLocationContent {
  const { t, i18n } = useTranslation("home");

  const { data: homeSettings } = useQuery({
    queryKey: ["homeSettings"],
    queryFn: siteSettingsApi.getHome,
    staleTime: 30 * 60 * 1000,
  });

  const location = homeSettings?.location;
  const lang = i18n.language?.slice(0, 2) ?? "pl";
  const tr = location?.translations ?? {};
  const order = [lang, "en", "pl", "es"];

  const pick = (field: "badge" | "subtitle") => {
    for (const l of order) {
      const v = tr[l]?.[field]?.trim();
      if (v) return v;
    }
    return "";
  };

  const pickPlaces = () => {
    for (const l of order) {
      const list = tr[l]?.locations?.filter((x) => x.trim() !== "");
      if (list && list.length > 0) return list;
    }
    return [];
  };

  return {
    enabled: location?.enabled === true,
    badge: pick("badge"),
    // The title is FIXED — always from i18n, the admin does not change it.
    title: t("location.title"),
    subtitle: pick("subtitle"),
    places: pickPlaces(),
  };
}
