package com.whisperwire.app.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.whisperwire.app.R;
import com.whisperwire.app.data.chat.ChatSummary;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * RecyclerView adapter displaying the user's list of recent chats.
 *
 * Each row shows:
 *   - Contact email
 *   - Last message preview (decrypted by repository before reaching UI)
 *   - Timestamp of last activity
 *   - Unread indicator (●)
 *
 * Click events are forwarded to the Activity/Fragment via Listener.
 */
public class RecentChatsAdapter extends RecyclerView.Adapter<RecentChatsAdapter.Holder> {

    /**
     * Listener interface for handling row clicks.
     */
    public interface Listener {
        void onClick(ChatSummary chat);
    }

    // Backing list for all recent chat items
    private final List<ChatSummary> items = new ArrayList<>();

    // Host context (MainActivity) receives click callbacks
    private final Listener listener;

    // Standard system date-time formatter
    private final DateFormat df = DateFormat.getDateTimeInstance();

    public RecentChatsAdapter(Listener listener) {
        this.listener = listener;
    }

    /**
     * Replaces entire dataset. Simple approach used because lists are small.
     */
    public void setItems(List<ChatSummary> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();  // No diffing — adequate for small lists
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate row layout
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_chat, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        // Bind chat summary to row
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder for a single recent chat row.
     */
    class Holder extends RecyclerView.ViewHolder {

        TextView textEmail;    // contact's email
        TextView textPreview;  // decrypted message preview
        TextView textTime;     // timestamp of last message
        TextView textUnread;   // "●" indicator for unread messages

        Holder(@NonNull View itemView) {
            super(itemView);
            textEmail = itemView.findViewById(R.id.textRecentChatEmail);
            textPreview = itemView.findViewById(R.id.textRecentChatPreview);
            textTime = itemView.findViewById(R.id.textRecentChatTime);
            textUnread = itemView.findViewById(R.id.textRecentChatUnread);
        }

        /**
         * Populates the row views with data from ChatSummary.
         */
        void bind(ChatSummary chat) {
            // Display contact's email (primary identifier in UI)
            textEmail.setText(chat.getContactEmail());

            // Format last activity timestamp
            textTime.setText(df.format(new Date(chat.getLastTimestamp())));

            // Short decrypted message preview
            textPreview.setText(chat.getLastMessagePreview());

            // Show unread dot only when needed
            if (chat.hasUnread()) {
                textUnread.setVisibility(View.VISIBLE);
            } else {
                textUnread.setVisibility(View.GONE);
            }

            // Clicking row opens ChatActivity
            itemView.setOnClickListener(v -> listener.onClick(chat));
        }
    }
}
