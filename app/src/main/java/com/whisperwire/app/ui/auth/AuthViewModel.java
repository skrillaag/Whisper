package com.whisperwire.app.ui.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.whisperwire.app.data.AuthRepository;

/**
 * ViewModel for authentication operations.
 * Exposes LiveData for login, registration, error messages, and loading state.
 * Communicates with AuthRepository.
 */
public class AuthViewModel extends ViewModel {

    // Repository where I delegate all Firebase Auth and profile work.
    private final AuthRepository authRepository;

    // True/false flags to inform the UI about login state changes.
    private final MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>();
    private final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>();
    // Carries human-readable error messages to the UI.
    private final MutableLiveData<String> authErrorMessage = new MutableLiveData<>();
    // Indicates when an auth-related call is in progress, for spinners and button disabling.
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public AuthViewModel() {
        this.authRepository = new AuthRepository();
    }

    public LiveData<Boolean> getLoginSuccess() {
        return loginSuccess;
    }

    public LiveData<Boolean> getRegisterSuccess() {
        return registerSuccess;
    }

    public LiveData<String> getAuthErrorMessage() {
        return authErrorMessage;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void login(@NonNull String email, @NonNull String password) {
        loading.setValue(true);
        // Delegate to AuthRepository and update LiveData when Firebase completes.
        authRepository.loginWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    loading.setValue(false);
                    loginSuccess.setValue(task.isSuccessful());
                    if (!task.isSuccessful() && task.getException() != null) {
                        authErrorMessage.setValue(task.getException().getMessage());
                    }
                });
    }

    public void register(@NonNull String email, @NonNull String password) {
        loading.setValue(true);

        authRepository.registerWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // Registration failed before creating any profile data.
                        loading.setValue(false);
                        registerSuccess.setValue(false);
                        if (task.getException() != null) {
                            authErrorMessage.setValue(task.getException().getMessage());
                        } else {
                            authErrorMessage.setValue("Registration failed");
                        }
                        return;
                    }

                    AuthResult authResult = task.getResult();
                    FirebaseUser firebaseUser = authResult != null ? authResult.getUser() : null;
                    if (firebaseUser == null) {
                        // This would indicate an unexpected Firebase state.
                        loading.setValue(false);
                        registerSuccess.setValue(false);
                        authErrorMessage.setValue("User is null after registration");
                        return;
                    }

                    // After account creation I create a Firestore profile with an identity key.
                    authRepository.createUserProfileWithIdentityKey(firebaseUser)
                            .addOnCompleteListener(profileTask -> {
                                loading.setValue(false);
                                if (profileTask.isSuccessful()) {
                                    registerSuccess.setValue(true);
                                } else {
                                    registerSuccess.setValue(false);
                                    if (profileTask.getException() != null) {
                                        authErrorMessage.setValue(profileTask.getException().getMessage());
                                    } else {
                                        authErrorMessage.setValue("Profile creation failed");
                                    }
                                }
                            });
                });
    }

    public boolean isLoggedIn() {
        // Simple helper for activities to decide where to route the user.
        return authRepository.getCurrentUser() != null;
    }

    public void logout() {
        // Clears Firebase auth session; UI is responsible for navigation.
        authRepository.logout();
    }
}
