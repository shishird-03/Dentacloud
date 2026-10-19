package com.dentacloud.app.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.dentacloud.app.data.repo.AuthRepository;

import java.util.function.Consumer;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** Shared by the login and signup screens. */
@HiltViewModel
public class LoginViewModel extends ViewModel {
    public enum State { IDLE, BUSY, DONE }

    private final AuthRepository auth;
    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    @Inject
    public LoginViewModel(AuthRepository auth) {
        this.auth = auth;
    }

    public LiveData<State> state() { return state; }
    public LiveData<String> error() { return error; }

    public void login(String email, String password) {
        if (email.isBlank() || password.isEmpty()) {
            error.setValue("Enter email and password");
            return;
        }
        state.setValue(State.BUSY);
        auth.login(email, password, done());
    }

    public void signup(String clinic, String name, String email, String phone, String password) {
        if (clinic.isBlank() || name.isBlank() || email.isBlank()) {
            error.setValue("Clinic name, your name and email are required");
            return;
        }
        if (password.length() < 8) {
            error.setValue("Password needs at least 8 characters");
            return;
        }
        state.setValue(State.BUSY);
        auth.signup(clinic, name, email, phone, password, done());
    }

    public void demo() {
        state.setValue(State.BUSY);
        auth.loginDemo(done());
    }

    private Consumer<String> done() {
        return err -> {
            error.postValue(err);
            state.postValue(err == null ? State.DONE : State.IDLE);
        };
    }
}
