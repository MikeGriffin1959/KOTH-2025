package model;

public class User {
	private int idUser;
    private String firstName;
    private String lastName;
    private String username;
    private String email;
    private String cellNumber;
    private String password;
    private int initialPicks;
    private int remainingPicksLive;
    private int remainingPicksWeekly;
    private boolean admin;
    private boolean commish;
    private String resetToken;
    private String rememberMeToken;
    private int picksSeason;
    private boolean picksPaid;
    
    // Default constructor
    public User() {}

    
    // Getters and Setters

    public int getIdUser() {
        return idUser;
    }

    public void setIdUser(int idUser) {
        this.idUser = idUser;
    }
    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCellNumber() {
        return cellNumber;
    }

    public void setCellNumber(String cellNumber) {
        this.cellNumber = cellNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getInitialPicks() {
        return initialPicks;
    }

    public void setInitialPicks(int initialPicks) {
        this.initialPicks = initialPicks;
    }

    public int getRemainingPicksLive() {
        return remainingPicksLive;
    }

    public void setRemainingPicksLive(int remainingPicksLive) {
        this.remainingPicksLive = remainingPicksLive;
    }
    
    public int getRemainingPicksWeekly() {
        return remainingPicksWeekly;
    }

    public void setRemainingPicksWeekly(int remainingPicksWeekly) {
        this.remainingPicksWeekly = remainingPicksWeekly;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isCommish() {
        return commish;
    }

    public void setCommish(boolean commish) {
        this.commish = commish;
    }
    
    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
    
    public String getRememberMetToken() {
        return rememberMeToken;
    }

    public void setRememberMeToken(String rememberMeToken) {
        this.rememberMeToken = rememberMeToken;
    }
    
    public int getPicksSeason() {
        return picksSeason;
    }

    public void setPicksSeason(int picksSeason) {
        this.picksSeason = picksSeason;
    }

    public boolean getPicksPaid() {
        return picksPaid;
    }

    public void setPicksPaid(boolean picksPaid) {
        this.picksPaid = picksPaid;
    }
}
