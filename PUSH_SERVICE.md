# Push: what KaPay for Android needs

KaPay uses KaChat's push service (`kachat-indexer/kasia-indexer`, `indexer/src/push.rs`,
default `https://indexer.kachat.app`, formerly `kachat.duckdns.org`), the same one the iPhone wallet uses - see the iPhone
wallet's `PUSH_SERVICE.md` for the service side. It asks for only two kinds of push:

| Push | How the wallet asks for it |
|---|---|
| **Received KAS** (`type: address_activity`, thread `funds:<address>`) | its own addresses in `watch_only_addresses` |
| **.kachat name events** (`type: name_event`) | its main address in `primary_address` |

## What the app sends (done)

`POST /v1/push/register`, same shape and signed auth as KaChat (`PushRegistrationManager`), with:

- `platform`: `"android"`, `device_token`: the FCM token
- `watched_addresses`: `[]`, `watched_group_ids`: none, `watched_broadcast_channels`: `[]`,
  `aliases`: `[]`, `kaposts_pubkey`: null, `kaposts_watch_authors`: `[]` (no chats or KaPosts)
- `primary_address`: the wallet's main address
- `watch_only_addresses`: the main address, its spending addresses (while their switch is on)
  and the Cold Storage addresses whose account notifies - at most 200
  (`AddressActivityNotifier.pushWatchOnlyAddresses`). With Address Activity off there is nothing
  to watch there, and the device stays registered for .kachat events through `primary_address`.
- `apns_topic`: `"kapay.kachat.app"`, the app's own package (debug builds send it too). FCM
  delivery doesn't use it; the service reads it to send a wallet device only `address_activity`
  and `name_event` pushes (`WALLET_PUSH_TYPES`), so it has to treat `kapay.kachat.app` as a wallet
  topic, as it does the iPhone wallet's `com.kachat.wallet`, and keep it among its allowed topics.

The registration is re-sent when Address Activity or the spending switch changes and on every
foreground (skipped when nothing changed), so a new spending slot or Cold Storage account is
picked up.

## Firebase (owner's step)

FCM only works once `app/google-services.json` is in place (gitignored; without it the app builds
and push is simply off - see the conditional plugin apply in `app/build.gradle.kts`).

1. In the Firebase project the push service already sends with (KaChat's), **add an Android app**
   with package name **`kapay.kachat.app`** - and **`kapay.kachat.app.debug`** for debug builds.
   It has to be the same project: the service sends with that project's service account, and an
   FCM token only works within its own project.
2. Download `google-services.json` (it lists every app in the project) to `app/google-services.json`.

On the service, Android adds one topic, `kapay.kachat.app`, treated like `com.kachat.wallet` (above);
no new key or credential. Handed off in the indexer repo as `docs/ANDROID_APP_TOPICS.md`
(KaChat-Indexer 516d70b); until it ships, the service drops the unknown topic and treats the phone
as a KaChat device.

## In the app

The service sends `address_activity` to Android with a `notification` block, so while the app is
in the background Android draws it itself ("Funds received", with the wallet's status-bar icon from
the manifest's `default_notification_icon`); a tap lands in `MainActivity.applyFcmNotificationTarget`
and opens that address's history. While the app is open the message reaches
`KaChatFirebaseMessagingService`, which shows nothing for it - the app's own Address Activity
notification announces that receive. The handler acts on `name_event` and `address_activity`
only; any other type is claimed and dropped (`ChatFeatures`).
