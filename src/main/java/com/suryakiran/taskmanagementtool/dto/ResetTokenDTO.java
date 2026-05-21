package com.suryakiran.taskmanagementtool.dto;

public class ResetTokenDTO {

    private String email;
    private String otp;
    private String message;

    public ResetTokenDTO(String email, String otp, String message) {
        this.email = email;
        this.otp = otp;
        this.message = message;
    }

    public String getEmail() { return email; }
    public String getOtp() { return otp; }
    public String getMessage() { return message; }
}
