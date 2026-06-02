package com.salmak;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the JSON body of a POST /register request.
 *
 * Accepts two coordinate formats:
 *   - Flat:   {"lat": 33.89, "lng": 35.50, ...}           (sent by the mobile app)
 *   - Nested: {"coordinates": {"lat": 33.89, "lng": 35.50}, ...}  (legacy)
 *
 * Flat fields take precedence when both are present.
 */
public class RegisterRequest {

    private String phoneNumber;
    private String name;
    private Coordinates coordinates;
    private String emergencyContact;
    private Integer peopleInHouse;
    private String idPhoto; // optional

    // Flat lat/lng — accepted directly at the top level
    @JsonProperty("lat")
    private Double flatLat;

    @JsonProperty("lng")
    private Double flatLng;

    public static class Coordinates {
        private Double lat;
        private Double lng;

        public Double getLat() { return lat; }
        public void setLat(Double lat) { this.lat = lat; }

        public Double getLng() { return lng; }
        public void setLng(Double lng) { this.lng = lng; }
    }

    /**
     * Returns a Coordinates object regardless of whether lat/lng arrived
     * flat or nested. Flat fields take precedence.
     */
    public Coordinates getCoordinates() {
        if (flatLat != null && flatLng != null) {
            Coordinates c = new Coordinates();
            c.setLat(flatLat);
            c.setLng(flatLng);
            return c;
        }
        return coordinates;
    }

    public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmergencyContact() { return emergencyContact; }
    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }

    public Integer getPeopleInHouse() { return peopleInHouse; }
    public void setPeopleInHouse(Integer peopleInHouse) { this.peopleInHouse = peopleInHouse; }

    public String getIdPhoto() { return idPhoto; }
    public void setIdPhoto(String idPhoto) { this.idPhoto = idPhoto; }
}
