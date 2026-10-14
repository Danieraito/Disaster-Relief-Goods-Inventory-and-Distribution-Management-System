public class Disaster {
    private String disasterId;
    private String name;
    private String type;
    private String location;
    private String date;
    private String status;

    public Disaster(String disasterId, String name, String type, String location, String date, String status) {
        this.disasterId = disasterId;
        this.name = name;
        this.type = type;
        this.location = location;
        this.date = date;
        this.status = status;
    }

    public String getDisasterId() {
        return disasterId;
    }

    public void setDisasterId(String disasterId) {
        this.disasterId = disasterId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
