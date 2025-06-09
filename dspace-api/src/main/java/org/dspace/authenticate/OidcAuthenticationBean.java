/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.authenticate;

import static java.lang.String.format;
import static java.lang.String.join;
import static java.net.URLEncoder.encode;
import static org.apache.commons.lang.BooleanUtils.toBoolean;
import static org.apache.commons.lang3.StringUtils.isAnyBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.dspace.authenticate.oidc.OidcClient;
import org.dspace.authenticate.oidc.model.OidcTokenResponseDTO;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.service.EPersonService;
import org.dspace.services.ConfigurationService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * OpenID Connect Authentication for DSpace.
 *
 * This implementation doesn't allow/needs to register user, which may be holder
 * by the openID authentication server.
 *
 * @link   https://openid.net/developers/specs/
 *
 * @author Luca Giamminonni (luca.giamminonni at 4science.it)
 */
public class OidcAuthenticationBean implements AuthenticationMethod {

    public static final String OIDC_AUTH_ATTRIBUTE = "oidc";

    private final static String LOGIN_PAGE_URL_FORMAT = "%s?client_id=%s&response_type=code&scope=%s&redirect_uri=%s";

    private static final Logger LOGGER = LoggerFactory.getLogger(OidcAuthenticationBean.class);

    private static final String OIDC_AUTHENTICATED = "oidc.authenticated";

    protected static final int NAME_MAX_SIZE = 64;

    /**
     * User information recovery modes. With some providers, the userinfo call can be
     * avoided by retrieving the information through the ID Token.
     */
    protected enum UserInfoRecoveryMode {
        // Default: retrieve information through userinfo call to the OpenID Connect Provider
        USERINFO_CALL(0),
        // Retrieve information through claims, detected through ID Token
        MERGE_CLAIMS(1),
        // Performs both methods, giving priority to the claims
        BOTH(2);

        private final int value;

        UserInfoRecoveryMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public boolean requiresUserInfoCall() {
            return this == USERINFO_CALL || this == BOTH;
        }

        public boolean requiresClaimsMerge() {
            return this == MERGE_CLAIMS || this == BOTH;
        }

    }


    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private OidcClient oidcClient;

    @Autowired
    private EPersonService ePersonService;

    @Override
    public boolean allowSetPassword(Context context, HttpServletRequest request, String username) throws SQLException {
        return false;
    }

    @Override
    public boolean isImplicit() {
        return false;
    }

    @Override
    public boolean canSelfRegister(Context context, HttpServletRequest request, String username) throws SQLException {
        return canSelfRegister();
    }

    @Override
    public void initEPerson(Context context, HttpServletRequest request, EPerson eperson) throws SQLException {
    }

    @Override
    public List<Group> getSpecialGroups(Context context, HttpServletRequest request) throws SQLException {
        return List.of();
    }

    @Override
    public String getName() {
        return OIDC_AUTH_ATTRIBUTE;
    }

    @Override
    public int authenticate(Context context, String username, String password, String realm, HttpServletRequest request)
        throws SQLException {

        if (request == null) {
            LOGGER.warn("Unable to authenticate using OIDC because the request object is null.");
            return BAD_ARGS;
        }

        if (request.getAttribute(OIDC_AUTH_ATTRIBUTE) == null) {
            return NO_SUCH_USER;
        }

        String code = (String) request.getParameter("code");
        if (StringUtils.isEmpty(code)) {
            LOGGER.warn("The incoming request has not code parameter");
            return NO_SUCH_USER;
        }

        return authenticateWithOidc(context, code, request);
    }

