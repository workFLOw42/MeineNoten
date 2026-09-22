package de.workflow42.meinenoten.ui.components

import androidx.compose.ui.window.DialogProperties

/**
 * Dialog behaviour for every dialog that holds text the user has typed but not yet saved.
 *
 * Two defaults are wrong for an input form and are corrected here:
 *
 * **Tapping beside the dialog no longer closes it.** For a confirmation dialog that is a
 * convenient escape hatch, but here it throws away everything that was typed, without
 * warning and without a way back. A form is left through "Speichern" or "Abbrechen",
 * where the choice is visible and deliberate. Dismissing with the back gesture still
 * works, so the dialog never becomes a trap.
 *
 * **The window is laid out behind the system bars**
 * ([DialogProperties.decorFitsSystemWindows] = false),
 * which is what makes the keyboard measurable at all. A floating dialog window otherwise
 * reports no keyboard inset, so the dialog keeps its full height, and the keyboard covers
 * the lower fields together with the save button – the form then cannot be completed.
 * Combined with `Modifier.imePadding()` on the dialog the height shrinks to the free space
 * instead, and the scrollable content reaches every field.
 *
 * [DialogProperties.usePlatformDefaultWidth] is off because the platform width does not
 * survive the window
 * being resized by the keyboard on some devices; the dialogs set their own width instead.
 */
val InputDialogProperties = DialogProperties(
    dismissOnClickOutside = false,
    decorFitsSystemWindows = false,
    usePlatformDefaultWidth = false,
)
