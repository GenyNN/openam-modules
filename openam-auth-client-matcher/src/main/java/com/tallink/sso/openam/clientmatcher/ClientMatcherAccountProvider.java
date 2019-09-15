package com.tallink.sso.openam.clientmatcher;

import com.iplanet.sso.SSOException;
import com.sun.identity.idm.*;
import com.sun.identity.shared.debug.Debug;
import org.forgerock.openam.authentication.modules.common.mapping.DefaultAccountProvider;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ClientMatcherAccountProvider extends DefaultAccountProvider {

    private static Debug debug = Debug.getInstance("amAuth");

    @Override
    public AMIdentity searchUser(AMIdentityRepository idrepo, Map<String, Set<String>> attr) {
        AMIdentity identity = null;
        if(attr != null && !attr.isEmpty()) {
            IdSearchControl ctrl = this.getSearchControl(IdSearchOpModifier.AND, attr);

            try {
                IdSearchResults results = idrepo.searchIdentities(IdType.USER, "*", ctrl);
                Iterator ex = results.getSearchResults().iterator();
                if(ex.hasNext()) {
                    identity = (AMIdentity)ex.next();
                    if(debug.messageEnabled()) {
                        debug.message("getUser: user found : " + identity.getName());
                    }
                }
            } catch (IdRepoException var7) {
                debug.error("DefaultAccountMapper.searchUser: Problem while searching for the user. IdRepo", var7);
            } catch (SSOException var8) {
                debug.error("DefaultAccountMapper.searchUser: Problem while searching for the user. SSOExc", var8);
            }

            return identity;
        } else {
            debug.warning("DefaultAccountMapper.searchUser: empty search");
            return null;
        }
    }

    private IdSearchControl getSearchControl(IdSearchOpModifier modifier, Map<String, Set<String>> avMap) {
        IdSearchControl control = new IdSearchControl();
        control.setMaxResults(1);
        control.setSearchModifiers(modifier, avMap);
        return control;
    }
}
