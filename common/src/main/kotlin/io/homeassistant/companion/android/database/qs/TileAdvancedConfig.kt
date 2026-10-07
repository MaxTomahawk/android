package io.homeassistant.companion.android.database.qs

import io.homeassistant.companion.android.common.util.kotlinJsonMapper
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

fun decodeTileTextParts(value: String?): List<TileTextPart> = value?.takeIf {
    it.isNotBlank()
}?.let { runCatching { kotlinJsonMapper.decodeFromString<List<TileTextPart>>(it) }.getOrNull() }
    .orEmpty()

fun encodeTileTextParts(value: List<TileTextPart>): String? =
    value.takeIf { it.isNotEmpty() }?.let { kotlinJsonMapper.encodeToString(it) }

fun decodeTileIconRules(value: String?): List<TileIconRule> = value?.takeIf {
    it.isNotBlank()
}?.let { runCatching { kotlinJsonMapper.decodeFromString<List<TileIconRule>>(it) }.getOrNull() }
    .orEmpty()

fun encodeTileIconRules(value: List<TileIconRule>): String? =
    value.takeIf { it.isNotEmpty() }?.let { kotlinJsonMapper.encodeToString(it) }

fun decodeStringList(value: String?): List<String> = value?.takeIf {
    it.isNotBlank()
}?.let { runCatching { kotlinJsonMapper.decodeFromString<List<String>>(it) }.getOrNull() }
    .orEmpty()

fun encodeStringList(value: List<String>): String? =
    value.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.let { kotlinJsonMapper.encodeToString(it) }