    private int authenticateWithOidc(Context context, String code, HttpServletRequest request) throws SQLException {

        OidcTokenResponseDTO accessToken = getOidcAccessToken(code);
        if (accessToken == null) {
            LOGGER.warn("No access token retrieved by code");
            return NO_SUCH_USER;
        }

        Map<String, Object> userInfo = getUserDetails(accessToken);

        EPerson ePerson = null;

        // 1 - check by netId
        String netId = getAttributeAsString(userInfo, getNetIdAttribute());
        if (StringUtils.isNotBlank(netId)) {
            ePerson = ePersonService.findByNetid(context, netId);
            if (ePerson != null) {
                LOGGER.debug("Identified EPerson based upon Oidc unique id: '{}'", netId);
                if (ePerson.canLogIn()) {
                    request.setAttribute(OIDC_AUTHENTICATED, true);
                    try {
                        updateEPerson(context, ePerson, userInfo);
                    } catch (SQLException | AuthorizeException ex) {
                        LOGGER.error("An error occurs updating the EPerson", ex);
                        return NO_SUCH_USER;
                    }
                    return logInEPerson(context, ePerson);
                } else {
                    LOGGER.warn("EPerson with netid '{}' is not allowed to log in", netId);
                    return BAD_ARGS;
                }
            } else {
                LOGGER.info("Unable to identify EPerson based upon Oidc unique id: '{}'", netId);
            }
        }

        // 2 - check by email (if self registration is enabled)
        String email = getAttributeAsString(userInfo, getEmailAttribute());
        if (isBlank(email)) {
            LOGGER.warn("No email found in the user info attributes");
            return NO_SUCH_USER;
        }

        ePerson = ePersonService.findByEmail(context, email);
        if (ePerson != null) {
            LOGGER.info("Identified EPerson based upon Shibboleth email {}", email);
            if (ePerson.canLogIn()) {
                request.setAttribute(OIDC_AUTHENTICATED, true);
                try {
                    updateEPerson(context, ePerson, userInfo);
                } catch (SQLException | AuthorizeException ex) {
                    LOGGER.error("An error occurs updating the EPerson", ex);
                    return NO_SUCH_USER;
                }
                return logInEPerson(context, ePerson);
            } else {
                LOGGER.warn("EPerson with email '{}' is not allowed to log in", email);
                return BAD_ARGS;
            }
        } else {
            LOGGER.info("Unable to identify EPerson based upon Oidc email {}", email);
        }

        // if self registration is disabled, warn about this failure to find a matching eperson
        if (!canSelfRegister()) {
            LOGGER.warn("Self registration is currently disabled for OIDC, " +
                    "and no ePerson could be found for email: {}", email);
        }

        return canSelfRegister() ? registerNewEPerson(context, userInfo, email) : NO_SUCH_USER;

    }

