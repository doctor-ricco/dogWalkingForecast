package garagem.ideias.dogwalkingforecast.feature;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import garagem.ideias.dogwalkingforecast.R;

public class UserLocationsAdapter extends RecyclerView.Adapter<UserLocationsAdapter.LocationViewHolder> {
    private List<UserLocationsBottomSheet.LocationItem> locations;
    private final OnLocationClickListener listener;
    private final OnLocationDeleteListener deleteListener;

    public interface OnLocationClickListener {
        void onLocationClick(UserLocationsBottomSheet.LocationItem location);
    }

    public interface OnLocationDeleteListener {
        void onLocationDelete(UserLocationsBottomSheet.LocationItem location);
    }

    public UserLocationsAdapter(List<UserLocationsBottomSheet.LocationItem> locations, 
                              OnLocationClickListener listener,
                              OnLocationDeleteListener deleteListener) {
        this.locations = locations;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_location, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        UserLocationsBottomSheet.LocationItem location = locations.get(position);
        holder.nameText.setText(location.name);
        holder.typeText.setText(location.type);
        holder.itemView.setOnClickListener(v -> listener.onLocationClick(location));
        holder.deleteButton.setOnClickListener(v -> deleteListener.onLocationDelete(location));
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    public void updateLocations(List<UserLocationsBottomSheet.LocationItem> newLocations) {
        this.locations = newLocations;
        notifyDataSetChanged();
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView typeText;
        ImageButton deleteButton;

        LocationViewHolder(View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.locationName);
            typeText = itemView.findViewById(R.id.locationType);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
} 