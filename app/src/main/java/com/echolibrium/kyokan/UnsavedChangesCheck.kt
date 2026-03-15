package com.echolibrium.kyokan

/**
 * F-01: Interface for fragments that can have unsaved changes.
 * MainActivity checks this before switching tabs.
 */
interface UnsavedChangesCheck {
    fun hasUnsavedChanges(): Boolean
}
