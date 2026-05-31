package com.salmak;

/**
 * Represents the JSON body of a POST /register request.
 */
public class RegisterRequest {

    private String phoneNumber;
    private String name;
    private Coordinates coordinates;
    private String emergencyContact;
    private Integer peopleInHouse;
    private String idPhoto; // optional

    public static class Coordinates {
        private Double lat;
        private Double lng;

        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }

        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Coordinates getCoordinates() { return coordinates; }
    public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public Integer getPeopleInHouse() { return peopleInHouse; }
    public void setPeopleInHouse(Integer peopleInHouse) { this.peopleInHouse = peopleInHouse; }

    public String getIdPhoto() { return idPhoto; }
    public void setIdPhoto(String idPhoto) { this.idPhoto = idPhoto; }
}
