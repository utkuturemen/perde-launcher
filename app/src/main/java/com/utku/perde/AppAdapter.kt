package com.utku.perde

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppEntry) -> Unit,
    private val onLongClick: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    var items: List<AppEntry> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val banner: ImageView = view.findViewById(R.id.app_banner)
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        view.onFocusChangeListener = FocusScale
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        if (app.banner != null) {
            holder.banner.visibility = View.VISIBLE
            holder.banner.setImageDrawable(app.banner)
            holder.icon.visibility = View.GONE
        } else {
            holder.banner.visibility = View.GONE
            holder.icon.visibility = View.VISIBLE
            holder.icon.setImageDrawable(app.icon)
        }
        holder.label.text = app.label
        holder.itemView.setOnClickListener { onClick(app) }
        holder.itemView.setOnLongClickListener { onLongClick(app); true }
        holder.itemView.setOnKeyListener(menuKey { onLongClick(app) })
    }

    override fun getItemCount() = items.size
}

/** Slight zoom on the focused card, the standard TV focus cue. */
object FocusScale : View.OnFocusChangeListener {
    override fun onFocusChange(v: View, hasFocus: Boolean) {
        val s = if (hasFocus) 1.08f else 1f
        v.animate().scaleX(s).scaleY(s).translationZ(if (hasFocus) 8f else 0f)
            .setDuration(120).start()
    }
}

/** The remote's menu key opens the same options as a long press. */
fun menuKey(action: () -> Unit) = View.OnKeyListener { _, keyCode, event ->
    if (keyCode != KeyEvent.KEYCODE_MENU) return@OnKeyListener false
    if (event.action == KeyEvent.ACTION_UP) action()
    true
}
