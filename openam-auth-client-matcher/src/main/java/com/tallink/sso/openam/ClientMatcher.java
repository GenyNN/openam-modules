package com.tallink.sso.openam;

import com.iplanet.sso.SSOException;
import com.sun.identity.authentication.spi.AMLoginModule;
import com.sun.identity.authentication.spi.AuthLoginException;
import com.sun.identity.authentication.util.ISAuthConstants;
import com.sun.identity.idm.*;
import com.sun.identity.shared.datastruct.CollectionHelper;
import com.sun.identity.shared.debug.Debug;
import com.tallink.sso.openam.clientmatcher.ClientMatcherService;
import org.forgerock.openam.authentication.modules.common.mapping.AccountProvider;
import org.forgerock.openam.utils.CollectionUtils;
import org.json.JSONException;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.util.*;

// TODO
// [WARNING] /C:/Users/alexe/_work/projects/open-am-components/openam-auth-client-matcher/src/main/java/com/tallink/sso/openam/ClientMatcher.java: C:\Users\alexe\_work\projects\open-am-components\openam-auth-client-matcher\src\main\java\com\tallink\sso\openam\ClientMatcher.java uses unchecked or unsafe operations.
// [WARNING] /C:/Users/alexe/_work/projects/open-am-components/openam-auth-client-matcher/src/main/java/com/tallink/sso/openam/ClientMatcher.java: Recompile with -Xlint:unchecked for details.

public class ClientMatcher extends AMLoginModule {

    // Name for the debug-log
    private final static String DEBUG_NAME = "ClientMatcher";
    private final static Debug debug = Debug.getInstance(DEBUG_NAME);
    // Name of the resource bundle
    private final static String amAuthClientMatcher = "amAuthClientMatcher";
    // Orders defined in the callbacks file
    private final static int STATE_BEGIN = 1;
    private final static int STATE_AUTH = 2;
    private final static int STATE_ERROR = 3;
    // User names for authentication logic
    private String authenticatedUser = null;

    private static final String DEFAULT_PASSWORD = "changeit";
    private static final String LDAP_EMAIL = "mail";
    private static final String LDAP_TEL_NUMBER = "telephoneNumber";
    private static final String LDAP_FIRST_NAME = "givenName";
    private static final String LDAP_LAST_NAME = "sn";
    private static final String LDAP_UID = "uid";
    private static final String LDAP_CLUB_ONE = "clubone";
    private static final String LDAP_PASSWORD = "userPassword";
    private static final String LDAP_INET_STATUS = "inetuserstatus";
    private static final String LDAP_OBJECT_CLASS = "objectclass";
    private static final String LDAP_BIRTH_DAY = "birthdate";
    private static final String LDAP_ID_SEAWARE = "seaware-id";
    private static final String LDAP_FULL_NAME = "cn";


    private static final String CM_FULL_NAME = "FULL_NAME";
    private static final String CM_CLIENT_ID = "CLIENT_ID";
    private static final String CM_EMAIL = "EMAIL";
    private static final String CM_PHONE_NUMBER = "PHONE_NUMBER";
    private static final String CM_FIRST_NAME = "FIRST_NAME";
    private static final String CM_LAST_NAME = "LAST_NAME";
    private static final String CM_CLUB_ONE = "CLUB_ACCOUNT";
    private static final String INET_STATUS = "Active";
    private static final String CM_BIRTH_DAY = "BIRTHDAY";
    private static final String CM_ID_SEAWARE = "CLIENT_ID";


    private static final Set<String> setObjectClasses = CollectionUtils.asSet("Tallink", "top", "person",
            "organizationalPerson");

    private ResourceBundle bundle;
    private Map currentConfig;
    private ClientMatcherService service;
    private String email;
    private String phoneNumber;
    private String countryCode;
    private String clubOne;
    private String firstName;
    private String lastName;

    public ClientMatcher() {
        super();
    }


    @Override
    public void init(Subject subject, Map sharedState, Map options) {

        debug.message("ClientMatcher::init");
        this.bundle = amCache.getResBundle(amAuthClientMatcher, Locale.ENGLISH);
        currentConfig = options;
        String clientMatcherUrl = CollectionHelper.getMapAttr(options, "tallink-client-matcher-url");
        this.service = new ClientMatcherService(clientMatcherUrl);

    }

