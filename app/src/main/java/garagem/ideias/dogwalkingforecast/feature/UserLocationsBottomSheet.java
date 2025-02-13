package garagem.ideias.dogwalkingforecast.feature;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import garagem.ideias.dogwalkingforecast.R;

public class UserLocationsBottomSheet extends BottomSheetDialogFragment {
    private RecyclerView recyclerView;
    private TextView emptyView;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private UserLocationsAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_user_locations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Initialize views
        recyclerView = view.findViewById(R.id.locationsRecyclerView);
        emptyView = view.findViewById(R.id.emptyView);

        // Setup header
        TextView titleView = view.findViewById(R.id.titleText);
        titleView.setText("My Locations");

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new UserLocationsAdapter(
            new ArrayList<>(),
            location -> {
                // Handle location click - navigate to map
                if (getActivity() instanceof MapActivity) {
                    ((MapActivity) getActivity()).navigateToLocation(location.latitude, location.longitude);
                    dismiss();
                }
            },
            location -> deleteLocation(location.id, location.name) // Handle delete
        );
        recyclerView.setAdapter(adapter);

        // Load user's locations
        loadUserLocations();
    }

    private void loadUserLocations() {
        String userId = auth.getCurrentUser().getUid();
        db.collection("locations")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                List<LocationItem> locations = new ArrayList<>();
                for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                    LocationItem location = new LocationItem(
                        document.getId(),
                        document.getString("name"),
                        document.getString("type"),
                        document.getDouble("latitude"),
                        document.getDouble("longitude")
                    );
                    locations.add(location);
                }
                
                if (locations.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                    adapter.updateLocations(locations);
                }
            });
    }

    // Add this method to handle location deletion
    private void deleteLocation(String locationId, String locationName) {
        new AlertDialog.Builder(requireContext())
            .setTitle("Delete Location")
            .setMessage("Are you sure you want to delete " + locationName + "?")
            .setPositiveButton("Delete", (dialog, which) -> {
                db.collection("locations")
                    .document(locationId)
                    .delete()
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Location deleted", Toast.LENGTH_SHORT).show();
                        loadUserLocations(); // Refresh the list
                        
                        // Refresh map markers
                        if (getActivity() instanceof MapActivity) {
                            ((MapActivity) getActivity()).refreshLocations();
                        }
                    })
                    .addOnFailureListener(e -> 
                        Toast.makeText(getContext(), "Error deleting location", Toast.LENGTH_SHORT).show()
                    );
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    // Data class for location items
    public static class LocationItem {
        String id;
        String name;
        String type;
        double latitude;
        double longitude;

        LocationItem(String id, String name, String type, double latitude, double longitude) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
} 