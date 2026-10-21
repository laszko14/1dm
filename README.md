# 1DM+ — grupy kart (etap 2: logika, adapter i natywny dialog)

Repozytorium z **własnym kodem** do zarządzania grupami otwartych kart oraz z narzędziem do analizy nazw metod w pliku DEX. To nie jest źródłowy kod zamkniętej aplikacji 1DM+.

## Status (etap 1)

- [x] Zweryfikowano lokalnie APK użytkownika 1DM+ 18.2: istnieją klasy `acr.browser.lightning.activity.TabsManager` i `acr.browser.lightning.fragment.TabsFragment`.
- [x] Zaimplementowano niezależny model grup i przypisywania identyfikatorów kart (źródła Java).
- [x] Utworzono osobny format przechowywania grup, bez zmiany zapisanych kart przeglądarki.
- [x] Dodano testy tworzenia/zmiany nazwy/usuwania/przenoszenia/zapisywania/odtwarzania/uzgadniania z sesją.
- [x] Przygotowano `TabGroupCoordinator`: aktywna grupa, filtrowanie rzeczywistych kart, porządek kart 1DM+, odrębna sesja prywatna bez zapisu jej grup na dysk.
- [x] Przygotowano `ReflectiveTabProvider`: odczyt `TabsManager.size()/getTabAtPosition()/LightningView.getId()` przez adapter runtime.
- [x] Przygotowano polski natywny `TabGroupsDialog` (tworzenie, przełączanie, zmiana nazwy, usuwanie, przenoszenie karty). UI należy dopiero podłączyć do prawdziwego panelu aplikacji.
- [x] Zweryfikowano 48 kontroli testowych logiki, adaptera i obsługi sesji (17+28+3).
- [ ] Potwierdzić, że ID karty jest trwałe po restarcie przeglądarki (same sygnatury nie wystarczają).
- [ ] Sprawdzić czy i jak możliwa jest zgodna z licencją integracja z natywnym panelem kart 1DM+ (`TabsFragment`).
- [ ] Zaimplementować widok grup i wywołania `TabsManager` w rzeczywistym APK.
- [ ] Testy na urządzeniu, zgodność z pobieraniem, przekierowaniami i przywracaniem sesji.
- [ ] Zbudowanie kompletnej, podpisanej aplikacji (nie jest możliwe tylko z tym repozytorium).

**Ważne:** Kod z tego repo **nie zmienia jeszcze wyglądu ani działania zainstalowanego 1DM+**. Kompilowane `.jar` obejmują logikę grup i osobno klasy dialogu Android. To nie jest zmodyfikowany instalator APK.

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

Wymaga JDK 17. GitHub Actions uruchamia testy na push oraz kompiluje natywny panel z Android SDK. Same testy nie dowodzą poprawnej integracji z zamkniętym 1DM+.

## Lokalna diagnostyka APK (bez jego publikowania)

```bash
python3 scripts/dex_index.py /sciezka/do/wlasnego.apk --filter TabsManager
python3 scripts/dex_index.py /sciezka/do/wlasnego.apk --filter TabsFragment
python3 scripts/dex_contract.py /sciezka/do/wlasnego.apk --filter TabsManager --name getTab
```

Skrypt czyta lokalny DEX i pokazuje **nazwy** znalezionych klas/metod; nie publikuje bajtkodu.

## Integracja z aplikacją — następny etap

1. Ustalić trwały identyfikator każdej karty w `TabsManager.getTabById` / `getTabIndexById`.
2. Zintegrować `TabGroupCoordinator.onSessionReady()` **wyłącznie po zakończeniu** przywracania sesji (`restoreState`, `initializeTabs`), a przy otwarciu/zamknięciu karty wywołać `onTabOpened` / `onTabClosed`. Nie używać `onSessionReady` podczas częściowego przywracania.
3. Podłączyć gotowy `TabGroupsDialog` do `TabsFragment`, a następnie zastosować filtr `visibleTabIds()` w adapterze, zachowując oryginalną kolejność kart.
4. Zapis grup wykonywać w prywatnym katalogu aplikacji, **nie** zastępować istniejącego formatu zapisu sesji 1DM+.
5. Uruchomić testy kompatybilności z aktualną wersją 18.2 oraz testy regresji pobierania.

