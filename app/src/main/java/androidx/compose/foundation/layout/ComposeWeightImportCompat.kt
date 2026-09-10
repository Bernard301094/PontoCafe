@file:Suppress("unused")

package androidx.compose.foundation.layout

/**
 * Compatibility symbol for legacy explicit imports of
 * `androidx.compose.foundation.layout.weight`.
 *
 * `Modifier.weight(...)` is a scoped member extension of RowScope/ColumnScope.
 * With the current Compose Foundation, an explicit package import named
 * `weight` may otherwise resolve to an internal RowColumnParentData helper and
 * fail Kotlin compilation. This zero-argument public overload exists only to
 * make that legacy import legal; real `Modifier.weight(...)` calls continue to
 * resolve to the official RowScope/ColumnScope implementation.
 */
fun weight(): Unit = Unit
