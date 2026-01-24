package com.whisperwire.app.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.whisperwire.app.R;
import com.whisperwire.app.data.contacts.Contact;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {

    // Listener used by the Activity/Fragment to navigate to a chat.
    public interface OnContactClickListener {
        void onContactClick(Contact contact);
    }

    // Internal list of contacts belonging to the user.
    private final List<Contact> items = new ArrayList<>();
    private final OnContactClickListener listener;

    public ContactsAdapter(OnContactClickListener listener) {
        this.listener = listener;
    }

    /** Replaces the list of contacts and refreshes the UI. */
    public void setItems(List<Contact> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate a single contact row.
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ContactViewHolder extends RecyclerView.ViewHolder {

        private final TextView textContactEmail;

        ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            textContactEmail = itemView.findViewById(R.id.textContactEmail);
        }

        /** Displays a single contact's email and forwards click events. */
        void bind(Contact contact) {
            textContactEmail.setText(contact.getContactEmail());
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onContactClick(contact);
                }
            });
        }
    }
}
