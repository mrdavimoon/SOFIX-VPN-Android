# SOFIX VPN for Android — beta

Early Android client for SOFIX VPN with Telegram Mini App pairing and independent device subscriptions.

- Device setup: SOFIX Android app requests a one-time pairing; user approves it in the official Telegram Mini App with the six-digit device code.
- Each paired Android device receives its own VLESS/REALITY subscription. Current limit: 3 active SOFIX Android installations per account.
- Old third-party VPN clients and legacy sharing are not affected by the Android device count.
- This is a **beta**, not a reviewed app-store release. APK builds must be tested on Android hardware before broad distribution.

## Build

GitHub Actions `build.yml` fetches the pinned v2rayNG GPLv3 upstream source at commit `118cf5bc7d0b290947b0827f5ad03e73018d8e5d`, its Xray and hev-tunnel dependencies, overlays our code, and builds a debug-signed arm64 APK. Source links/patches are in this repository. The build artifact includes source corresponding to the modified binary.

## License

GNU GPL-3.0-or-later, as a derivative of [v2rayNG](https://github.com/2dust/v2rayNG). See `LICENSE`. This repository contains only public Android client code — **not** SOFIX server code, credentials, customer databases, or signing secrets. Preserve original authors' attributions when distributing builds.


## Как устроено приложение

- **VPN внутри Android**: один главный экран, подключение / отключение, состояние соединения, обновление VPN-конфигурации, технические настройки.
- **Аккаунт через Telegram Mini App**: баланс, подписка, продление, устройства. Авторизация Android-приложения при первом запуске выполняется через Telegram и одноразовый код.
- **Подпись**: тестовые APK по-прежнему имеют сборочную debug-подпись. Перестановка вместо обновления может потребовать отвязки старого устройства. До публичной стабильной версии нужен постоянный ключ подписи, а затем — встроенный updater.

## Версии и список изменений

Каждый выпуск сопровождается текстовым списком изменений на русском языке: добавлено, исправлено, изменено и известные ограничения.

- Полная история: [CHANGELOG.md](CHANGELOG.md).
- Отдельные заметки: [release-notes/](release-notes/).
- Перед новой версией создайте `release-notes/vX.Y.Z-beta.N.md` по [шаблону](release-notes/TEMPLATE.md) и обновите `CHANGELOG.md`.
- Публикуйте через GitHub Actions **SOFIX Android APK (public beta)** → **Run workflow** → укажите `version` (например `v0.3.0-beta.1`). Сборка проверит наличие списка изменений и прикрепит к выпуску APK, `RELEASE_NOTES.md`, `CHANGELOG.md`, исходники и SHA256.
- Без `version` создаётся только тестовый APK без нового релиза.

Не объявляйте версию выпущенной, пока сборка, контроль SHA256 и публикация файлов не прошли успешно.
