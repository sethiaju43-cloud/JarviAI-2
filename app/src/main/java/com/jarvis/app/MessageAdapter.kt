package com.jarvis.app

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(val text: String, val isUser: Boolean)

class MessageAdapter(private val items: List<ChatMessage>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(R.id.tvMsg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val ctx = holder.tv.context
        val t = AppTheme.current(ctx)
        holder.tv.text = m.text
        holder.tv.setTextColor(t.text)
        val lp = holder.tv.layoutParams as FrameLayout.LayoutParams
        lp.gravity = if (m.isUser) Gravity.END else Gravity.START
        holder.tv.background = AppTheme.drawableFor(ctx, if (m.isUser) "msg_user" else "msg_bot")
        holder.tv.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10), UiKit.dp(ctx, 14), UiKit.dp(ctx, 10))
        holder.tv.layoutParams = lp
    }

    override fun getItemCount() = items.size
}
