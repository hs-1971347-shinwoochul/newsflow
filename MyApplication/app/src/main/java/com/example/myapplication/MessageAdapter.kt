package com.example.myapplication

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.util.Collections

class MessageAdapter(private val messages: MutableList<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_IMAGE = 0
        const val VIEW_TYPE_TEXT = 1
        const val VIEW_TYPE_SUMMARY = 2
        const val VIEW_TYPE_INPUT = 3 // 입력 텍스트 메시지를 위한 뷰 타입
    }

    override fun getItemViewType(position: Int): Int {
        return messages[position].viewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_IMAGE -> ImageMessageViewHolder(inflater.inflate(R.layout.item_message_image, parent, false))
            VIEW_TYPE_TEXT -> TextMessageViewHolder(inflater.inflate(R.layout.item_message_text, parent, false))
            VIEW_TYPE_SUMMARY -> SummaryViewHolder(inflater.inflate(R.layout.item_message_summary, parent, false))
            VIEW_TYPE_INPUT -> InputMessageViewHolder(inflater.inflate(R.layout.item_message_input, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is ImageMessageViewHolder -> holder.bind(message)
            is TextMessageViewHolder -> holder.bind(message)
            is SummaryViewHolder -> holder.bind(message)
            is InputMessageViewHolder -> holder.bind(message)
        }
    }


    override fun getItemCount(): Int {
        return messages.size
    }

    fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        Collections.swap(messages, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class TextMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.messageText)
        private val labelView: TextView = view.findViewById(R.id.textLabel)

        fun bind(message: Message) {
            labelView.text = if (message.isSummary) "Summary" else "Text"
            textView.text = message.textContent
        }
    }

    class ImageMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.messageImage)
        private val labelView: TextView = view.findViewById(R.id.imageLabel)

        fun bind(message: Message) {
            labelView.text = "이미지"
            Glide.with(itemView.context).load(message.imageUri).into(imageView)
        }
    }
    class SummaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val summaryLabel: TextView = view.findViewById(R.id.summaryLabel)
        private val summaryMessageText: TextView = view.findViewById(R.id.summaryMessageText)

        fun bind(message: Message) {
            summaryLabel.text = "summary"  // 라벨 설정
            summaryMessageText.text = message.textContent  // 요약된 메시지 표시
        }
    }

    class InputMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val inputTextView: TextView = view.findViewById(R.id.inputMessageText)
        private val labelView: TextView = view.findViewById(R.id.inputLabel)

        fun bind(message: Message) {
            labelView.text = "입력"
            inputTextView.text = message.textContent
        }
    }
}
