package com.mycompany.networkscanner.adapters

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.mycompany.networkscanner.R

data class ToolItem(
    val id: Int,
    val name: String,
    val description: String,
    val icon: String,
    val color: Int
)

class ToolsAdapter(
    private val tools: List<ToolItem>,
    private val onClick: (ToolItem) -> Unit
) : RecyclerView.Adapter<ToolsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.tool_card)
        val icon: TextView = view.findViewById(R.id.tool_icon)
        val name: TextView = view.findViewById(R.id.tool_name)
        val desc: TextView = view.findViewById(R.id.tool_desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tool, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tool = tools[position]
        holder.name.text = tool.name
        holder.desc.text = tool.description
        holder.icon.text = tool.icon

        val bg = holder.icon.background as? GradientDrawable
            ?: GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                holder.icon.background = it
            }
        bg.setColor(tool.color)

        holder.card.setOnClickListener { onClick(tool) }
    }

    override fun getItemCount() = tools.size
}
