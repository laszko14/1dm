# 1DM+ — grupy kart (etap 2: adapter i natywny interfejs)

Repozytorium z **własnym kodem** do zarządzania grupami otwartych kart oraz z narzędziem do analizy nazw metod w pliku DEX. To nie jest źródłowy kod zamkniętej aplikacji 1DM+.

## Status (etap 1)

- [x] Zweryfikowano lokalnie APK użytkownika 1DM+ 18.2: istnieją klasy `acr.browser.lightning.activity.TabsManager` i `acr.browser.lightning.fragment.TabsFragment`.
- [x] Zaimplementowano niezależny model grup i przypisywania identyfikatorów kart (źródła Java).
- [x] Utworzono osobny format przechowywania grup, bez zmiany zapisanych kart przeglądarki.
- [x] Dodano testy tworzenia/zmiany nazwy/usuwania/przenoszenia/zapisywania/odtwarzania/uzgadniania z sesją.
- [x] Dodano koordynator sesji `TabGroupCoordinator` i filtr listy otwartych kart.
- [x] Dodano niezależną sesję incognito bez zapisu jej grup na dysk.
- [x] Dodano `ReflectiveTabProvider`, korzystający z potwierdzonych sygnatur API 1DM+.
- [x] Dodano polski panel Android `TabGroupsDialog` do obsługi grup.
- [x] Rozszerzono zestaw testów Java i CI Android SDK.
- [ ] Zweryfikować trwałość natywnych identyfikatorów kart po restarcie.
- [ ] Sprawdzić czy i jak możliwa jest zgodna z licencją integracja z natywnym panelem kart 1DM+ (`TabsFragment`).
- [ ] Zaimplementować widok grup i wywołania `TabsManager` w rzeczywistym APK.
- [ ] Testy na urządzeniu, zgodność z pobieraniem, przekierowaniami i przywracaniem sesji.
- [ ] Zbudowanie kompletnej, podpisanej aplikacji (nie jest możliwe tylko z tym repozytorium).

**Ważne:** Kod z tego repo **nie zmienia jeszcze wyglądu ani działania 1DM+**. Kompilowane przez CI biblioteki `.jar` to własna logika i osobne klasy Android. Żadna z nich nie jest zmodyfikowanym instalatorem APK.

## Jak mają działać grupy

- Początkowa grupa `Pozostałe`; własne `Filmy`, `Seriale` itd.
- Każda otwarta karta należy do dokładnie jednej grupy.
- Nowe karty przypisywane do aktywnej grupy.
- Przenoszenie bez przeładowania WebView.
- Usunięcie grupy przenosi jej karty do `Pozostałe` zamiast je zamykać.
- Na starcie aplikacja synchronizuje listę aktywnych kart z przypisaniami.
- Zapisywane są wyłącznie nazwy, identyfikatory grup i identyfikatory kart — nie adresy stron, treść kart ani pliki pobrane.

## Testy

```bash
bash scripts/test.sh
```

Wymaga JDK 17. GitHub Actions uruchamia testy na push.

## Lokalna diagnostyka APK (bez jego publikowania)

```bash
python3 scripts/dex_index.py /sciezka/do/wlasnego.apk --filter TabsManager
python3 scripts/dex_index.py /sciezka/do/wlasnego.apk --filter TabsFragment
```

Skrypt czyta lokalny DEX i pokazuje **nazwy** znalezionych klas/metod; nie publikuje bajtkodu.

## Integracja z aplikacją — następny etap

1. Ustalić trwały identyfikator każdej karty w `TabsManager.getTabById` / `getTabIndexById`.
2. Podczas przywracania sesji (`restoreState`, `initializeTabs`) uruchomić `reconcileOpenTabs`, a przy otwarciu/zamknięciu karty wywołać odpowiednio `addNewTab`/`closeTab`.
3. W `TabsFragment` wyświetlić listę grup i przełączać filtr jego adaptera; potrzebny będzie odrębny interfejs do tworzenia i przenoszenia kart.
4. Zapis grup wykonywać w prywatnym katalogu aplikacji, **nie** zastępować istniejącego formatu zapisu sesji 1DM+.
5. Uruchomić testy kompatybilności z aktualną wersją 18.2 oraz testy regresji pobierania.

## Ochrona danych i licencji

**Repo jest publiczne. Nie commituj APK 1DM+, zdekodowanych zasobów, bibliotek ani kluczy.** Płatny plik aplikacji użytkownika pozostaje poza GitHubem. Projekt nie zawiera obchodzenia weryfikacji licencji ani podpisu aplikacji. Podpis zmodyfikowanego APK byłby inny niż podpis producenta — nie można go bezpiecznie nałożyć jako aktualizacji.

## Etap 2: gotowe elementy

- `TabGroupCoordinator` — oddziela prywatne i zwykłe grupy, filtruje natywne ID kart, zachowuje ich kolejność.
- `ReflectiveTabProvider` — adapter do `TabsManager.size()`, `getTabAtPosition(int)` i `LightningView.getId()`.
- `TabGroupsDialog` — natywny polski interfejs do grup, możliwy do wywołania po integracji z aplikacją.
- [Dokumentacja integracji](docs/1dm-18.2-contract.md) — potwierdzone API DEX, zagrożenia i dalsze prace.

**Status:** etapy 1 i 2 to działające komponenty, ale nadal nie ma gotowego APK 1DM+ ani instalowalnej modyfikacji.
