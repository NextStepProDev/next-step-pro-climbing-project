import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { siteSettingsApi } from "../api/client";

// Domyślna lista miejsc (fallback gdy admin nic nie zapisał)
const DEFAULT_LOCATIONS = ["El Chorro", "Granada", "Motril", "Los Cahorros", "Órgiva"];

export interface ResolvedLocationContent {
  /** Czy cała sekcja (plakietka u góry + blok "Gdzie teraz szkolę") jest widoczna. */
  enabled: boolean;
  badge: string;
  title: string;
  subtitle: string;
  places: string[];
}

/**
 * Treść sekcji lokalizacji dla aktualnego języka, z fallbackiem do tłumaczeń i18n.
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
  const content = location?.translations?.[lang];

  return {
    enabled: location?.enabled !== false,
    badge: content?.badge?.trim() || t("location.badge"),
    title: content?.title?.trim() || t("location.title"),
    subtitle: content?.subtitle?.trim() || t("location.subtitle"),
    places:
      content?.locations && content.locations.length > 0
        ? content.locations
        : DEFAULT_LOCATIONS,
  };
}
