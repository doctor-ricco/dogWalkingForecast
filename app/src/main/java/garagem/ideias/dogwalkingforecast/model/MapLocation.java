package garagem.ideias.dogwalkingforecast.model;

public class MapLocation {
    private String id;
    private double latitude;
    private double longitude;
    private String name;
    private String type;  // e.g., "vet", "park", "pet_store"
    private long timestamp;

    // Required empty constructor for Firestore
    public MapLocation() {}

    public MapLocation(String id, double latitude, double longitude, String name, String type) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
        this.name = name;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
} 