# 1DM+ 18.2 — pełny pakiet .apks: weryfikacja lokalna

Dane wejściowe od właściciela aplikacji: base.apk (19 502 716 B), split_config.arm64_v8a.apk (20 280 484 B), split_config.xxhdpi.apk (654 416 B).

## Etap ukończony
- Kod grup został skompilowany do DEX (39 984 bajty) w GitHub Actions.
- AndroidManifest.xml w base wskazuje Application: org.local.tabgroups.InjectedBrowserApp.
- Base zawiera classes4.dex z własną nakładką grup.
- Dodatkowe dwa splity są obecne w końcowym archiwum; trzy podpisy mają ten sam certyfikat testowy.
- apksigner verify potwierdził schematy Android v2/v3 dla każdej części. Każdy APK i kompletne archiwum .apks przeszły test ZIP CRC.
- SHA256 testowego certyfikatu: fd5b07df1c08635b5d1337dea777d40d972f9d21681c8d1fad4728caa64d1e5c.

## Ograniczenia
Nie było instalacji na fizycznym Androidzie ani emulatorze. Wynik budowy nie potwierdza poprawnego uruchomienia, działania licencji, pobierania ani trwałości grup.

Wariant z oryginalnym pakietem nie może zaktualizować wersji sklepowej, ponieważ jest podpisany innym kluczem. Nie odinstalowywać oryginalnego 1DM+.

Wariant testowy pod osobnym identyfikatorem pakietu nie powinien naruszyć instalacji sklepowej, ale może nie przejść weryfikacji legalnej licencji 1DM+ i może mieć problemy z wewnętrznymi identyfikatorami. Nie należy omijać tej weryfikacji.

**Prywatność**: binaria płatnej aplikacji, klucz i podpisany instalator nie znajdują się w publicznym repozytorium.
