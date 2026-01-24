package com.whisperwire.app.ui.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.whisperwire.app.R;
import com.whisperwire.app.data.contacts.ContactRequest;

import java.util.ArrayList;
import java.util.List;

public class ContactRequestsAdapter extends RecyclerView.Adapter<ContactRequestsAdapter.RequestViewHolder> {

    // Listener interface for accepting/rejecting requests from the UI.
    public interface OnRequestActionListener {
        void onAccept(ContactRequest request);
        void onReject(ContactRequest request);
    }

    // Backing list of pending requests.
    private final List<ContactRequest> items = new ArrayList<>();
    // Callback target provided by the Activity.
    private final OnRequestActionListener listener;

    public ContactRequestsAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    // Replace the current list and refresh the RecyclerView.
    public void setItems(List<ContactRequest> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate UI for a single pending contact request.
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        // Delegate binding logic to the ViewHolder.
        ContactRequest request = items.get(position);
        holder.bind(request);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RequestViewHolder extends RecyclerView.ViewHolder {

        // UI components for displaying the sender and choosing an action.
        private final TextView textFromEmail;
        private final Button buttonAccept;
        private final Button buttonReject;

        RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            textFromEmail = itemView.findViewById(R.id.textFromEmail);
            buttonAccept = itemView.findViewById(R.id.buttonAccept);
            buttonReject = itemView.findViewById(R.id.buttonReject);
        }

        void bind(ContactRequest request) {
            // Show email when present; fallback to UID for malformed entries.
            textFromEmail.setText(request.getFromEmail() != null
                    ? request.getFromEmail()
                    : request.getFromUid());

            // Wire per-item callbacks to the adapter-level listener.
            buttonAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(request);
                }
            });

            buttonReject.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReject(request);
                }
            });
        }
    }
}
