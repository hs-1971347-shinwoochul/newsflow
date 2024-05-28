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
        private val imageView: ImageView = view.findViewById(R.id.profileImage)

        fun bind(message: Message) {
            // 여기에 이미지 설정 코드 추가 (예: 기본 이미지 또는 URL에서 로드)
            Glide.with(itemView.context)
                .load(R.drawable.people) // 기본 이미지 또는 메시지의 프로필 이미지 URI
                .into(imageView)

            textView.text = message.textContent
        }
    }

    class ImageMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val imageView: ImageView = view.findViewById(R.id.messageImage)
        private val labelView: ImageView = view.findViewById(R.id.imageLabel) // TextView 대신 ImageView

        fun bind(message: Message) {
            // 여기에 이미지 설정 코드 추가 (예: 기본 이미지 또는 URL에서 로드)
            Glide.with(itemView.context)
                .load(R.drawable.image) // 기본 이미지 또는 메시지의 프로필 이미지 URI
                .into(labelView)

            Glide.with(itemView.context).load(message.imageUri).into(imageView)
        }
    }

    class SummaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val summaryLabel: ImageView = view.findViewById(R.id.summaryLabel) // TextView 대신 ImageView
        private val summaryMessageText: TextView = view.findViewById(R.id.summaryMessageText)

        fun bind(message: Message) {
            // 여기에 이미지 설정 코드 추가 (예: 기본 이미지 또는 URL에서 로드)
            Glide.with(itemView.context)
                .load(R.drawable.summary) // 기본 이미지 또는 메시지의 프로필 이미지 URI
                .into(summaryLabel)

            summaryMessageText.text = message.textContent  // 요약된 메시지 표시
        }
    }

    class InputMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val inputTextView: TextView = view.findViewById(R.id.inputMessageText)
        private val labelView: ImageView = view.findViewById(R.id.inputLabel) // TextView 대신 ImageView

        fun bind(message: Message) {
            // 여기에 이미지 설정 코드 추가 (예: 기본 이미지 또는 URL에서 로드)
            Glide.with(itemView.context)
                .load(R.drawable.input) // 기본 이미지 또는 메시지의 프로필 이미지 URI
                .into(labelView)

            inputTextView.text = message.textContent
        }
    }
}
