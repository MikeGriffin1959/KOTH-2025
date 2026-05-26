package model;

import java.io.Serializable;

/** Static team metadata seeded into KOTH.Teams: division/conference/stadium geo/dome/tz. */
public class TeamContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private int    teamId;          // = KOTH.Teams.apiTeamID
    private String shortName;       // apiTeamShortName (abbrev)
    private String division;        // e.g. AFC_NORTH
    private String conference;      // AFC | NFC
    private Double lat;
    private Double lng;
    private boolean dome;
    private String tz;              // IANA

    public TeamContext() {}

    public int getTeamId() { return teamId; }
    public void setTeamId(int v) { this.teamId = v; }

    public String getShortName() { return shortName; }
    public void setShortName(String v) { this.shortName = v; }

    public String getDivision() { return division; }
    public void setDivision(String v) { this.division = v; }

    public String getConference() { return conference; }
    public void setConference(String v) { this.conference = v; }

    public Double getLat() { return lat; }
    public void setLat(Double v) { this.lat = v; }

    public Double getLng() { return lng; }
    public void setLng(Double v) { this.lng = v; }

    public boolean isDome() { return dome; }
    public void setDome(boolean v) { this.dome = v; }

    public String getTz() { return tz; }
    public void setTz(String v) { this.tz = v; }
}