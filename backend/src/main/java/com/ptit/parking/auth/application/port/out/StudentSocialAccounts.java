package com.ptit.parking.auth.application.port.out;

import java.util.List;
import java.util.Optional;

public interface StudentSocialAccounts {

    Optional<Long> findStudent(String provider, String subject);

    List<String> linkedProviders(long studentId);

    void insert(long studentId, String provider, String subject);
}
