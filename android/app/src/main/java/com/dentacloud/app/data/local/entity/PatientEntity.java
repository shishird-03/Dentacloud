package com.dentacloud.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(tableName = "patient", indices = {@Index("clinicId"), @Index("phone"), @Index("fullName")})
public class PatientEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String clinicId = "";
    @NonNull public String patientNo = "";
    @NonNull public String fullName = "";
    public String dob;            // ISO yyyy-MM-dd
    public String gender;
    public String phone;
    public String email;
    public String address;
    public String medicalHistory;
    public String allergies;
    public String photoPath;      // local file, never synced in M1
    public int cardColor;         // 0 = default
    public String familyHeadId;
    public long createdAt;
    public long updatedAt;
    public int version;
    public Long deletedAt;

    /** Field-wise equality so DiffUtil can tell when a row's content changed. */
    public boolean sameContent(PatientEntity o) {
        return fullName.equals(o.fullName) && Objects.equals(phone, o.phone)
                && Objects.equals(allergies, o.allergies) && cardColor == o.cardColor
                && Objects.equals(photoPath, o.photoPath) && patientNo.equals(o.patientNo)
                && updatedAt == o.updatedAt;
    }
}
