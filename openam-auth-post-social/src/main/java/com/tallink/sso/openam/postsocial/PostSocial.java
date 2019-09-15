/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file at legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 */

package com.tallink.sso.openam.postsocial;

import java.lang.reflect.InvocationTargetException;
import java.security.Principal;
import java.util.*;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.login.LoginException;

import com.iplanet.sso.SSOException;
import com.sun.identity.authentication.spi.AMLoginModule;
import com.sun.identity.authentication.spi.AuthLoginException;
import com.sun.identity.authentication.util.ISAuthConstants;
import com.sun.identity.idm.*;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.openam.authentication.modules.common.mapping.AccountProvider;
import org.json.JSONException;

import static org.forgerock.openam.utils.CollectionUtils.asSet;


public class PostSocial extends AMLoginModule {

    // Name for the debug-log
    private final static String DEBUG_NAME = "PostSocial";
    private final static Debug debug = Debug.getInstance(DEBUG_NAME);

    // Name of the resource bundle
    private final static String amAuthPostSocial = "amAuthPostSocial";

    private final static String UID_ATTRIBUTE = "uid";
    private final static String GOOGLE_ID_PREFIX = "google-";
    private final static String GOOGLE_ID_ATTRIBUTE = "google-id";
    private final static String FACEBOOK_ID_ATTRIBUTE = "facebook-id";

    private Map<String, String> options;
    private ResourceBundle bundle;
    private Map<String, String> sharedState;

    private String authenticatedUser = null;

    public PostSocial() {
        super();
    }


    /**
     * This method stores service attributes and localized properties for later
     * use.
     * @param subject
     * @param sharedState
     * @param options
     */
    @Override
    public void init(Subject subject, Map sharedState, Map options) {

        debug.message("PostSocial::init");

        this.options = options;
        this.sharedState = sharedState;
        this.bundle = amCache.getResBundle(amAuthPostSocial, getLoginLocale());
    }

    @Override
    public int process(Callback[] callbacks, int state) throws LoginException {

        debug.message("PostSocial::process state: {}", state);

        String uid = getUserSessionProperty(UID_ATTRIBUTE);
        if (uid == null) {
            throw new AuthLoginException("Got no user ID");
        }

        Map<String, Set<String>> searchParameters = createSearchParameters(uid);
        String realm = selectRealm();
        AccountProvider accountProvider = instantiateAccountProvider();

        try {
            authenticatedUser = getUser(realm, accountProvider, searchParameters);
            if (!org.apache.commons.lang.StringUtils.isEmpty(authenticatedUser)) {
                return ISAuthConstants.LOGIN_SUCCEED;
            }
        } catch (JSONException | SSOException | IdRepoException e) {
            throw new AuthLoginException("PostSocial:unable to read from the Data Store", e);
        }

        throw new AuthLoginException("PostSocial:unable to find user in the Data Store");
    }

    private String selectRealm() {
        String realm = getRequestOrg();
        if (realm == null) {
            realm = "/";
        }
        return realm;
    }

    private Map<String, Set<String>> createSearchParameters(String uid) {
        String searchAttribute;
        if (uid.startsWith(GOOGLE_ID_PREFIX)) searchAttribute = GOOGLE_ID_ATTRIBUTE;
        else searchAttribute = FACEBOOK_ID_ATTRIBUTE;

        Map<String, Set<String>> paramVals = new HashMap<>();
        paramVals.put(searchAttribute, asSet(uid));
        return paramVals;
    }

    // Create an instance of the pluggable account mapper
    private AccountProvider instantiateAccountProvider()
            throws AuthLoginException {
        try {
            String accountProviderString = "org.forgerock.openam.authentication.modules.common.mapping.DefaultAccountProvider";// CollectionHelper.getMapAttr(options, "org-forgerock-auth-post-social-account-provider");
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
    private String getUser(String realm, AccountProvider accountProvider,
                           Map<String, Set<String>> paramVals)
            throws AuthLoginException, JSONException, SSOException, IdRepoException {
        String user = null;
        prepareSearch(realm, paramVals);
        AMIdentity userIdentity =accountProvider.searchUser(
                getAMIdentityRepository(realm), paramVals);
        if (userIdentity != null) {
            user = userIdentity.getName();
        }
        return user;
    }

    private void prepareSearch(String realm, Map<String, Set<String>> attributes) throws IdRepoException, SSOException {
        IdSearchControl idSearchControl = new IdSearchControl();
        idSearchControl.setSearchModifiers(IdSearchOpModifier.OR, attributes);
        idSearchControl.setAllReturnAttributes(true);
        getAMIdentityRepository(realm).searchIdentities(IdType.USER, "*", idSearchControl);
    }

    @Override
    public Principal getPrincipal() {
        if (authenticatedUser != null) {
            return new PostSocialPrincipal(authenticatedUser);
        }
        return null;
    }
}
