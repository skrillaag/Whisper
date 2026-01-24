package com.whisperwire.app.ui.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.whisperwire.app.R;
import com.whisperwire.app.data.chat.ChatMessage;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

// Adapter for rendering decrypted chat messages.
// Each item is either "mine" or "theirs" to support left/right alignment.
public class ChatMessagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_THEIRS = 0;
    private static final int VIEW_TYPE_MINE = 1;

    // Backing list for plaintext ChatMessage objects provided by the ViewModel.
    private final List<ChatMessage> items = new ArrayList<>();
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);

    public void setItems(List<ChatMessage> newItems) {
        // Replace adapter contents with the updated decrypted messages.
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        // Direction classification for layout inflation (left/right bubble).
        ChatMessage msg = items.get(position);
        return msg.isMine() ? VIEW_TYPE_MINE : VIEW_TYPE_THEIRS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_MINE) {
            // My outgoing messages use the "mine" bubble style.
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message_mine, parent, false);
            return new MineViewHolder(view);
        } else {
            // Remote participant messages use the "theirs" bubble style.
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message_theirs, parent, false);
            return new TheirsViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = items.get(position);
        if (holder instanceof MineViewHolder) {
            ((MineViewHolder) holder).bind(msg);
        } else if (holder instanceof TheirsViewHolder) {
            ((TheirsViewHolder) holder).bind(msg);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // Holder for messages sent by the current user.
    static class MineViewHolder extends RecyclerView.ViewHolder {

        private final TextView textMessage;
        private final TextView textTime;

        MineViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.textMessageMine);
            textTime = itemView.findViewById(R.id.textTimeMine);
        }

        void bind(ChatMessage message) {
            // These are already decrypted by ChatRepository before arriving in the adapter.
            textMessage.setText(message.getText());
            textTime.setText(DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(message.getTimestamp())));
        }
    }

    // Holder for messages sent by the contact.
    static class TheirsViewHolder extends RecyclerView.ViewHolder {

        private final TextView textMessage;
        private final TextView textTime;

        TheirsViewHolder(@NonNull View itemView) {
            super(itemView);
            this.textMessage = itemView.findViewById(R.id.textMessageTheirs);
            this.textTime = itemView.findViewById(R.id.textTimeTheirs);
        }

        void bind(ChatMessage message) {
            // Displays remote plaintext after AES-GCM decryption in ViewModel/Repository.
            textMessage.setText(message.getText());
            textTime.setText(DateFormat.getTimeInstance(DateFormat.SHORT)
                    .format(new Date(message.getTimestamp())));
        }
    }
}
