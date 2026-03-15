package com.echolibrium.kyokan

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for the voice grid (Phase 3.3 — H5 fix).
 *
 * Replaces the old removeAllViews() + rebuild pattern that recreated 52+ cards
 * every 500ms during downloads. Uses DiffUtil to only update changed items.
 *
 * Three view types:
 *   HEADER  — engine section header (spans full grid width)
 *   CARD    — individual voice card (1/3 grid width)
 *   EMPTY   — "no voices match" label (spans full grid width)
 *
 * Usage with GridLayoutManager(3) + SpanSizeLookup to make headers/empty span 3.
 */
class VoiceGridAdapter(
    private val cardBuilder: VoiceCardBuilder,
    private val onPreview: ((voiceId: String, name: String) -> Unit)?
) : ListAdapter<VoiceGridItem, VoiceGridAdapter.ViewHolder>(DIFF) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CARD = 1
        const val TYPE_EMPTY = 2

        private val DIFF = object : DiffUtil.ItemCallback<VoiceGridItem>() {
            override fun areItemsTheSame(a: VoiceGridItem, b: VoiceGridItem): Boolean = when {
                a is VoiceGridItem.Header && b is VoiceGridItem.Header -> a.title == b.title
                a is VoiceGridItem.Card && b is VoiceGridItem.Card -> a.voiceId == b.voiceId
                a is VoiceGridItem.Empty && b is VoiceGridItem.Empty -> a.engine == b.engine
                else -> false
            }
            override fun areContentsTheSame(a: VoiceGridItem, b: VoiceGridItem): Boolean = a == b
        }
    }

    class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is VoiceGridItem.Header -> TYPE_HEADER
        is VoiceGridItem.Card -> TYPE_CARD
        is VoiceGridItem.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val container = LinearLayout(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(container)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ctx = holder.container.context
        holder.container.removeAllViews()

        when (val item = getItem(position)) {
            is VoiceGridItem.Header -> {
                val view = VoiceCardBuilder.buildSectionHeader(
                    ctx, item.title, item.subtitle, item.accent,
                    item.onAction, item.actionLabel
                )
                holder.container.addView(view)
            }
            is VoiceGridItem.Card -> {
                val preview = if (item.enabled) onPreview else null
                val view = VoiceCardBuilder.buildVoiceCard(
                    ctx, item.name, item.icon, item.iconColor,
                    item.status, item.statusColor,
                    item.voiceId, item.active, item.accent,
                    item.enabled, item.onClick, preview
                )
                holder.container.addView(view)
            }
            is VoiceGridItem.Empty -> {
                val view = VoiceCardBuilder.emptyLabel(ctx, item.message)
                holder.container.addView(view)
            }
        }
    }
}

/**
 * Sealed class representing items in the voice grid.
 * Data classes enable proper DiffUtil comparison.
 */
sealed class VoiceGridItem {
    data class Header(
        val title: String,
        val subtitle: String,
        val accent: Int,
        val actionLabel: String = "",
        @Transient val onAction: (() -> Unit)? = null
    ) : VoiceGridItem() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Header) return false
            return title == other.title && subtitle == other.subtitle
                && accent == other.accent && actionLabel == other.actionLabel
        }
        override fun hashCode(): Int = title.hashCode()
    }

    data class Card(
        val voiceId: String,
        val name: String,
        val icon: String,
        val iconColor: Int,
        val status: String,
        val statusColor: Int,
        val active: Boolean,
        val accent: Int,
        val enabled: Boolean,
        @Transient val onClick: (() -> Unit)? = null
    ) : VoiceGridItem() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Card) return false
            return voiceId == other.voiceId && name == other.name && icon == other.icon
                && iconColor == other.iconColor && status == other.status
                && statusColor == other.statusColor && active == other.active
                && accent == other.accent && enabled == other.enabled
        }
        override fun hashCode(): Int = voiceId.hashCode()
    }

    data class Empty(
        val engine: String,
        val message: String
    ) : VoiceGridItem()
}
