package com.kachat.app.util

/**
 * The tail of a Kaspa address, for a log line: `…q8x7f2ab`.
 *
 * The diagnostics export bundles a day of log lines and mails them to support, so a full contact
 * address in one of them travels with it — enough to tie a person to their on-chain history. Eight
 * characters are plenty to match a line against an address you already have in front of you, and
 * useless for identifying one you do not. Mirrors iOS's `address.suffix(8)` (f7a207c).
 *
 * Only for Kaspa addresses. Node endpoints (`host:port`) are not identifying and stay whole — a
 * probe failure you cannot attribute to a node is not a diagnostic.
 */
fun String.redactedForLog(): String = if (length <= 8) this else "…" + takeLast(8)

// A Kaspa address anywhere in a line of text: plain, URL-encoded (`kaspa%3Aqq…`) or the `_` form used
// in keys and file names (iOS AppLog's pattern, 1652437: 12 or more characters after the prefix).
// Not `\b`: `_` is a word character, so an address right after one was missed (iOS 2da5f4b).
private val ADDRESS_IN_TEXT = Regex("(?i)(?<![a-z0-9])(kaspa(?:test|dev|sim)?)(:|%3A|_)([a-z0-9]{12,})")

/** [this] with every Kaspa address in it cut to its tail (see [redactedForLog]). */
fun String.redactingAddressesForLog(): String =
    ADDRESS_IN_TEXT.replace(this) { m -> m.groupValues[1] + m.groupValues[2] + m.groupValues[3].redactedForLog() }

/**
 * A request URL for a log line: scheme, host, port and path, with any address in the path cut to
 * its tail and the query string left out (REST paths and queries carry the wallet's addresses -
 * `/addresses/kaspa:…/balance`, `?owner=kaspa:…`). The diagnostics export bundles logcat.
 */
fun okhttp3.HttpUrl.forLog(): String {
    val portPart = if (port == okhttp3.HttpUrl.defaultPort(scheme)) "" else ":$port"
    val queryPart = if (encodedQuery != null) "?…" else ""
    return "$scheme://$host$portPart$encodedPath".redactingAddressesForLog() + queryPart
}
