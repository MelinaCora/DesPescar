package com.despescar.identityservice.exception;

public class AccountTemporarilyLockedException extends RuntimeException {

    private final long remainingSeconds;

    public AccountTemporarilyLockedException(long remainingSeconds) {
        super("Cuenta temporalmente bloqueada. Intente nuevamente en " + remainingSeconds + " segundos.");
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
