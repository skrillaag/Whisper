package com.whisperwire.app.ui.contacts;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.whisperwire.app.R;
import com.whisperwire.app.data.contacts.ContactRequest;

public class ContactRequestsActivity extends AppCompatActivity {

    // ViewModel coordinating incoming contact requests and actions.
    private ContactsViewModel contactsViewModel;
    // List of pending contact requests.
    private RecyclerView recyclerContactRequests;
    private ContactRequestsAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_requests);

        contactsViewModel = new ViewModelProvider(this).get(ContactsViewModel.class);

        recyclerContactRequests = findViewById(R.id.recyclerContactRequests);
        recyclerContactRequests.setLayoutManager(new LinearLayoutManager(this));

        // Adapter exposes callbacks for accept/reject which are delegated to the ViewModel.
        adapter = new ContactRequestsAdapter(new ContactRequestsAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(ContactRequest request) {
                contactsViewModel.acceptRequest(request);
            }

            @Override
            public void onReject(ContactRequest request) {
                contactsViewModel.rejectRequest(request);
            }
        });

        recyclerContactRequests.setAdapter(adapter);

        observeViewModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload incoming requests whenever the screen becomes visible.
        contactsViewModel.loadIncomingRequests();
    }

    private void observeViewModel() {
        // Update the list whenever the pending requests LiveData changes.
        contactsViewModel.getIncomingRequests().observe(this, requests -> {
            adapter.setItems(requests);
        });

        // Show errors from repository or network issues.
        contactsViewModel.getRequestErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });

        // Action-level feedback (e.g., “Request accepted”, “Request rejected”).
        contactsViewModel.getActionMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
