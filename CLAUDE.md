# CLAUDE.md

Guidance for Claude Code in this repository.

## What KaPay is

KaPay (named KaChat Wallet until 2026-10-27) is a standalone Android Kaspa wallet, split out of
KaChat so KaChat can stay focused on 1:1 and group chats - the Android counterpart of the iPhone's
KaPay (`KaspaSilver/KaChat-Wallet-iPhone`), which it matches screen for screen. It keeps KaChat's
wallet features: Portfolio, Cold Storage, the .kachat names marketplace, Contacts and the
account's Wallet tab (receive, send, request payment, addresses, domains, help).

The source started as a copy of KaChat-Android (`KaspaSilver/KaChat-Android`) at commit `9289fad8`
(5.2.2, 2026-10-22); everything chat has since been deleted (see "No chat code" below). The Kotlin
namespace is still `com.kachat.app`, so wallet fixes port across from KaChat-Android file for
file. Only what people read says KaPay; identifiers, folder and class names keep KaChat, and texts
about the KaChat ecosystem (KaChat profiles, .kachat names, the Nextcloud `KaChat` folder) still say
KaChat.

When the iPhone wallet changes, the same change lands here: its commits name the iOS files, and
the Android ones usually have the same names (Swift `FooView` -> Kotlin `FooScreen`).

Saved addresses are **Contacts** in everything people read (named the Address Book until
2026-10-28, iOS 8118f09): the Wallet tab's Contacts row, the Contacts pill under a recipient
field, Settings > Storage's Contact Photos; the icon is `Icons.Outlined.Badge` (iOS
`person.text.rectangle`). The code keeps its names (`AddressBookManager`, `AddressBookScreen`).

## Dock

Five fixed tabs, `WALLET_DOCK` in `app/src/main/java/com/kachat/app/ui/KaChatApp.kt` (rendered by
`MainShell`), opening on Wallet (`HOME_ROUTE`):

| Tab | Screen |
|---|---|
| Portfolio | `PortfolioScreen` - one floating + sheet for every add, import and export |
| Storage | `ColdStorageListScreen` |
| Wallet | `ProfileScreen` (`ui/screens/Screens.kt`, route `profile`; pieces in `WalletTabComponents.kt`) - the home tab, as on KaChat's Wallet tab: the total balance split into chatting and spending (`WalletBalanceCard`), Send, Receive and Request (`CreatePaymentRequestSheet`), Backup Hygiene when due, and the Manage Addresses and Chatting Address squares; then Your Domains, Contacts (`AddressBookScreen`, its own pushed screen) and Help. No profile card: those belong to KaChat and KaPost. About is in Settings |
| .kachat | `KaChatHubSections.kt` `KachatMarketScreen` + `KachatNamesLiveScreens.kt` - the full .kachat names marketplace: the logo and four numbered cards (Marketplace, Available, Expired, Activity), each opening its page; only the Marketplace has the name search; dock icon is the name tag with an @ (`dockIconPainter` in `ui/ChessTabIcon.kt`) |
| Settings | `SettingsScreen(isDockTab = true)` - no Done button; alphabetical rows with no descriptive text, as in KaChat: About, Account Name, Back Up Account (Complete Your Backup, View 12 or 24 Words, Back Up to NFC Card), Connection, Customization, Diagnostics, Notifications, Security, Storage (`settings_section/<key>`); then Delete Account (`DeleteAccountSheet`, the warning the sign-in screen's Delete also shows; both ask for the phone's lock; `WalletViewModel.deleteCurrentAccount`) and Log Out |

`resolveDock` ignores any saved arrangement; there is no Kaspa Hub, Customize Dock or Simple Mode.