    @Override
    public int process(Callback[] callbacks, int state) throws LoginException {

        debug.message("ClientMatcher::process state: {}", state);

        switch (state) {

            case STATE_BEGIN:
                // No time wasted here - simply modify the UI and
                // proceed to next state
                substituteUIStrings();
                return STATE_AUTH;

            case STATE_AUTH:
                // Get data from callbacks. Refer to callbacks XML file.

                initSearchParams(callbacks);

                String realm = getRequestOrg();
                if (realm == null) {
                    realm = "/";
                }
                AccountProvider accountProvider = instantiateAccountProvider();

                try {

                    authenticatedUser = getUser(realm, accountProvider);
                    if (!org.apache.commons.lang.StringUtils.isEmpty(authenticatedUser)) {
                        storeUsernamePasswd(authenticatedUser, DEFAULT_PASSWORD);
                        return ISAuthConstants.LOGIN_SUCCEED;
                    } else {

                        Map<String, Object> bm = service.tryFindBestMatch(getClientMatcherParams());
                        if (bm != null) {
                            authenticatedUser = provisionAccountNow(accountProvider, realm, bm);
                            storeUsernamePasswd(authenticatedUser, DEFAULT_PASSWORD);
                            return ISAuthConstants.LOGIN_SUCCEED;
                        } else {
                            return ISAuthConstants.LOGIN_IGNORE;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return STATE_ERROR;
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (SSOException e) {
                    e.printStackTrace();
                } catch (IdRepoException e) {
                    e.printStackTrace();
                }
                throw new AuthLoginException("amAuthClientMatcher", "authFailed", null);

            case STATE_ERROR:
                return STATE_ERROR;
            default:
                throw new AuthLoginException("invalid state");
        }
    }

    private void initSearchParams(Callback[] callbacks) {

        TextInputCallback ec;
        TextInputCallback pnc;
        TextInputCallback cc;
        TextInputCallback coc;
        TextInputCallback fnc;
        TextInputCallback lnc;
        if (callbacks != null) {
            ec = (TextInputCallback) callbacks[0];
            email = ec.getText();
            pnc = (TextInputCallback) callbacks[1];
            phoneNumber = pnc.getText();
            cc = (TextInputCallback) callbacks[2];
            countryCode = cc.getText();
            coc = (TextInputCallback) callbacks[3];
            clubOne = coc.getText();
            fnc = (TextInputCallback) callbacks[4];
            firstName = fnc.getText();
            lnc = (TextInputCallback) callbacks[5];
            lastName = lnc.getText();
        } else {
            email = "";
            phoneNumber = "";
            countryCode = "";
            clubOne = "";
            firstName = "";
            lastName = "";
        }

    }

    private Map<String, Set<String>> getLDAPSearchAttributes() {
        Map<String, Set<String>> paramVals = new HashMap<>();
        if (!email.isEmpty())
            paramVals.put(LDAP_EMAIL, CollectionUtils.asSet(email));
        if (!phoneNumber.isEmpty()) {
            String phoneWithDash = countryCode + "-" + phoneNumber;
            String phoneWithoutDash = countryCode + phoneNumber;
            paramVals.put(LDAP_TEL_NUMBER, CollectionUtils.asSet(phoneWithDash, phoneWithoutDash));
        }
        if (!clubOne.isEmpty()) {
            paramVals.put(LDAP_CLUB_ONE, CollectionUtils.asSet(clubOne));
            paramVals.put(LDAP_FIRST_NAME, CollectionUtils.asSet(firstName));
            paramVals.put(LDAP_LAST_NAME, CollectionUtils.asSet(lastName));
        }
        return paramVals;
    }


    @Override
    public Principal getPrincipal() {
        return new ClientMatcherPrincipal(authenticatedUser);
    }

    private void substituteUIStrings() throws AuthLoginException {

        // Get property from bundle
        String new_hdr = bundle.getString("clientmatcher-auth-ui-login-header");
        substituteHeader(STATE_AUTH, new_hdr);

        replaceCallback(STATE_AUTH, 0, new TextInputCallback(
                bundle.getString("clientmatcher-ui-email-prompt")));

        replaceCallback(STATE_AUTH, 1, new TextInputCallback(
                bundle.getString("clientmatcher-ui-phonenumber-prompt")));

        replaceCallback(STATE_AUTH, 2, new TextInputCallback(
                bundle.getString("clientmatcher-ui-countrycode-prompt")));

        replaceCallback(STATE_AUTH, 3, new TextInputCallback(
                bundle.getString("clientmatcher-ui-clubone-prompt")));

        replaceCallback(STATE_AUTH, 4, new TextInputCallback(
                bundle.getString("clientmatcher-ui-firstname-prompt")));

        replaceCallback(STATE_AUTH, 5, new TextInputCallback(
                bundle.getString("clientmatcher-ui-lastname-prompt")));
    }


    private String provisionAccountNow(AccountProvider accountProvider, String realm, Map<String, Object> bestMatch)
            throws AuthLoginException {

        Map<String, Set<String>> attributes = new HashMap<>();

        String uid =  bestMatch.get(CM_FULL_NAME) + "_" + bestMatch.get(CM_CLIENT_ID);
        attributes.put(LDAP_UID, CollectionUtils.asSet(uid));
        attributes.put(LDAP_EMAIL, CollectionUtils.asSet((String) bestMatch.get(CM_EMAIL)));
        attributes.put(LDAP_TEL_NUMBER, CollectionUtils.asSet((String) bestMatch.get(CM_PHONE_NUMBER)));
        attributes.put(LDAP_FIRST_NAME, CollectionUtils.asSet((String) bestMatch.get(CM_FIRST_NAME)));
        attributes.put(LDAP_LAST_NAME, CollectionUtils.asSet((String) bestMatch.get(CM_LAST_NAME)));
        attributes.put(LDAP_CLUB_ONE, CollectionUtils.asSet((String) bestMatch.get(CM_CLUB_ONE)));
        attributes.put(LDAP_FULL_NAME, CollectionUtils.asSet((String) bestMatch.get(CM_FULL_NAME)));
        attributes.put(LDAP_PASSWORD, CollectionUtils.asSet(DEFAULT_PASSWORD));
        attributes.put(LDAP_INET_STATUS, CollectionUtils.asSet(INET_STATUS));
        attributes.put(LDAP_OBJECT_CLASS, setObjectClasses);
        attributes.put(LDAP_BIRTH_DAY, CollectionUtils.asSet((String) bestMatch.get(CM_BIRTH_DAY)));
        attributes.put(LDAP_ID_SEAWARE, CollectionUtils.asSet(bestMatch.get(CM_ID_SEAWARE).toString()));

        AMIdentity userIdentity =
                accountProvider.provisionUser(getAMIdentityRepository(realm),
                        attributes);
        if (userIdentity != null) {
            return userIdentity.getName().trim();
        } else {
            return null;
        }
    }

    // Create an instance of the pluggable account mapper
    private AccountProvider instantiateAccountProvider()
            throws AuthLoginException {
        try {
            String accountProviderString = CollectionHelper.getMapAttr(currentConfig, "tallink-client-matcher-account-provider");
            return getConfiguredType(AccountProvider.class, accountProviderString);
        } catch (ClassCastException ex) {
            debug.error("Account Provider is not actually an implementation of AccountProvider.", ex);
            throw new AuthLoginException("Problem when trying to instantiate the account provider", ex);
        } catch (Exception ex) {
            throw new AuthLoginException("Problem when trying to instantiate the account provider", ex);
        }
    }

    private <T> T getConfiguredType(Class<T> type, String config) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String[] parameters = new String[0];
        int delimiter = config.indexOf('|');
        if (delimiter > -1) {
            parameters = config.substring(delimiter + 1).split("\\|");
            config = config.substring(0, delimiter);
        }

        Class<? extends T> clazz = Class.forName(config).asSubclass(type);
        Class<?>[] parameterTypes = new Class<?>[parameters.length];
        Arrays.fill(parameterTypes, String.class);
        return clazz.getConstructor(parameterTypes).newInstance(parameters);
    }

    // Search for the user in the realm, using the instantiated account mapper
    private String getUser(String realm, AccountProvider accountProvider)
            throws AuthLoginException, JSONException, SSOException, IdRepoException {
        Map<String, Set<String>> attributes = getLDAPSearchAttributes();
        String user = null;
        prepareSearch(realm, attributes);
        AMIdentity userIdentity = accountProvider.searchUser(
                getAMIdentityRepository(realm), attributes);
        if (userIdentity != null) {
            user = userIdentity.getName();
        }
        return user;
    }

    private String[] getClientMatcherParams() {
        List<String> params = new ArrayList<>();
        if (!email.isEmpty()) {
            params.add(ClientMatcherService.EMAIL);
            params.add(email);
        }
        if (!phoneNumber.isEmpty()) {
            params.add(ClientMatcherService.PHONE);
            params.add(phoneNumber);
        }
        if (!countryCode.isEmpty()) {
            params.add(ClientMatcherService.COUNTRY_CODE);
            params.add(countryCode);
        }
        if (!clubOne.isEmpty()) {
            params.add(ClientMatcherService.CLUB_ONE);
            params.add(clubOne);
        }
        if (!firstName.isEmpty()) {
            params.add(ClientMatcherService.FIRST_NAME);
            params.add(firstName);
        }
        if (!lastName.isEmpty()) {
            params.add(ClientMatcherService.LAST_NAME);
            params.add(lastName);
        }
        return params.toArray(new String[params.size()]);
    }

    private void prepareSearch(String realm, Map<String, Set<String>> attributes) throws IdRepoException, SSOException {
        IdSearchControl idSearchControl = new IdSearchControl();
        idSearchControl.setSearchModifiers(IdSearchOpModifier.AND, attributes);
        idSearchControl.setAllReturnAttributes(true);
        getAMIdentityRepository(realm).searchIdentities(IdType.USER, "*", idSearchControl);
    }

}
