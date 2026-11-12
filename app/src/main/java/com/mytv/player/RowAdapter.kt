package com.mytv.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/** One focusable row type used for categories, channels, movies, seasons and episodes. */
class RowAdapter<T>(
    private val label: (T) -> String,
    private val image: ((T) -> String?)? = null,
    private val badge: ((T) -> String)? = null,
    private val onFocus: ((Int, T) -> Unit)? = null,
    private val onLongClick: ((Int, T) -> Unit)? = null,
    private val onClick: (Int, T) -> Unit,
) : RecyclerView.Adapter<RowAdapter.VH>() {

    var items: List<T> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }

    /** Row shown as the current choice (e.g. the open category). */
    var selected = -1
        set(value) {
            val old = field
            field = value
            if (old >= 0) notifyItemChanged(old)
            if (value >= 0) notifyItemChanged(value)
        }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val name: TextView = v.findViewById(R.id.name)
        val badge: TextView = v.findViewById(R.id.badge)
    }

    override fun getItemCount() = items.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        // Without change animations a rebound row keeps the D-pad focus.
        recyclerView.itemAnimator = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false))
        vh.itemView.setOnClickListener {
            val pos = vh.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(pos, items[pos])
        }
        vh.itemView.setOnFocusChangeListener { _, hasFocus ->
            val pos = vh.bindingAdapterPosition
            if (hasFocus && pos != RecyclerView.NO_POSITION) onFocus?.invoke(pos, items[pos])
        }
        if (onLongClick != null) {
            vh.itemView.setOnLongClickListener {
                val pos = vh.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onLongClick.invoke(pos, items[pos])
                true
            }
        }
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = label(item)
        holder.itemView.isSelected = position == selected
        holder.badge.text = badge?.invoke(item) ?: ""
        if (image == null) {
            holder.logo.visibility = View.GONE
        } else {
            holder.logo.visibility = View.VISIBLE
            holder.logo.load(image.invoke(item)) {
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                fallback(R.drawable.ic_tv)
            }
        }
    }
}
