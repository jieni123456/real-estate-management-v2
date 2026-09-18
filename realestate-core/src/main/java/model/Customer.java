package model;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String requirements;

    public Customer(String id, String name, String phone, String requirements) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.requirements = requirements;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getRequirements() { return requirements; }
}