## Ochrona danych i licencji

**Repo jest publiczne. Nie commituj APK 1DM+, zdekodowanych zasobów, bibliotek ani kluczy.** Płatny plik aplikacji użytkownika pozostaje poza GitHubem. Projekt nie zawiera obchodzenia weryfikacji licencji ani podpisu aplikacji. Podpis zmodyfikowanego APK byłby inny niż podpis producenta — nie można go bezpiecznie nałożyć jako aktualizacji.

## Etap 2 — pliki

- [`docs/1dm-18.2-contract.md`](docs/1dm-18.2-contract.md) — potwierdzone API DEX i niewiadome integracji.
- `src/main/java/org/local/tabgroups/TabGroupCoordinator.java` — most do prawdziwej sesji i prywatność.
- `src/main/java/org/local/tabgroups/ReflectiveTabProvider.java` — własny adapter pod sygnatury 1DM+ 18.2.
- `src/android/java/org/local/tabgroups/TabGroupsDialog.java` — natywne dialogi Android (dopiero do podpięcia).

Aktualnie nie ma gotowego APK do instalacji. Nie udostępniamy płatnego kodu ani nie obchodzimy podpisów i weryfikacji licencji.

## Etap 3 — eksperymentalny hook Activity (nieprzetestowany na urządzeniu)

- `src/inject/java/org/local/tabgroups/InjectedBrowserApp.java` — eksperymentalna klasa startowa dziedzicząca `BrowserApp` (bez zastępowania jej działania).
- `src/inject/java/org/local/tabgroups/TabGroupsOverlay.java` — przycisk *Grupy* i panel wyświetlający karty konkretnej grupy; nie zmienia oryginalnego adaptera 1DM+.
- `src/inject/stubs` — **wyłącznie kompilacyjna** deklaracja `BrowserApp`, nie wolno wstrzykiwać jej do DEX.
- GHA kompiluje tylko nowy `classes.dex` do prywatnego testu; nie trafia tam płatny APK.
- Aby uzyskać APK, trzeba lokalnie wbudować tę klasę do skompilowanego APK, zmodyfikować manifest i podpisać cały pakiet. Instalacja może wymagać jego brakujących splitów (`base__abi`, `base__density`).

**Status:** potrzebny test runtime; GitHub nie posiada i nie przetwarza oryginalnego APK.

## Etap 3 — lokalna kompilacja testowej kopii APK (21.10.2026)

W środowisku prywatnym (nie na publicznym GitHubie) z wejściowym APK użytkownika i `classes.dex` z CI wykonano następujące kroki:

1. Spakowano oryginalne zasoby i DEX-y bez publikowania ich w repozytorium.
2. Podmieniono nazwę klasy Application w binarnym manifeście na `InjectedBrowserApp`.
3. Dodano skompilowany moduł jako `classes4.dex`.
4. Na potrzeby jednego, niekompletnego base APK usunięto z manifestu wymóg `base__abi,base__density` — **nie uzupełnia to brakujących zasobów**.
5. Podpisano własnym, testowym certyfikatem APK Signature Scheme v2.
6. Oficjalne narzędzie Android `apksigner verify --verbose` potwierdziło poprawny podpis v2. Archiwum ZIP przeszło test integralności, a `resources.arsc` i pliki DEX są wyrównane do 4 bajtów.

**To nie jest zweryfikowany instalator produkcyjny.** Nie przeprowadzono instalacji ani testów urządzeniowych. Brakujące splity i ewentualna kontrola licencyjna mogą uniemożliwić uruchomienie aplikacji. Własny podpis nie pozwala na aktualizację oficjalnego 1DM+ bez odinstalowania, a użytkownik NIE powinien odinstalowywać oficjalnej aplikacji w celu przetestowania eksperymentalnego pakietu — mógłby stracić sesje i dane.

Żaden fragment płatnego APK ani prywatny klucz podpisu nie jest commitowany do repozytorium.
