package in.projecteka.consentmanager.user;

import in.projecteka.consentmanager.NullableConverter;
import in.projecteka.consentmanager.clients.ClientError;
import in.projecteka.consentmanager.clients.OtpServiceClient;
import in.projecteka.consentmanager.clients.model.OtpRequest;
import in.projecteka.consentmanager.clients.model.Session;
import in.projecteka.consentmanager.clients.properties.OtpServiceProperties;
import in.projecteka.consentmanager.common.cache.CacheAdapter;
import in.projecteka.consentmanager.user.exception.InvalidPasswordException;
import in.projecteka.consentmanager.user.exception.InvalidUserNameException;
import in.projecteka.consentmanager.user.model.LogoutRequest;
import in.projecteka.consentmanager.user.model.OtpPermitRequest;
import in.projecteka.consentmanager.user.model.OtpVerificationRequest;
import in.projecteka.consentmanager.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static in.projecteka.consentmanager.common.Constants.BLACKLIST;
import static in.projecteka.consentmanager.common.Constants.BLACKLIST_FORMAT;
import static in.projecteka.consentmanager.user.TestBuilders.session;
import static in.projecteka.consentmanager.user.TestBuilders.sessionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class SessionServiceTest {

    @Captor
    private ArgumentCaptor<OtpRequest> otpRequestArgumentCaptor;

    @Mock
    TokenService tokenService;

    @Mock
    CacheAdapter<String, String> blacklistedTokens;

    @Mock
    LockedUserService lockedUserService;

    @Mock
    private OtpServiceClient otpServiceClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpServiceProperties otpServiceProperties;

    @Mock
    CacheAdapter<String, String> unverifiedSessions;

    @BeforeEach
    void init() {
        initMocks(this);
    }

    @Test
    void returnSession() {
        var sessionRequest = sessionRequest().build();
        var expectedSession = session().build();
        when(tokenService.tokenForUser(sessionRequest.getUsername(), sessionRequest.getPassword()))
                .thenReturn(Mono.just(expectedSession));

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        var sessionPublisher = sessionService.forNew(sessionRequest);

        StepVerifier.create(sessionPublisher)
                .assertNext(session -> assertThat(session).isEqualTo(expectedSession))
                .verifyComplete();
    }

    @ParameterizedTest
    @CsvSource({
            ",",
            "empty",
            "null"
    })
    void returnUnAuthorizedErrorWhenUsernameIsEmpty(@ConvertWith(NullableConverter.class) String value) {
        var sessionRequest = sessionRequest().username(value).build();
        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        var sessionPublisher = sessionService.forNew(sessionRequest);

        StepVerifier.create(sessionPublisher)
                .expectErrorSatisfies(throwable ->
                        assertThat(((ClientError) throwable).getHttpStatus() == UNAUTHORIZED))
                .verify();
    }

    @ParameterizedTest
    @CsvSource({
            ",",
            "empty",
            "null"
    })
    void returnUnAuthorizedErrorWhenPasswordIsEmpty(
            @ConvertWith(NullableConverter.class) String value) {
        var sessionRequest = sessionRequest().password(value).build();
        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);


        var sessionPublisher = sessionService.forNew(sessionRequest);

        StepVerifier.create(sessionPublisher)
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == UNAUTHORIZED))
                .verify();
    }

    @Test
    void returnUnAuthorizedWhenAnyTokenServiceThrowsInvalidPasswordException() {
        var sessionRequest = sessionRequest().build();
        var patientId = sessionRequest.getUsername();
        var password = sessionRequest.getPassword();

        when(tokenService.tokenForUser(patientId, password)).thenReturn(Mono.error(new InvalidPasswordException()));
        when(lockedUserService.userFor(patientId)).thenReturn(Mono.empty());
        when(lockedUserService.createUser(patientId)).thenReturn(Mono.empty());
        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        var sessionPublisher = sessionService.forNew(sessionRequest);

        StepVerifier.create(sessionPublisher)
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == UNAUTHORIZED))
                .verify();
        verify(tokenService, times(1)).tokenForUser(patientId, password);
        verify(lockedUserService, times(1)).userFor(patientId);
        verify(lockedUserService, times(1)).createUser(patientId);
    }

    @Test
    void returnUnAuthorizedWhenAnyTokenServiceThrowsInvalidUserNameException() {
        var sessionRequest = sessionRequest().build();
        var patientId = sessionRequest.getUsername();
        var password = sessionRequest.getPassword();

        when(tokenService.tokenForUser(patientId, password)).thenReturn(Mono.error(new InvalidUserNameException()));
        when(lockedUserService.userFor(patientId)).thenReturn(Mono.empty());
        when(lockedUserService.createUser(patientId)).thenReturn(Mono.empty());
        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        var sessionPublisher = sessionService.forNew(sessionRequest);

        StepVerifier.create(sessionPublisher)
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == UNAUTHORIZED))
                .verify();
        verify(tokenService, times(1)).tokenForUser(patientId, password);
        verify(lockedUserService, times(1)).userFor(patientId);
        verify(lockedUserService, times(1)).createUser(patientId);
    }

    @Test
    public void shouldBlackListToken() {
        String testAccessToken = "accessToken";
        String refreshToken = "refreshToken";
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken);
        when(blacklistedTokens.put(String.format(BLACKLIST_FORMAT, BLACKLIST, testAccessToken), "")).
                thenReturn(Mono.empty());
        when(tokenService.revoke(refreshToken)).thenReturn(Mono.empty());

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        Mono<Void> logout = sessionService.logout(testAccessToken, logoutRequest);

        StepVerifier.create(logout).verifyComplete();
        verify(blacklistedTokens).put(String.format(BLACKLIST_FORMAT, BLACKLIST, testAccessToken), "");
        verify(tokenService).revoke(refreshToken);
    }

    @Test
    public void shouldMakeOtpRequestAndReturnSessionId() {
        String username = "foobar@ncg";
        String testPhone = "9876543210";

        when(userRepository.userWith(username)).thenReturn(Mono.just(User.builder().phone(testPhone).build()));
        when(otpServiceClient.send(otpRequestArgumentCaptor.capture())).thenReturn(Mono.empty());
        when(otpServiceProperties.getExpiryInMinutes()).thenReturn(5);
        when(unverifiedSessions.put(any(String.class), eq(username))).thenReturn(Mono.empty());

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);
        StepVerifier.create(sessionService.sendOtp(new OtpVerificationRequest(username))).
                assertNext(response -> {
                    assertThat(response.getSessionId()).isNotEmpty();
                    assertThat(response.getMeta().getCommunicationExpiry()).isEqualTo("300");
                    assertThat(response.getMeta().getCommunicationHint()).endsWith("3210");
                }).verifyComplete();

        verify(userRepository).userWith(username);
        verify(unverifiedSessions).put(any(String.class), eq(username));
        assertThat(otpRequestArgumentCaptor.getValue().getCommunication().getValue().equals(testPhone)).isTrue();
    }

    @Test
    public void shouldThrowExceptionWhenNoUserFound() {
        String username = "foobar@ncg";

        when(userRepository.userWith(username)).thenReturn(Mono.empty());
        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);
        StepVerifier.create(sessionService.sendOtp(new OtpVerificationRequest(username)))
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == NOT_FOUND))
                .verify();
    }

    @Test
    public void shouldThrowExceptionWhenOtpCallFails() {
        String username = "foobar@ncg";
        String testPhone = "9876543210";

        when(userRepository.userWith(username)).thenReturn(Mono.just(User.builder().phone(testPhone).build()));
        when(otpServiceClient.send(otpRequestArgumentCaptor.capture())).thenReturn(Mono.error(ClientError.unknownErrorOccurred()));

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);
        StepVerifier.create(sessionService.sendOtp(new OtpVerificationRequest(username)))
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == INTERNAL_SERVER_ERROR))
                .verify();

        verify(userRepository).userWith(username);
        assertThat(otpRequestArgumentCaptor.getValue().getCommunication().getValue().equals(testPhone)).isTrue();
    }

    @Test
    public void shouldThrowErrorForInvalidSessionId() {
        String testSession = "testSession";
        OtpPermitRequest otpPermitRequest = new OtpPermitRequest(null, testSession, null);
        when(unverifiedSessions.get(testSession)).thenReturn(Mono.empty());

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        StepVerifier.create(sessionService.validateOtp(otpPermitRequest))
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == BAD_REQUEST))
                .verify();
        verify(unverifiedSessions).get(testSession);
    }

    @Test
    public void shouldThrowErrorForDifferentUsersSessionId() {
        String testSession = "testSession";
        String testUser = "testUser";
        String differentUser = "differentUser";
        OtpPermitRequest otpPermitRequest = new OtpPermitRequest(testUser, testSession, null);
        when(unverifiedSessions.get(testSession)).thenReturn(Mono.just(differentUser));

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        StepVerifier.create(sessionService.validateOtp(otpPermitRequest))
                .expectErrorSatisfies(throwable -> assertThat(((ClientError) throwable).getHttpStatus() == BAD_REQUEST))
                .verify();
        verify(unverifiedSessions).get(testSession);
    }

    @Test
    public void shouldValidateOtp() {
        String testSession = "testSession";
        String testOtp = "666666";
        String username = "testUser";
        OtpPermitRequest otpPermitRequest = new OtpPermitRequest(username, testSession, testOtp);
        when(unverifiedSessions.get(testSession)).thenReturn(Mono.just(username));
        Session expectedSession = in.projecteka.consentmanager.clients.TestBuilders.session().build();
        when(tokenService.tokenForOtpUser(eq(username), eq(testSession), eq(testOtp))).thenReturn(Mono.just(expectedSession));

        SessionService sessionService = new SessionService(tokenService, blacklistedTokens, unverifiedSessions, lockedUserService, userRepository, otpServiceClient, otpServiceProperties);

        StepVerifier.create(sessionService.validateOtp(otpPermitRequest))
                .assertNext(session -> assertThat(session).isEqualTo(expectedSession))
                .verifyComplete();
        verify(unverifiedSessions).get(testSession);
        verify(tokenService).tokenForOtpUser(eq(username), eq(testSession), eq(testOtp));
    }
}