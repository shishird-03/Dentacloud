package com.dentacloud.patient;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PatientRepository extends JpaRepository<Patient, UUID> {
    List<Patient> findByDeletedAtIsNullOrderByIdDesc(Limit limit);

    List<Patient> findByDeletedAtIsNullAndIdLessThanOrderByIdDesc(UUID cursor, Limit limit);

    List<Patient> findByFullNameContainingIgnoreCaseAndDeletedAtIsNullOrderByFullName(String name, Limit limit);

    List<Patient> findByPhoneBlindIdxAndDeletedAtIsNull(byte[] index);

    Optional<Patient> findByPatientNoAndDeletedAtIsNull(String patientNo);
}
