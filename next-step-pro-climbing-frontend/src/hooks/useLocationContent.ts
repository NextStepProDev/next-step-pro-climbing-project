import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { siteSettingsApi } from "../api/client";

export interface ResolvedLocationContent {
  /** Czy sekcja jest na stronie (wybrany szablon). */
  enabled: boolean;
  /** Plakietka — opcjonalna (puste = brak plakietki). */
  badge: string;
  title: string;
  /** Podtytuł — opcjonalny (puste = brak linijki). */
  subtitle: string;
  /** Lista miejsc — opcjonalna (puste = brak listy). */
  places: string[];
}

/**
 * Treść sekcji lokalizacji dla aktualnego języka.
 * Tytuł jest stały (i18n). Badge, podtytuł i lista miejsc są opcjonalne:
 * fallback krzyżowy między językami (bieżący → EN → PL → ES), a gdy puste
 * we wszystkich — pole zostaje puste (NIE pokazujemy zakodowanego domyślnego tekstu).
 * Współdzielona przez plakietkę hero (HomePage) i blok CurrentLocationSection.
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
    // Tytuł jest STAŁY — zawsze z i18n, admin go nie zmienia.
    title: t("location.title"),
    subtitle: pick("subtitle"),
    places: pickPlaces(),
  };
}
