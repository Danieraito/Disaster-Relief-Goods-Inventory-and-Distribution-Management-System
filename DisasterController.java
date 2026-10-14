public class DisasterController {
    public Disaster createDisaster(String disasterId, String name, String type,
            String location, String date, String status) {
        return new Disaster(disasterId, name, type, location, date, status);
    }

    public void updateDisasterStatus(Disaster disaster, String status) {
        if (disaster == null) {
            throw new IllegalArgumentException("Disaster cannot be null.");
        }
        disaster.setStatus(status);
    }
}
