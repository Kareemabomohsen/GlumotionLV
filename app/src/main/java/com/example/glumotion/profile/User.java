package com.example.glumotion.profile;

/**
 * The user (saved in Firebase under Users / uid).
 */
public class User {

    public static final String ROLE_PATIENT = "patient";   // person with diabetes / trainee
    public static final String ROLE_COACH = "coach";       // coach / trainer (the admin of a group)

    private String uid;
    private String role = ROLE_PATIENT;
    private String fullName;
    private String email;
    private String password;
    private int age;
    private String gender = "";
    private String phone = "";
    private String profileImage = ""; // photo as Base64 text (ImageHelper)

    // 1. Empty constructor - needed by Firebase
    public User() {
    }

    // 2. Constructor with the main fields
    public User(String uid, String fullName, String email, String password) {
        this.uid = uid;
        this.fullName = fullName;
        this.email = email;
        this.password = password;
    }

    // 3. Constructor with age and gender (create account screen)
    public User(String uid, String fullName, String email, String password, int age, String gender) {
        this(uid, fullName, email, password);
        this.age = age;
        this.gender = gender;
    }

    // "Karim Raed Mohsen" → "Karim"
    public String firstName() {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }
        return fullName.trim().split(" ")[0];
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
}
