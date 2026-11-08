package com.example.glumotion.chat;

import com.example.glumotion.R;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * Adapter of the chat list: bot message on the left, user message on the right.
 * Bot messages can have a link button (YouTube) or a 🗑 button (delete the foods the bot added),
 * user messages can have a photo.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatHolder> {

    private final ArrayList<ChatMessage> messages;
    private OnDeleteFoodsListener deleteListener;

    // The chat screen deletes the foods when the 🗑 button is tapped
    public interface OnDeleteFoodsListener {
        void onDeleteFoods(ChatMessage message);
    }

    // Constructor
    public ChatAdapter(ArrayList<ChatMessage> messages) {
        this.messages = messages;
    }

    // Sets who handles the 🗑 button
    public void setOnDeleteFoodsListener(OnDeleteFoodsListener listener) {
        this.deleteListener = listener;
    }

    // Creates one message row
    @NonNull
    @Override
    public ChatHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ChatHolder(view);
    }

    // Shows only the bubble of the right side (+ the photo / link if the message has one)
    @Override
    public void onBindViewHolder(@NonNull ChatHolder holder, int position) {
        final ChatMessage message = messages.get(position);
        if (message.isFromUser()) {
            holder.tvUser.setVisibility(View.VISIBLE);
            holder.tvBot.setVisibility(View.GONE);
            holder.tvLink.setVisibility(View.GONE);
            holder.tvUser.setText(message.getText());
            if (message.getPhoto() != null) {
                holder.ivPhoto.setVisibility(View.VISIBLE);
                holder.ivPhoto.setImageBitmap(message.getPhoto());
            } else {
                holder.ivPhoto.setVisibility(View.GONE);
            }
        } else {
            holder.tvBot.setVisibility(View.VISIBLE);
            holder.tvUser.setVisibility(View.GONE);
            holder.ivPhoto.setVisibility(View.GONE);
            holder.tvBot.setText(message.getText());
            if (message.getLink() != null) {
                holder.tvLink.setVisibility(View.VISIBLE);
                holder.tvLink.setText(message.getLinkLabel());
                holder.tvLink.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Implicit Intent: the phone opens the link in YouTube / the browser
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getLink()));
                        v.getContext().startActivity(intent);
                    }
                });
            } else if (message.getAddedFoods() != null && !message.getAddedFoods().isEmpty()) {
                // Foods the bot added → 🗑 button next to them
                holder.tvLink.setVisibility(View.VISIBLE);
                holder.tvLink.setText("🗑 Delete");
                holder.tvLink.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (deleteListener != null) {
                            deleteListener.onDeleteFoods(message);
                        }
                    }
                });
            } else {
                holder.tvLink.setVisibility(View.GONE);
            }
        }
    }

    // Number of messages
    @Override
    public int getItemCount() {
        return messages.size();
    }

    // Views of one row
    public static class ChatHolder extends RecyclerView.ViewHolder {
        TextView tvBot, tvUser, tvLink;
        ImageView ivPhoto;

        // Finds the views of the row once
        public ChatHolder(@NonNull View itemView) {
            super(itemView);
            tvBot = itemView.findViewById(R.id.tvBotMessage);
            tvUser = itemView.findViewById(R.id.tvUserMessage);
            tvLink = itemView.findViewById(R.id.tvBotLink);
            ivPhoto = itemView.findViewById(R.id.ivUserPhoto);
        }
    }
}
