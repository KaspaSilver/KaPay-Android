package com.kachat.app.models

import com.kachat.app.R
import com.kachat.app.util.tr

/**
 * Identity derivation-path family of the wallet a seed phrase was imported from. Different Kaspa
 * wallets put the same seed's funds and KNS domains on different BIP32 branches; import's
 * auto-detect checks every family and offers the likeliest account first (iOS 018e634), which is
 * what lets KaPay find them. Ported from iOS's
 * `WalletSourceFamily` (`Services/WalletManager.swift`), whose rules were replicated from
 * KasWare's ADDRESS_TYPES/RESTORE_WALLETS constants and its `hd-keyring.ts` `_pubkeyFromIndex`
 * derivation switch.
 *
 * - [KASPA_STANDARD]: `m/44'/111111'/0'/0/{i}` (receive chain, normal final index). KaChat's own
 *   family, and also what KasWare, Kaspium, Kastle, Core Golang CLI, OKX and Ledger seed imports
 *   land on. (Kastle derives `m/44'/111111'/{account}'/0/{index}`; account 0 is byte-identical to
 *   this, and its secondary variant `m/44'/111111'/0'/0/{accountIndex}` falls inside the same
 *   scan range, so it needs no derivation code of its own.)
 * - [KASPA_LEGACY_972]: `m/44'/972/0'/0'/{i'}` — KDX and the Kaspanet Web Wallet. NOTE: 972 is
 *   deliberately NOT hardened (KasWare's hdPath string "m/44'/972/0'" has no apostrophe on 972
 *   and their keyring derives it normally), while the change level AND the final index ARE
 *   hardened.
 * - [CHAINGE]: `m/44'/111111'/0'/0'` - a single hardened leaf; Chainge wallets have exactly one
 *   address.
 * - [ONE_KEY]: the standard `m/44'/111111'/0'/0/{i}` key, then a BIP340 taproot-style tweak —
 *   negate the private key when its compressed pubkey has an odd Y (0x03 prefix), then add
 *   taggedHash("TapTweak", xOnlyPubkey) mod n. The address derives from the tweaked key.
 *
 * Persisted by [name] on [com.kachat.app.services.WalletManager.Account.sourceFamily] — do not
 * rename the entries. A null/unknown stored value means [KASPA_STANDARD], which is exactly what
 * every account created before this feature existed used.
 */
enum class WalletSourceFamily {
    KASPA_STANDARD,
    KASPA_LEGACY_972,
    CHAINGE,
    ONE_KEY;

    /** Chainge wallets have exactly one address. */
    val supportsIndexScan: Boolean get() = this != CHAINGE

    /** How import names this wallet type (iOS 018e634 `displayName`). Standard covers KaChat,
     *  KasWare, Kaspium, Kastle, Core Golang CLI, OKX and Ledger, which all use the same addresses. */
    val displayName: String
        get() = when (this) {
            KASPA_STANDARD -> tr(R.string.kp_standard_kaspa_wallet_0671b)
            KASPA_LEGACY_972 -> "KDX / Kaspanet Web"
            CHAINGE -> "Chainge"
            ONE_KEY -> "OneKey"
        }

    /** How many addresses import's auto-detect checks (iOS 018e634 `importSearchDepth`). The
     *  standard chain keeps the depth of one page of the chatting address picker; the others only
     *  have to be recognised. */
    val importSearchDepth: IntRange
        get() = when (this) {
            KASPA_STANDARD -> 0 until 50
            KASPA_LEGACY_972, ONE_KEY -> 0 until 20
            CHAINGE -> 0 until 1
        }

    companion object {
        fun fromRaw(raw: String?): WalletSourceFamily =
            entries.firstOrNull { it.name == raw } ?: KASPA_STANDARD
    }
}

/** An address import's auto-detect looked at (iOS 018e634 `ImportAccountCandidate`,
 *  `WalletViewModel.findImportAccounts`). */
data class ImportAccountCandidate(
    val family: WalletSourceFamily,
    val index: Int,
    val address: String,
    val balanceSompi: Long = 0,
    val hasHistory: Boolean = false,
)
