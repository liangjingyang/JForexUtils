package com.jforex.programming.connection;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.aeonbits.owner.ConfigFactory;

import com.dukascopy.api.system.IClient;
import com.github.oxo42.stateless4j.StateMachine;
import com.github.oxo42.stateless4j.StateMachineConfig;
import com.jforex.programming.misc.JFHotSubject;
import com.jforex.programming.settings.PlatformSettings;

import rx.Completable;
import rx.Completable.CompletableSubscriber;
import rx.Observable;
import rx.Subscription;

public class AuthentificationUtil {

    private final IClient client;
    private final JFHotSubject<LoginState> loginStateSubject = new JFHotSubject<>();
    private final Observable<ConnectionState> connectionStateObs;
    private final StateMachineConfig<LoginState, FSMTrigger> fsmConfig = new StateMachineConfig<>();
    private final StateMachine<LoginState, FSMTrigger> fsm = new StateMachine<>(LoginState.LOGGED_OUT, fsmConfig);

    private enum FSMTrigger {
        CONNECTED,
        DISCONNECTED,
        LOGOUT
    }

    private final static PlatformSettings platformSettings = ConfigFactory.create(PlatformSettings.class);

    public AuthentificationUtil(final IClient client,
                                final Observable<ConnectionState> connectionStateObs) {
        this.client = client;
        this.connectionStateObs = connectionStateObs;

        initConnectionStateObs(connectionStateObs);
        configureFSM();
    }

    private final void initConnectionStateObs(final Observable<ConnectionState> connectionStateObs) {
        connectionStateObs.subscribe(connectionState -> {
            if (connectionState == ConnectionState.CONNECTED)
                fsm.fire(FSMTrigger.CONNECTED);
            else
                fsm.fire(FSMTrigger.DISCONNECTED);
        });
    }

    private final void configureFSM() {
        fsmConfig.configure(LoginState.LOGGED_OUT)
                .onEntry(() -> loginStateSubject.onNext(LoginState.LOGGED_OUT))
                .permit(FSMTrigger.CONNECTED, LoginState.LOGGED_IN)
                .ignore(FSMTrigger.DISCONNECTED)
                .ignore(FSMTrigger.LOGOUT);

        fsmConfig.configure(LoginState.LOGGED_IN)
                .onEntry(() -> loginStateSubject.onNext(LoginState.LOGGED_IN))
                .permit(FSMTrigger.LOGOUT, LoginState.LOGGED_OUT)
                .ignore(FSMTrigger.CONNECTED)
                .ignore(FSMTrigger.DISCONNECTED);
    }

    public final Observable<LoginState> loginStateObs() {
        return loginStateSubject.observable();
    }

    public LoginState loginState() {
        return fsm.getState();
    }

    public Completable login(final LoginCredentials loginCredentials) {
        final Optional<Exception> maybeException = connectClient(loginCredentials);
        return completableFromConnectResult(maybeException);
    }

    private Completable completableFromConnectResult(final Optional<Exception> maybeException) {
        return maybeException.isPresent()
                ? Completable.error(maybeException.get())
                : Completable
                        .create(this::loginSubscription)
                        .timeout(platformSettings.logintimeoutseconds(), TimeUnit.SECONDS);
    }

    private final Subscription loginSubscription(final CompletableSubscriber subscriber) {
        return connectionStateObs.take(1)
                .subscribe(connectionState -> processConnectionStateForLogin(connectionState, subscriber));
    }

    private final void processConnectionStateForLogin(final ConnectionState connectionState,
                                                      final CompletableSubscriber subscriber) {
        if (connectionState == ConnectionState.CONNECTED)
            subscriber.onCompleted();
        else
            subscriber.onError(new ConnectException());
    }

    private final Optional<Exception> connectClient(final LoginCredentials loginCredentials) {
        final String jnlpAddress = loginCredentials.jnlpAddress();
        final String username = loginCredentials.username();
        final String password = loginCredentials.password();

        try {
            if (loginCredentials.maybePin().isPresent())
                client.connect(jnlpAddress, username, password, loginCredentials.maybePin().get());
            else
                client.connect(jnlpAddress, username, password);
        } catch (final Exception e) {
            return Optional.of(e);
        }
        return Optional.empty();
    }

    public final void logout() {
        if (isLoggedIn())
            client.disconnect();
        fsm.fire(FSMTrigger.LOGOUT);
    }

    private boolean isLoggedIn() {
        return loginState() == LoginState.LOGGED_IN;
    }
}