    /**
     * Retrieves the user details from the OIDC token response.
     *
     * @param accessToken OIDC token response
     * @return Map containing user details, empty if no user details could be retrieved
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getUserDetails(OidcTokenResponseDTO accessToken) {
        Map<String, Object> info = new HashMap<>();
        UserInfoRecoveryMode recoveryMode = getUserInfoRecoveryMode();
        if (recoveryMode.requiresUserInfoCall()) {
            info.putAll(getOidcUserInfo(accessToken.getAccessToken()));
        }
        if (recoveryMode.requiresClaimsMerge()) {
            info.putAll(Objects.requireNonNull(getClaims(accessToken.getIdToken())));
        }
        return info;
    }

    /**
     * Retrieves the UserInfoRecoveryMode from configuration.
     * Default value is USERINFO_CALL (0) if not configured.
     *
     * @return the configured UserInfoRecoveryMode
     * @throws RuntimeException if the configured value is invalid
     */
    private UserInfoRecoveryMode getUserInfoRecoveryMode() {
        int configuredValue = configurationService.getIntProperty("authentication-oidc.user-info-recovery-mode", 0);
        return Stream.of(UserInfoRecoveryMode.values())
                .filter(mode -> mode.getValue() == configuredValue)
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "Invalid value for authentication-oidc.user-info-recovery-mode: " + configuredValue +
                                ". Allowed values are: 0 (USERINFO_CALL), 1 (MERGE_CLAIMS), 2 (BOTH)"));
    }

    /**
     * Extracts all claims from JWT token into a Map
     *
     * @param token JWT token to decode
     * @return Map containing all claims as key-value pairs, null if decoding fails
     */
    private Map<String, Object> getClaims(String token) {
        try {
            JSONObject jsonObject = decodeJWTToken(token);
            if (jsonObject == null) {
                return null;
            }

            Map<String, Object> claims = new HashMap<>();
            for (String key : jsonObject.keySet()) {
                claims.put(key, jsonObject.get(key));
            }
            return claims;

        } catch (Exception e) {
            LOGGER.error("Error extracting claims from token: {}", e.getMessage());
            return null;
        }
    }


    /**
     * Decodes JWT token payload without signature verification
     *
     * @param token JWT token to decode
     * @return JSONObject containing token claims, null if parsing fails
     */
    private JSONObject decodeJWTToken(String token) {
        try {
            if (isBlank(token)) {
                return null;
            }

            // Split token into parts
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                LOGGER.error("Invalid token format");
                return null;
            }

            // Decode payload (second part of token)
            Base64.Decoder decoder = Base64.getUrlDecoder();
            String payload = new String(decoder.decode(parts[1]));

            return new JSONObject(payload);

        } catch (Exception e) {
            LOGGER.error("Error decoding token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Updates the provided EPerson object with the user information retrieved from a Map of userInfo data.
     * This includes updating attributes such as first name, last name, email, and net ID. If the size of
     * the first name or last name exceeds the maximum allowed length, it will be truncated.
     * The changes are persisted using the context and ePersonService.
     *
     * @param context the DSpace context in which this operation is performed
     * @param ePerson the EPerson object to be updated
     * @param userInfo a map containing user information, including attributes such as first name,
     *                 last name, email, and net ID
     * @throws SQLException if an SQL database error occurs during the operation
     * @throws AuthorizeException if the current user is not authorized to perform the update
     */
    private void updateEPerson(Context context, EPerson ePerson, Map<String, Object> userInfo)
            throws SQLException, AuthorizeException {
        String firstName = getAttributeAsString(userInfo, getFirstNameAttribute());
        String lastName = getAttributeAsString(userInfo, getLastNameAttribute());
        String email = getAttributeAsString(userInfo, getEmailAttribute());
        String netId = getAttributeAsString(userInfo, getNetIdAttribute());

        if (StringUtils.isNotBlank(firstName) && firstName.length() > NAME_MAX_SIZE) {
            LOGGER.warn(
                    "Truncating eperson's first name because it is longer than {}: {}", NAME_MAX_SIZE, firstName);
            firstName = firstName.substring(0, NAME_MAX_SIZE);
        }

        if (StringUtils.isNotBlank(lastName) && lastName.length() > NAME_MAX_SIZE) {
            LOGGER.warn(
                    "Truncating eperson's last name because it is longer than {}: {}", NAME_MAX_SIZE, lastName);
            lastName = lastName.substring(0, NAME_MAX_SIZE);
        }

        ePerson.setFirstName(context, firstName);
        ePerson.setLastName(context, lastName);
        ePerson.setEmail(email);
        ePerson.setNetid(netId);

        context.turnOffAuthorisationSystem();
        ePersonService.update(context, ePerson);
        context.dispatchEvents();
        context.restoreAuthSystemState();
    }


    @Override
    public String loginPageURL(Context context, HttpServletRequest request, HttpServletResponse response) {

        String authorizeUrl = configurationService.getProperty("authentication-oidc.authorize-endpoint");
        String clientId = configurationService.getProperty("authentication-oidc.client-id");
        String clientSecret = configurationService.getProperty("authentication-oidc.client-secret");
        String redirectUri = configurationService.getProperty("authentication-oidc.redirect-url");
        String tokenUrl = configurationService.getProperty("authentication-oidc.token-endpoint");
        String userInfoUrl = configurationService.getProperty("authentication-oidc.user-info-endpoint");

        String[] defaultScopes = {  "openid", "email", "profile" };
        String scopes = join(" ", configurationService.getArrayProperty("authentication-oidc.scopes", defaultScopes));

        if (isAnyBlank(authorizeUrl, clientId, redirectUri, clientSecret, tokenUrl, userInfoUrl)) {
            LOGGER.error("Missing mandatory configuration properties for OidcAuthenticationBean");

            // prepare a Map of the properties which can not have sane defaults, but are still required
            final Map<String, String> map = Map.of("authorizeUrl", authorizeUrl, "clientId", clientId, "redirectUri",
                redirectUri, "clientSecret", clientSecret, "tokenUrl", tokenUrl, "userInfoUrl", userInfoUrl);
            final Iterator<Entry<String, String>> iterator = map.entrySet().iterator();

            while (iterator.hasNext()) {
                final Entry<String, String> entry = iterator.next();

                if (isBlank(entry.getValue())) {
                    LOGGER.error(" * {} is missing", entry.getKey());
                }
            }
            return "";
        }

        try {
            return format(LOGIN_PAGE_URL_FORMAT, authorizeUrl, clientId, scopes, encode(redirectUri, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e.getMessage(), e);
            return "";
        }

    }

    private int logInEPerson(Context context, EPerson ePerson) {
        context.setCurrentUser(ePerson);
        return SUCCESS;
    }

    private int registerNewEPerson(Context context, Map<String, Object> userInfo, String email) throws SQLException {
        try {

            context.turnOffAuthorisationSystem();

            EPerson eperson = ePersonService.create(context);

            String netId = getAttributeAsString(userInfo, getNetIdAttribute());
            eperson.setNetid(StringUtils.isNotBlank(netId) ? netId : email);
            eperson.setEmail(email);

            String firstName = getAttributeAsString(userInfo, getFirstNameAttribute());
            if (StringUtils.isNotBlank(firstName)) {
                if (firstName.length() > NAME_MAX_SIZE) {
                    LOGGER.warn(
                            "Truncating new e-person's first name because it is longer than {}: {}",
                            NAME_MAX_SIZE, firstName);
                    firstName = firstName.substring(0, NAME_MAX_SIZE);
                }
                eperson.setFirstName(context, firstName);
            }

            String lastName = getAttributeAsString(userInfo, getLastNameAttribute());
            if (StringUtils.isNotBlank(lastName)) {
                if (lastName.length() > NAME_MAX_SIZE) {
                    LOGGER.warn(
                            "Truncating new e-person's last name because it is longer than {}: {}",
                            NAME_MAX_SIZE, lastName);
                    lastName = lastName.substring(0, NAME_MAX_SIZE);
                }
                eperson.setLastName(context, lastName);
            }

            eperson.setCanLogIn(true);
            eperson.setSelfRegistered(true);

            ePersonService.update(context, eperson);
            context.setCurrentUser(eperson);
            context.dispatchEvents();

            return SUCCESS;

        } catch (Exception ex) {
            LOGGER.error("An error occurs registering a new EPerson from OIDC", ex);
            context.rollback();
            return NO_SUCH_USER;
        } finally {
            context.restoreAuthSystemState();
        }
    }

    private OidcTokenResponseDTO getOidcAccessToken(String code) {
        try {
            return oidcClient.getAccessToken(code);
        } catch (Exception ex) {
            LOGGER.error("An error occurs retriving the OIDC access_token", ex);
            return null;
        }
    }

    private Map<String, Object> getOidcUserInfo(String accessToken) {
        try {
            return oidcClient.getUserInfo(accessToken);
        } catch (Exception ex) {
            LOGGER.error("An error occurs retriving the OIDC user info", ex);
            return Map.of();
        }
    }

    private String getAttributeAsString(Map<String, Object> userInfo, String attribute) {
        if (isBlank(attribute)) {
            return null;
        }
        return userInfo.containsKey(attribute) ? String.valueOf(userInfo.get(attribute)) : null;
    }

    private String getNetIdAttribute() {
        return configurationService.getProperty("authentication-oidc.user-info.netid", "uniqueid");
    }

    private String getEmailAttribute() {
        return configurationService.getProperty("authentication-oidc.user-info.email", "email");
    }

    private String getFirstNameAttribute() {
        return configurationService.getProperty("authentication-oidc.user-info.first-name", "given_name");
    }

    private String getLastNameAttribute() {
        return configurationService.getProperty("authentication-oidc.user-info.last-name", "family_name");
    }

    private boolean canSelfRegister() {
        String canSelfRegister = configurationService.getProperty("authentication-oidc.can-self-register", "true");
        if (isBlank(canSelfRegister)) {
            return true;
        }
        return toBoolean(canSelfRegister);
    }

    public OidcClient getOidcClient() {
        return this.oidcClient;
    }

    public void setOidcClient(OidcClient oidcClient) {
        this.oidcClient = oidcClient;
    }

    @Override
    public boolean isUsed(final Context context, final HttpServletRequest request) {
        if (request != null &&
                context.getCurrentUser() != null &&
                request.getAttribute(OIDC_AUTHENTICATED) != null) {
            return true;
        }
        return false;
    }

    @Override
    public boolean canChangePassword(Context context, EPerson ePerson, String currentPassword) {
        return false;
    }

}
