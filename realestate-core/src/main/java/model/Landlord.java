package model;

public class Landlord {
    private String id;
    private String name;
    private String contact;

    public Landlord(String id, String name, String contact) {
        this.id = id;
        this.name = name;
        this.contact = contact;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getContact() { return contact; }
}