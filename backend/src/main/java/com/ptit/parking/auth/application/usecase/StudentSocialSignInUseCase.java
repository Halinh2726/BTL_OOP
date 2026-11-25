package com.ptit.parking.auth.application.usecase;

import com.ptit.parking.auth.application.AuthenticatedUser;
import com.ptit.parking.auth.application.port.out.StudentSocialAccounts;
import com.ptit.parking.auth.domain.Role;
import com.ptit.parking.shared.domain.DomainException;
import com.ptit.parking.shared.domain.ErrorCode;
import com.ptit.parking.student.application.usecase.RegisterStudentUseCase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StudentSocialSignInUseCase {

    private final StudentSocialAccounts accounts;
    private final RegisterStudentUseCase registration;
    private final GetCurrentUserUseCase currentUser;

    public StudentSocialSignInUseCase(StudentSocialAccounts accounts, RegisterStudentUseCase registration,
                                      GetCurrentUserUseCase currentUser) {
        this.accounts = accounts;
        this.registration = registration;
        this.currentUser = currentUser;
    }

    public Optional<AuthenticatedUser> findLinkedUser(String provider, String subject) {
        return accounts.findStudent(provider, subject).map(currentUser::get);
    }

    public List<String> linkedProviders(AuthenticatedUser user) {
        return user != null && user.role() == Role.SINH_VIEN ? accounts.linkedProviders(user.id()) : List.of();
    }

    public void link(AuthenticatedUser user, String provider, String subject) {
        if (user.role() != Role.SINH_VIEN) {
            throw new DomainException(ErrorCode.FORBIDDEN);
        }
        if (accounts.findStudent(provider, subject).filter(id -> id == user.id()).isEmpty()) {
            accounts.insert(user.id(), provider, subject);
        }
    }

    public AuthenticatedUser register(String provider, String subject, String studentCode, String fullName,
                                      String username, String recoveryEmail) {
        // Tài khoản dùng nhà cung cấp có một mật khẩu ngẫu nhiên nội bộ, không gửi ra ngoài.
        var command = new RegisterStudentUseCase.Command(studentCode, fullName, username,
                UUID.randomUUID().toString(), recoveryEmail);
        var student = registration.register(command, saved -> accounts.insert(saved.id(), provider, subject));
        return currentUser.get(student.id());
    }
}
