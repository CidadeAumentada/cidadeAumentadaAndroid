package br.org.inovacidades.www.mapboxapp;

import java.io.Serializable;

/**
 * Created by mario.rscastro on 21/10/2015.
 */
public class MyMarker implements Serializable {

    private String Title;
    private String Description;
    private double Latitude;
    private double Longitude;
    private Boolean Visible;

    public MyMarker(String title, String description, double latitude, double longitude) {
        Title = title;
        Description = description;
        Latitude = latitude;
        Longitude = longitude;
        Visible = false;
    }

    public String getTitle() {
        return Title;
    }

    public void setTitle(String title) {
        Title = title;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String description) {
        Description = description;
    }

    public double getLatitude() {
        return Latitude;
    }

    public void setLatitude(double latitude) {
        Latitude = latitude;
    }

    public double getLongitude() {
        return Longitude;
    }

    public void setLongitude(double longitude) {
        Longitude = longitude;
    }

    public Boolean isVisible() {
        return Visible;
    }

    public void setVisible(Boolean visible) {
        Visible = visible;
    }
}
