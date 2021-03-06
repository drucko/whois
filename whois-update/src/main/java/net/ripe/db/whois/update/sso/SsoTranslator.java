package net.ripe.db.whois.update.sso;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.rpsl.RpslObjectBuilder;
import net.ripe.db.whois.update.domain.Update;
import net.ripe.db.whois.update.domain.UpdateContext;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class SsoTranslator {
    public static final Splitter SPACE_SPLITTER = Splitter.on(' ');

    public String getUuidForUsername(UpdateContext updateContext, String username) {
        String ssoTranslationResult = updateContext.getSsoTranslationResult(username);
        if (ssoTranslationResult != null) {
            return ssoTranslationResult;
        }

        // TODO: implement
        updateContext.addSsoTranslationResult(username, "1234-5678-90AB-DCEF");
        return updateContext.getSsoTranslationResult(username);
//        throw new IllegalArgumentException("Unknown RIPE Access user: " + username);
    }

    public String getUsernameForUuid(UpdateContext updateContext, String uuid) {
        String ssoTranslationResult = updateContext.getSsoTranslationResult(uuid);
        if (ssoTranslationResult != null) {
            return ssoTranslationResult;
        }

        // TODO: implement
        updateContext.addSsoTranslationResult(uuid, "agoston@ripe.net");
        return updateContext.getSsoTranslationResult(uuid);
//        throw new IllegalArgumentException("Unknown RIPE Access UUID: " + uuid);
    }

    public void populate(Update update, UpdateContext updateContext) {
        RpslObject submittedObject = update.getSubmittedObject();
        if (!ObjectType.MNTNER.equals(submittedObject.getType())) {
            return;
        }

        for (RpslAttribute auth : submittedObject.findAttributes(AttributeType.AUTH)) {
            CIString authValue = auth.getCleanValue();
            Iterator<String> authIterator = SPACE_SPLITTER.split(authValue).iterator();
            String passwordType = authIterator.next();
            if (passwordType.equalsIgnoreCase("SSO")) {
                getUuidForUsername(updateContext, authIterator.next());
            }
        }
    }

    public RpslObject translateAuthToUuid(UpdateContext updateContext, RpslObject rpslObject) {
        if (!ObjectType.MNTNER.equals(rpslObject.getType())) {
            return rpslObject;
        }

        Map<RpslAttribute, RpslAttribute> replace = Maps.newHashMap();
        for (RpslAttribute auth : rpslObject.findAttributes(AttributeType.AUTH)) {
            CIString authValue = auth.getCleanValue();
            Iterator<String> authIterator = SPACE_SPLITTER.split(authValue.toUpperCase()).iterator();
            String passwordType = authIterator.next();
            if (passwordType.equals("SSO")) {
                replace.put(auth, new RpslAttribute(auth.getKey(), "SSO" + getUuidForUsername(updateContext, authIterator.next())));
            }
        }

        if (replace.isEmpty()) {
            return rpslObject;
        } else {
            return new RpslObjectBuilder(rpslObject).replaceAttributes(replace).get();
        }
    }
}