Account setup: `KaChatApp` -> `OnboardingScreen` (create / import a seed) -> `MainShell`. Create
New Account makes an account in one tap: 24 words, no passphrase, the next free default name
(`WalletManager.nextDefaultAccountName`: My Account, My Account 2 and on). "Advanced: Create with a
Passphrase" is the only other way: also 24 words, through `PassphraseSetupScreen`. Import still
takes 12 or 24 words. Creating never shows the seed phrase: an account made here is owed a backup
(`AccountBackupStatus`), and until the backup steps (`AccountBackupScreen`: why, get ready, the
words six at a time, a check of four random words, the passphrase and its check if there is one,
where to keep them; FLAG_SECURE wherever a word or the passphrase shows) are finished, every tab
shows `AccountBackupBanner` under the balance and just above its title (`MainPageHeader`'s `banner`;
.kachat has no title), red once the account holds funds, spending addresses included, and the Delete
Account warning leads with Back Up Account; deleting is never
blocked. The steps never offer a copy; Settings > Back Up Account > View 12 or 24 Words does. Imported and older
accounts are never owed one. Finishing the steps or importing starts a six-month timer; when it runs out the
Wallet tab shows `BackupHygieneCard` under Send, Receive and Request, and `BackupHygieneScreen` asks for five
random words in random order on `SeedPhraseKeyboard`, then the passphrase if the account has one.
Passing, or Can't Complete This and the backup steps again, starts the timer over; closing leaves
the card up. The passphrase entry screen (`PassphraseAddressPreview`) shows the chatting address and
its balance as you type. Import has no wallet list (iOS 018e634): after the words and "Did you use a
passphrase?", `WalletViewModel.findImportAccounts` derives every `WalletSourceFamily` (Standard 0..<50,
KDX and OneKey 0..<20, Chainge 0), makes one node balance call plus a REST transactions-count per
type's #0 (`KaspaWalletEngine.addressHasHistory`), keeps those with funds or history, and lists them
with the most funded type first: "Your Account" (`PassphraseFoundStep`) with No, or live under the
passphrase field. The pick goes to `setPendingImportChoice`; nothing found is Standard #0, and "See
Every Wallet Type" lists each type's #0. Import waits for that search (iOS 0799cec).
A created account is marked as owing its backup just before its keys are saved
(`commitCreatedWallet`'s `beforeSaving`), so a kill mid-create can't skip it.
Every sign-in opens on Wallet. Biometrics for account login are off by default, and switched off
once on existing installs (iOS 953b255, `AppSettingsRepository`), so a phone with no passcode can
still see its account; the words, private keys and sends still need the phone's lock. On, they're
asked when signing back in to a saved account, not on every app open: a cold start goes straight into
the active account unless Log Out was the last thing done (`WalletManager.isLoggedOut`), and a
signed-out account stays unregistered from push. All this is iOS 99ab88a. Deleting the active account
leaves no account active (`getActiveAccount` has no first-account fallback) and the phone signed out,
on the sign-in screen, until a saved account is signed in to (iOS `deleteWallet`). While signed out
nothing runs for the account: no live subscription, catch-up, `SyncWorker` pass or "Received"
notification (`AddressActivityNotifier.walletAddressOrNull`, `WalletManager.loggedOutFlow`).

`HotWalletBalance` (`services/`, iOS 953b255): the chatting address plus every spending address
(hidden ones too, cold storage not), or a dash until both are known. The top bar on every tab
(`BalanceTopBarLabel`) and the Wallet tab's card read it; a single address's screen still shows that
address. The Chatting Address is address #0, so the legacy chain's first spending slot shows as #1
(`WalletManager.addressNumber`).

Every single-address page (the Chatting Address, each spending address, each Cold Storage address) has
one `AddressOptionsButton` at the top right, opening `AddressActionsSheet` (`ActionSheet.kt`): no View
Public Key on a quantum address, no View Private Key on a Cold Storage address (iOS 2da5f4b).

People read "12 or 24 words" rather than "seed phrase" or "recovery phrase", as in KaChat (iOS
953b255); the code keeps its names (`SeedPhraseScreen`, `R.string.seed_phrase`). Creating with a
passphrase opens on the entry screen, since "Advanced: Create with a Passphrase" already answered
the question; only an import asks it.

**NFC card backup** (`services/NfcCard.kt`, `ui/screens/NfcCardScreens.kt`; iOS 4f5dfd2), in
production builds since iOS 953b255: `NfcCard.isAvailable` hides it where the phone has no NFC, and
the main manifest declares NFC as optional. An extra copy of the words
of a seed phrase the user already has, on a Java Card loaded with
[KaChat-NFC](https://github.com/KaspaSilver/KaChat-NFC) through Kaspa Quick Start's NFC Card page.
Not cold storage: the card never generates or uses a key. The card commands and backup format are
that repo's `docs/PROTOCOL.md`, shared across apps; the payload is iOS's version 2 (`chattingAddress`,
no passphrase), so a card moves between phones. Settings > Back Up Account > Back Up to NFC Card (behind the seed
phrase's biometric gate) only exists for an account with a passphrase, which is what protects it:
it's never written to the card. Import's name screen has Restore from NFC Card at the bottom; it reads with the 6-digit
PIN, asks for the passphrase and checks it against the saved chatting address. Saving to a card never
clears the backup warning.

The sign-in screen says "KaPay" with the wallet logo and no tagline. Settings is in the Dock table:
Connection is Push / Node / Domains (the .kachat indexer is editable there) / Explorer, Notifications
is one page, Storage holds the Nextcloud backup; the Wallet tab keeps Your Domains, and a profile is
edited and shared in KaChat or KaPost, not here.
Text that came from the iPhone with its translations lives in `res/values*/strings_kapay.xml` (iOS
953b255's `Localizable.strings`, same wording) and is read with `tr(R.string.kp_...)` (`util/AppText.kt`),
which works outside a composable and follows Settings > Language. Other new text stays English until iOS translates it.
In debug builds `adb shell setprop debug.kachat.fail_send 1` makes sends fail, for testing.

## Send Kaspa (and every other send)

Every screen that moves Kaspa is the same steps (iOS wallet 05db583), built from the shared pieces
in `ui/screens/SendKaspaComponents.kt` and `SwipeUpSendScreen.kt`. Change those pieces, not one
screen.

1. **To**: `SendStepScreen` + `SendStepQuestion` ("Who are you sending to?") + `SendRecipientCard`
   (address or name; Paste, Scan QR and Contacts pills under the field). Cancel sits at the bottom
   beside Continue (`SendContinueButton`) on a first step; later steps have Back and Cancel in the
   header (`SendStepHeader`).
2. **From**: the Chatting Address, or Choose an Address with the list right on the screen
   (`OwnAddressList`, no sheet). Skipped when the From is fixed (an address's own Send).
3. **Amount**: `KaspaKeypadAmountEntry`, the app's own number pad (`KaspaKeypad`,
   `KeypadAmountText`), so no system keyboard covers the screen; "To" and "From" chips
   (`SendStepChip`), the KAS / currency switch and Max.
4. **Review**: `SwipeUpToSendScreen` with Back: the amount, To, From, Total. Only the fee and coin
   control can change (`SendFeeControls`); the fee's Edit (`onEditFee`) opens `SendFeeEditorScreen`
   (the fee on the keypad, never under the network's minimum, warned above ten times it).

Where: `SpendingAddressSendFlow` (`Screens.kt`: the Wallet tab's Send, an address's, a contact's,
paying a payment request, Compound UTXOs); Cold Storage's `ColdSendFlow` (then Sign with KasSigner
and the signed screen with swipe up to send); Request Payment's `CreatePaymentRequestSheet`
(`PaymentRequestViews.kt`: where to be paid, how much, the review with Copy and Share); .kachat's
`KachatTxSheet` / `KachatClaimSheet` (`KachatNamesLiveScreens.kt`: an entry step when the action
takes one, then the review with the fee editor and swipe up).

**A send with no answer** (`SubmitConfirmation`, `NodePoolManager.submitConfirmingKnown`; iOS
953b255): a node that took a transaction and never answered gets a longer look-up (six more, 3 s
apart), then `SendOutcomeUnknownException`: the screen says "Not confirmed" and offers only Done, since
Try Again could pay twice. A node's answer only counts if its id is the one computed here
(`KaspaTransactionId`), and a transaction the network can't see 5 s later is sent again, twice at most
(same transaction, so it can't pay twice). A node saying it already has this very transaction ("transaction
<id> is already in the mempool" / "was already accepted", "already spent by transaction <id>", our id in
the node's sentence, not rusty-kaspa's "Rejected transaction <id>:" prefix) makes it sent
(`SubmitConfirmation.isOwnDuplicateRejection`). Any other rejection after a try that lost its answer
keeps that lost answer and ends "Not confirmed" (stricter than iOS 1652437's `isVerdict` rule, where
only an "orphan" / "already" / spent reply keeps it; a connection that never opened is no lost answer
on both). After a rejection, a REST `is_accepted` counts only from the built-in REST server, never one
typed into Settings (`SubmitConfirmation.submitOrConfirmKnown(isKnownAfterRejection)`,
`NodePoolManager.isTransactionKnown(builtInRestOnly)`), and the mempool look-up always asks the
broadcast node too (`isInAnyMempool`). A node's
orphan pool doesn't count as the network having it (`NodePoolManager.isTransactionKnown`): the resend
check sends it again, and a rejected send some node holds as an orphan ends "Not confirmed". The
confirmed-only retry after an "orphan" / "already spent" / signature rejection (iOS has none) runs
only when no try lost its answer, the first transaction's id is known, no output of it is on chain
(then it was sent) and none of its confirmed coins is gone (then "Not confirmed"). Chained sends on
pending change stay (iOS spends confirmed coins only): Max, the previews and back-to-back sends count
that change. The reconnect retry (`refreshBroadcastConnection`) goes to the next probed Active node,
never an unprobed seed. A spending send's fresh change slot stays revealed once its transaction was
submitted (`sendKaspa(onSubmitting)`), whatever the outcome. An unknown send's coins aren't held: a
resend picks the same ones, so at most one of the two can land (see Coins). A plain send that moves
money isn't left in a node's orphan pool after its rejections (`escalateOrphan`, iOS 2da5f4b); a
chained one still asks for that from the start.

**Security**: `PrivacyCover` in `MainActivity` (`showPrivacyCover`) is a window of its own, so it covers
bottom sheets and dialogs too, whenever the activity is paused; each activity instance has its own (iOS's
one cover per scene). Android doesn't pause for the notification
shade, so unlike iOS that isn't covered. Android has no way for an app to refuse a third-party keyboard,
so there's no equivalent of iOS's rule: the words are typed on the app's own `SeedPhraseKeyboard`, the
passphrase and card PIN in `KeyboardType.Password` fields, which keyboards shouldn't learn from, but a
keyboard with its own network access still sees them. The words and passphrase sit in one
EncryptedSharedPreferences record (`WalletManager`), readable after the first unlock, unlike iOS's
`WhenUnlocked`: every key here is derived from the words, and push sign-in and `SyncWorker` need those
while the phone is locked. Moving them under a `setUnlockedDeviceRequired` key would mean re-encrypting
the only copy on phones whose Keystore may fail it, so they stay; everything people can read (View 12 or
24 Words, the backup steps, Back Up to NFC Card) is behind the device's lock; those four routes close
themselves when the back stack brings them back after the OS killed the app (`SeedRouteGate`). No keys
outlive an uninstall or leave the phone: `allowBackup` is false, and since targetSdk 31+ ignores that
for device-to-device transfer, `res/xml/data_extraction_rules.xml` (and `backup_rules.xml` for older
Android) exclude everything from cloud backup and transfer; uninstalling removes the file and its
Keystore key, so iOS's `noteKeysLeftByEarlierInstallIfNeeded` has nothing to do here. A seed store the
Keystore can't open anyway (WalletManager's, Cold Storage's) is copied aside into
`no_backup/unreadable_stores/`, never deleted, and starts empty, so the app opens on sign-in
(`StartupSafety.encryptedPrefsMovingAsideUnreadable`). The lock (`DeviceAuth`) is a strong biometric or
the screen lock, a weak biometric or the screen lock on Android 9-10, where androidx.biometric refuses
the strong pair. A copied private key or the words are wiped from the clipboard after 30 s, also on the
next return to the app if the app was away or the process died (`wipeSensitiveClipIfDue`); a clip the
app can't read then counts as ours. A `.kachat` name resolves only if the
chain holds its record (`KachatNamesRegistry.confirmOnChain`), and Accept Offer checks the on-chain amount
against what was shown. A `.kas` commit is filed before it's sent (`KnsRevealRecovery`), and if its reveal
never went out it is retried when the app becomes active, for an hour. `.kas` domain sends, profile saves and setting the primary domain ask for the device's lock. A bad fee rate
from a server is ignored (`ColdStorageSendEngine.quotedFeeRate`). Logs show only an address's last 8
characters (iOS 1652437's `AppLog`): there's no one log call to filter on Android, so log lines cut
addresses themselves (`util/LogRedaction.kt`: `redactedForLog`, `redactingAddressesForLog`,
`HttpUrl.forLog`), and the diagnostics export runs its logcat through `redactingAddressesForLog`, so an
address in an exception's message or a library's line leaves cut too.

**Face ID**: `SwipeUpToSendScreen` asks the device's lock (`authenticateWithDeviceCredential`) before
every swipe, whether Send Kaspa, Cold Storage's broadcast or a .kachat transaction (its `authReason`).
Declined, the screen settles back with everything filled in. A phone with no screen lock can't send
(`DeviceAuth` refuses and says why). Swiping up sends at about 30% of the screen. Touches while the
lock is asked are ignored, and one swipe starts one send (iOS 0799cec).

**Fees**: the send pays at the rate the Review quoted (`SpendingAddressSendFlow` passes
`liveFeeRateSompiPerGram` as `feeRateOverride`; Cold Storage already did), and the preview prices the
change at the script it really goes to (`WalletViewModel.sendChangeScriptLen`: a fresh quantum slot is
`SendFeeModel.P2SH_SCRIPT_LEN`, 35 bytes, for a primary's or a quantum source's change). Only the
primary rotates there; another quantum source just lists the slot. The Receive QR's slot is never given
to change or any other fresh allocation (`WalletManager.takenQuantumSlots`), a slot that has held change
or spent isn't shown again, and the QR is cleared on an account switch (iOS 2da5f4b). The recipient's amount is never cut to fit: too little left
for a change output is priced without one (`SelectionResult.changeOutputPriced` false, iOS
`selectUtxosForPayment`'s no-change try) and folded into the fee, and an amount the coins can't cover
says so before the swipe (`KaspaUtxoSelector.NOT_ENOUGH_FOR_AMOUNT_AND_FEE_MESSAGE`).
Change too small to keep goes to the fee, and the review counts it
(`SelectionResult.foldedChangeSompi`, the counterpart of iOS's `SendFeeEstimate`). Change worth more
than the transaction's fee is real money: the greedy selector tries another coin first and folds it
only if none lets it stay (iOS 2da5f4b `foldIfNothingBetter`). With no change
output, the recipient's output alone must fit storage mass (`KaspaUtxoSelector.changeIsKeptOrFoldable`;
the greedy selector adds a coin before giving up). The swipe (`SpendingAddressSendFlow` `canSend`)
waits until Max, the live fee quote and the fee (the preview, or coin control's) are in, and stays
off after a fee error, a preview no coin set fits included (`SendFeeModel.Preview.storageMassBlocked`);
Try Again under the error works the fee out again (`feeRefresh`, iOS 1652437). Max at Fast or Priority
prices the tier's extra from its own coins' base fee (`SendFeeModel.maxAfterFees`), is worked out again
when the speed, custom fee, coins or From change, and only the newest Max answer counts (`maxRun`).
At the swipe the send is rebuilt from that moment's coins; if it would pay more than the Review showed
(`SendFeeCheck`, 1,000 sompi margin) nothing is signed or sent (`SendFeeChangedException`) and the
Review re-estimates. iOS's 3-sompi selector buffer doesn't apply: the preview runs the send's own
selector (`KaspaUtxoSelector`), which has none.

**Coins**: a sent send's inputs stay out of the next send, Max and fee for two minutes whatever one
fetch says (`KaspaWalletEngine.recordSpend` / `reconcileUtxos`, `PENDING_UTXO_TTL_MS`; Cold Storage's
`PendingSpentOutpoints` holds its sent ones the same way). On top of that, whatever any node-pool
submit sends (every send, .kas and .kachat names, combines, Cold Storage) is held for the same two
minutes (`NodePoolManager.submittedInputs`, a `SubmittedOutpointHold`, recorded in
`submitConfirmingKnown`; iOS 1652437 `reserveOutpoints`): the hot wallet's reconcile, the KNS
inscription's funding and the .kachat names' funding and combine skip those coins. Neither holds
anything after "Not confirmed" (iOS 2da5f4b): a resend then picks the same coins, so at most one of
the two can land, where different coins would let both. A `.kachat` commit resend likewise ignores
the hold. Cold Storage's own
picks still skip only its sent coins, as on iOS. Available (`WalletViewModel.addressBalanceSompi`) and
the chatting address's Compound button count only the coins a send can spend (Android's rule:
`fetchUtxosOrThrow`, so mature mining rewards and the app's pending change count, unlike iOS's
`withdrawalSpendable`). Hot-wallet coin control lists every coin and greys out, with why, the ones a
send can't spend ("Just spent by a send", "Not spendable yet"; `KaspaWalletEngine.fetchCoinControlCoinsOrThrow`);
Cold Storage's lists them all. A Max or fee whose picked coins are all gone is that error, not
automatic selection. Cold Storage's coin control reads the node first, as its build does,
and a picked coin that is gone fails the build with "Selected UTXOs are no longer available". There is
no UTXO cache to drop: every send, Max and fee fetches fresh. An address with more than
`MAX_INPUTS_PER_TRANSACTION` (80) coins gets a Max of its largest 80 (`SendFeeModel.largestSpendable`)
and a Compound of one such chunk (`WalletViewModel.maxConsolidatableChunk`), and the amount's footnote
and the Review say so (`MaxEstimate.cappedAtInputLimit`). A payment request pasted into To fills the
send only when the paste is just that link. The KAS / currency switch keeps the exact KAS amount
(`KaspaFiatAmountState.kasText`).

**Accounts** (iOS 1652437): every way of removing an account (Settings' Delete Account, the sign-in
list's Delete) goes through `WalletViewModel.deleteWallet`, on both networks' addresses: contacts, bell
history, Cold Storage, the Portfolio, .kachat reminders, the backup status, the Nextcloud login (its app
password revoked first), the spending data (`WalletManager.removeSpendingData`: derived addresses, coin
and address labels, hidden set, Receive index, Manage Addresses snapshot, added-at time), its pending
.kachat commit salts and its Setup Guides switch (iOS 2da5f4b). Android keeps an account's
words inside its own record in one EncryptedSharedPreferences list, so iOS's keychain slot, snapshots,
recovery screen and Secure Enclave key have nothing to match: a record never outlives its words, a list
write that doesn't read back intact puts the previous list back (`saveAccountsKeepingExisting`), and the
Keystore key is never deleted. iOS tightening every words item to `WhenUnlocked` at launch has no
counterpart, for the reason under Security. The same words on the other network are the same account
(`importWallet` matches by `isSameAccount`); an NFC restore of an account already here keeps its name. A
sign-in error waits in `WalletViewModel.signInFailureMessage` for the sign-in screen, not in the
account's row. The passphrase search gives up after 12 s (`PASSPHRASE_SEARCH_TIMEOUT_MS`; "Skip the
Search", Search Again) and Import then uses #0. A new NFC PIN is typed twice, and a swapped card (one
with a PIN where the scanned one had none) stops before a PIN try. Screen Lock Required is a platform
dialog on the activity, above every open sheet and owned by none, one at a time (iOS gave its alert its
own window). The words page needs no screenshot warning: FLAG_SECURE blocks the screenshot iOS can only
warn about.

## No chat code

There is no chat code left: don't bring any back. Like the iPhone wallet's 301ebfb, KaPay deleted
1:1, group and public chats, KaPosts, Chess, Swap, bots, voice, the message store, chat push and
chat sync (`ChatRepository`, `GroupRepository`, `BroadcastRepository`, `ChatViewModel` and every
screen, service and util they used, with their tests). `WalletService` keeps the node connection,
sending and the REST reads; `SettingsViewModel` has the Kaspa Explorer choice and the diagnostics
export; `WalletViewModel` the onboarding hold and the own-address activity events. Push keeps its
registration shape: the chat fields go out empty (`PushRegistrationManager.register`).

## Nextcloud

`NextcloudSyncService` (when and what) and `NextcloudService` (the account and WebDAV) back up
Contacts and the Portfolio, nothing else (iOS 953b255). Connect with an app password.

- Contacts: KaChat's way, in the same file with the same key, so a contact added or deleted in
  either app reaches the other: `kachat-contacts.json` in the backup folder (`KaChat` by default;
  found on connect, or chosen in Settings), always in the encrypted v1 envelope (`BackupCrypto`; the
  key comes from the chatting address's private key). Inside is the contacts export file
  (`AddressBookManager.exportData`, with photos and `deleted`). One sync reads, merges (newest edit
  or deletion wins) and writes only if the file is missing something this device has. With Automatic
  Sync on (off by default): 15 s after a contact changes, at most every 90 s (300 s on metered
  networks), on background, and a foreground ETag poll every 30 s (60 s) brings in other devices'
  changes. A file this account can't read throws before any write, except its own damaged one, which
  is replaced, and a newer contacts file is never overwritten. So two accounts on one Nextcloud login
  need different Backup Folders: Connect's folder search only adopts a folder whose contacts file is
  this account's (its `walletHint`, or no hint and it opens with this key), and a contacts sync that
  fails in a way that won't fix itself (another account's file, say) is kept per account and shown in
  Settings until a sync succeeds or the Backup Folder changes. KaChat's NEXTCLOUD_SYNC.md, "Contacts
  only", is the contract.
- The earlier plain files (`KaPay Contacts.json` and the older names, all of them) are merged in once
  per account and never written again; once this account's encrypted contacts file is there they
  aren't merged at all (anyone who can write to the folder could have planted one). Never KaChat's
  `kachat-backup.json`.
- Portfolio: one file per portfolio in the backup folder, named what the portfolio is
  named ("Long Term.json"; "Long Term 2.json" only if another file has that name; a name no number
  can make allowed, one starting "KaPay Contacts", uses "Portfolio" as the base). Since KaPay iPhone's
  change of 2026-11-06 each file is in the contacts' encrypted v1 envelope (`BackupCrypto`, same key,
  `walletHint`, a fresh nonce on every write); writers always encrypt, and without the key nothing is
  read or written. An envelope that opens and holds this account's `walletAddress` is this account's;
  one with this account's `walletHint` that won't open is its damaged file, whose name is used again and
  written over; any other envelope, or a file that isn't a portfolio, is someone else's. A plain file
  is still read, and this account's is rewritten encrypted at the next backup; after a full backup
  plain files aren't read again (`isPlainPortfoliosRetired`, iOS `plainPortfoliosRetiredKey`), and
  once any of this account's files is encrypted a plain one is someone else's, since anyone can
  write one. Contacts merged from any file can't be dated later than the moment they're
  read (`AddressBookManager.mergeBackup`). Exports stay plain CSV. Never one
  `KaPay Portfolio.json`: that earlier file is merged in once per account and never written again -
  with an owner inside, only into that account; without one, only into the first account on this
  device to use that login (iOS's `singlePortfolioLoginsKey`) and only if none of its portfolio or row
  ids belong to another wallet here. No merge ever moves another wallet's rows: incoming portfolios
  and rows whose ids another wallet on this device holds are left out (`PortfolioLedgerStore
  .importFromArchive`, which also runs under the same lock as every portfolio and row save).
  Each file (`PortfolioBackupFile`, `type` "kapay-portfolio") holds that portfolio's transactions and
  fees, every deletion the account knows of, and the owning `walletAddress`. Another account's file,
  or any other file, is left alone. A renamed or deleted portfolio's old file is removed, never one
  whose name this backup just wrote; the other files carry the deletion, and when every portfolio
  here was deleted on another device the import leaves a new untouched "Portfolio 1" (when the only portfolio left is the untouched seed, which is otherwise
  never uploaded, the seed's file is uploaded to carry it, and isn't written over another device's
  seed file carrying the same deletions; another device's seed is never taken in
  (`PortfolioLedgerStore.importFromArchive`); with nothing uploaded no deleted portfolio's file is
  removed). An edit during an upload keeps the backup owed (`portfolioChanges`). A backup snapshots the wallet with its own stored login and folder (`BackupTarget`) and
  uses them for every read, PUT and DELETE, stopping once another wallet is active. With Automatic Sync on they upload a few seconds after a change and on
  background, and merge in what's on the server when the app opens, at most once a minute. A CSV
  export is named the same way ("Long Term.csv"). A CSV import (`PortfolioRepository.importCsv`)
  replaces a row the portfolio already had in the same second (the export writes whole seconds),
  each at most once, keeping that row's own time and source; rows of the file itself never replace
  each other. Importing a contacts file dates a deleted contact after its deletion, so it stays.
  Settings' Portfolio Backup footer says the files are encrypted (English until iOS's new wording is
  in `strings_kapay.xml`).
- A manual backup merges the server's copy in first, and a fresh connection merges both in once, which
  is why there's no Restore button. Contacts and Portfolio import/export use the `KaChat` folder and
  stay plain. A 401 stops every automatic sync (`needsReconnect`) and Settings says so. Connect saves the
  login to the account it started in (it fails if another became active). Disconnect revokes the app
  password on the server, and removing an account revokes its stored one (iOS
  `revokeStoredAppPassword`, at most 8 s), unless another account on this device signs in with the same server,
  user and app password. Deleting an account leaves its backup files on Nextcloud: there is no option
  that removes them (none on iOS either).

## What runs

Room (`services/database/KaChatDatabase.kt`, version 43) holds only the Portfolio:
`portfolio_transactions` and `portfolios`. `MIGRATION_42_43` drops KaChat's chat tables and keeps
those two; anything older is rebuilt empty. Contacts live in SharedPreferences
(`AddressBookManager`), settings in DataStore (`AppSettingsRepository`, which still has KaChat's
unused chat keys).

What runs is the wallet: the node pool; `OwnAddressSubscription`, the live UTXO subscription to
the wallet's own addresses (main, spending, cold storage) while the app is open - a node with a
UTXO index streams `NotifyUtxosChanged` batches into `AddressActivityNotifier.handleLiveUtxoChanges`
("Received KAS", self-sends suppressed) and a balance refresh, re-subscribing after a dropped
stream, a changed address set or at once on a change of default network, and retrying a failed
subscribe for as long as the app is open (iOS wallet `WalletService.setupUtxoSubscription`);
`AddressActivityNotifier`'s catch-up (on foreground, when the subscription comes up, and a 60 s
fallback poll only while it is down); Portfolio, Cold Storage and .kachat. `SyncWorker` (every
15 min) refreshes the balance and runs one receive pass. Notification channels carry no launcher
badge.

`KachatNamesRegistry` reads .kachat names through the indexer URL (Settings > Connection > Domains;
blank = the network's default) while it serves this registry and keeps up, else walks the chain
itself. An indexer that fails three requests in a row within two minutes (`fromIndexer`,
`indexerOutageConfirmed`) moves every read to the chain, market reads included; one timeout only
fails that read. After a failover the indexer isn't tried again for 2 minutes, doubling to 15, until
one of its data reads works. The chain copy answers only once a walk has reached the live UTXOs
(`readChainState`, `reachedTip`): before that a read fails, so a name never reads as free or unowned
and saved names keep showing. While the indexer is in use the copy is walked every 30 minutes (2
hours on a metered connection). Without an indexer, other people's profiles come from their own
chain records (`chainProfile`). A failed read never counts as "none": screens keep what they show, a
typed name says it couldn't be looked up, and a pending claim waits. `confirmOnChain` always checks
the record's live UTXO: a chain copy can be hours old. Anything that spends or withdraws on a registry
answer first asks `answersAreCurrent` (claims, automatic offer withdrawals).

## Push

See `PUSH_SERVICE.md`. KaChat's indexer is `https://indexer.kachat.app` - the default for push and the
.kachat indexer alike (`AppSettingsRepository.DEFAULT_*`); a saved `kachat.duckdns.org`, its old
host, reads as it, and the device unregisters from the old host before registering with the new.
Testnet has no default indexer (blank: no push, .kachat names from the chain), and only
`https://kachat.app` web links are opened (not the old `kachat.duckdns.org` ones).
The wallet registers with KaChat's push service only for "Received KAS"
on its own addresses (`watch_only_addresses`) and .kachat events (`primary_address`, which keeps it
registered with Address Activity off), with
`apns_topic` `kapay.kachat.app` (the app's package; the iPhone wallet sends `com.kachat.wallet`).
FCM needs `app/google-services.json` (gitignored) from KaChat's Firebase project (`kachat-fc22a`),
where `kapay.kachat.app` and `kapay.kachat.app.debug` are registered.

Claude never changes the server's code. Anything the server needs for the Android apps is handed
off in the KaChat indexer repo (`/Users/restosaved/KaChat Server/kachat-indexer`,
`KaspaSilver/KaChat-Indexer`) as one doc per change in its `docs/`, in that repo's format (a dated
title, "**For:** the indexer session. **From:** ...", the rule, what happens today, a suggested fix,
how to test), e.g. `docs/ANDROID_APP_TOPICS.md`. Commit only that doc there.

## Logo

`tools/make_logo.py` draws the logo and launcher icons (teal wallet, KaChat's backward K - the shape of KaChat's icon) - the
iPhone wallet's script with Android's adaptive-icon layers added:

```bash
python3 tools/make_logo.py app/src/main/res/drawable-nodpi/kachat_wallet_logo.png 512 logo
python3 tools/make_logo.py app/src/main/res/drawable-nodpi/kachat_wallet_logo_dark.png 512 dark   # the privacy cover in dark mode
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do n=${d#*:}; d=${d%:*}; f=$((n*9/4))
  python3 tools/make_logo.py app/src/main/res/mipmap-$d/ic_launcher_foreground.png $f foreground
  python3 tools/make_logo.py app/src/main/res/mipmap-$d/ic_launcher_monochrome.png $f monochrome
  python3 tools/make_logo.py app/src/main/res/mipmap-$d/ic_launcher.png $n logo
  python3 tools/make_logo.py app/src/main/res/mipmap-$d/ic_launcher_round.png $n logo
done
```

The status-bar icon is the vector `drawable/ic_kachat_wallet_notification.xml`.

## Identifiers

- `applicationId` `kapay.kachat.app` (`kapay.kachat.app.debug` for debug builds), the package
  registered in Play Console; the iPhone wallet's bundle is `com.kachat.wallet`. Nothing is
  shared with KaChat (`com.kachat.app`), so the two apps never read each other's storage or keys.
- URL schemes `kapay` (what KaPost and KaChat open to hand over a payment request:
  `kapay://pay/<address>?...`, the same tail as `kachat://pay/`; `kapay://market` opens the .kachat
  Marketplace page) and `kachatwallet` (its old name,
  still accepted). No `kachat://` or `https://kachat.app` links and no share target: those belong
  to KaChat.
- Version: `app/build.gradle.kts` (`versionName`, `versionCode`, `kachatBuildNumber`): 5.2.3 (6),
  versionCode 13, matching the iPhone wallet's version and build.
- Signing: `keystore.properties` and the keystores are gitignored and the owner's. KaPay's
  `keystore.properties` holds only the Play upload key (`kapay-upload.jks`, alias `kapay-upload`),
  which signs `bundlePlayRelease`; the GitHub APK keeps the debug key until a `storeFile` key is
  added there.

## Build

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assemblePlayDebug
```

Other KaChat Android projects on this machine share the Kotlin compile daemon; when it is busy,
add `-Pkotlin.compiler.execution.strategy=in-process`. Claude may compile to catch build errors,
but never runs the app on an emulator or device: the owner does all testing. Release and Play
builds are the owner's too.
