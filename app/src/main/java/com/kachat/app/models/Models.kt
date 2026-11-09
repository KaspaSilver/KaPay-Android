package com.kachat.app.models

import androidx.room.Entity
import androidx.room.Index

/**
 * The app's one display-name rule for an address (iOS `ContactsManager.displayName`, e52357d):
 * the name you gave them ([alias]), else the name it has in the Address Book (iOS 00767a4), else
 * their `.kachat` name - KNS is not consulted, on any
 * network since iOS 7227d69 (mainnet has no launched registry yet, so there it is always the
 * short address); the KNS branch ([knsName]) is kept, unreachable; else [fallback], the short
 * address. The `.kachat` name comes from the registry's identity cache
 * ([com.kachat.app.services.kachatnames.KachatNamesRegistry.kachatName]): read in a composable,
 * the name re-renders when it lands. Every place that names an address goes through this.
 */
fun addressDisplayName(
    address: String,
    alias: String? = null,
    knsName: String? = null,
    fallback: String = com.kachat.app.util.KaspaAddress.shortDisplay(address)
): String {
    // Your own chatting address goes by who you are on chain - your .kachat domain, else the
    // short address - never a contact or Address Book name (the chat LIST names that chat "Your
    // Address" itself). So the self chat's header reads kaspero.kachat the moment the domain
    // exists, and the plain address before it (iOS 7d11735).
    if (!com.kachat.app.services.WalletManager.isOwnChattingAddress(address)) {
        alias?.takeIf { it.isNotBlank() }?.let { return it }
        com.kachat.app.services.AddressBookManager.nameFor(address)?.let { return it }
    }
    if (com.kachat.app.services.kachatnames.KachatNamesService.isEnabled) {
        return com.kachat.app.services.kachatnames.KachatNamesRegistry.kachatName(address) ?: fallback
    }
    return knsName?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * A user-saved "host:port" node address, kept purely for quick copy/paste into the
 * trusted-node field in Connection Settings - not itself used for connections.
 */
data class SavedNodeAddress(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String,
    val address: String
)
