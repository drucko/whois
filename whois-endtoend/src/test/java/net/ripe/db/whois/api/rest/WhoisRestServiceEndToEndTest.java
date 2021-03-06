package net.ripe.db.whois.api.rest;

import com.google.common.collect.ImmutableMap;
import net.ripe.db.whois.api.AbstractIntegrationTest;
import net.ripe.db.whois.api.RestTest;
import net.ripe.db.whois.api.rest.mapper.WhoisObjectServerMapper;
import net.ripe.db.whois.common.domain.User;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.rpsl.RpslObjectBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;

public class WhoisRestServiceEndToEndTest extends AbstractIntegrationTest {

    private static ImmutableMap<String, RpslObject> fixtures = ImmutableMap.<String, RpslObject>builder()
            .put("PP1-TEST", RpslObject.parse("" +
                    "person:    Pauleth Palthen\n" +
                    "address:   Singel 258\n" +
                    "phone:     +31-1234567890\n" +
                    "e-mail:    noreply@ripe.net\n" +
                    "mnt-by:    OWNER-MNT\n" +
                    "nic-hdl:   PP1-TEST\n" +
                    "changed:   noreply@ripe.net 20120101\n" +
                    "remarks:   remark\n" +
                    "source:    TEST\n"))

            .put("OWNER-MNT", RpslObject.parse("" +
                    "mntner:      OWNER-MNT\n" +
                    "descr:       Owner Maintainer\n" +
                    "admin-c:     TP1-TEST\n" +
                    "upd-to:      noreply@ripe.net\n" +
                    "auth:        MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
                    "mnt-by:      OWNER-MNT\n" +
                    "referral-by: OWNER-MNT\n" +
                    "changed:     dbtest@ripe.net 20120101\n" +
                    "source:      TEST"))

            .put("TP1-TEST",  RpslObject.parse("" +
                    "person:    Test Person\n" +
                    "address:   Singel 258\n" +
                    "phone:     +31 6 12345678\n" +
                    "nic-hdl:   TP1-TEST\n" +
                    "mnt-by:    OWNER-MNT\n" +
                    "changed:   dbtest@ripe.net 20120101\n" +
                    "source:    TEST\n"))

            .put("TR1-TEST", RpslObject.parse("" +
                    "role:      Test Role\n" +
                    "address:   Singel 258\n" +
                    "phone:     +31 6 12345678\n" +
                    "nic-hdl:   TR1-TEST\n" +
                    "admin-c:   TR1-TEST\n" +
                    "abuse-mailbox: abuse@test.net\n" +
                    "mnt-by:    OWNER-MNT\n" +
                    "changed:   dbtest@ripe.net 20120101\n" +
                    "source:    TEST\n"))

            .build();

    @Autowired
    private WhoisObjectServerMapper whoisObjectMapper;

    @Before
    public void setup() {
        databaseHelper.addObjects(fixtures.values());
    }

    @Test
//    @Ignore
    public void DELETE_THIS_EXAMPLE_TEST() {
        databaseHelper.insertUser(User.createWithPlainTextPassword("agoston", "zoh", ObjectType.PERSON));

        final RpslObject updatedObject = new RpslObjectBuilder(fixtures.get("PP1-TEST")).addAttribute(new RpslAttribute(AttributeType.REMARKS, "updated")).sort().get();

        String whoisResources = RestTest.target(getPort(), "whois/test/person/PP1-TEST?override=agoston,zoh,reason")
                .request(MediaType.APPLICATION_XML)
                .put(Entity.entity(whoisObjectMapper.mapRpslObjects(Arrays.asList(updatedObject)), MediaType.APPLICATION_XML), String.class);

        System.err.println(whoisResources);

//        WhoisResources whoisResources = RestTest.target(getPort(), "whois/test/person/PP1-TEST?override=agoston,zoh,reason")
//                .request(MediaType.APPLICATION_XML)
//                .put(Entity.entity(whoisObjectMapper.mapRpslObjects(Arrays.asList(updatedObject)), MediaType.APPLICATION_XML), WhoisResources.class);

//        RestTest.assertErrorMessage(whoisResources, 0, "Info", "Authorisation override used");
//        assertThat(whoisResources.getWhoisObjects(), hasSize(1));
//        final WhoisObject object = whoisResources.getWhoisObjects().get(0);
//        assertThat(object.getAttributes(), contains(
//                new Attribute("person", "Pauleth Palthen"),
//                new Attribute("address", "Singel 258"),
//                new Attribute("phone", "+31-1234567890"),
//                new Attribute("e-mail", "noreply@ripe.net"),
//                new Attribute("nic-hdl", "PP1-TEST"),
//                new Attribute("remarks", "remark"),
//                new Attribute("remarks", "updated"),
//                new Attribute("mnt-by", "OWNER-MNT", null, "mntner", new Link("locator", "http://rest-test.db.ripe.net/test/mntner/OWNER-MNT")),
//                new Attribute("changed", "noreply@ripe.net 20120101"),
//                new Attribute("source", "TEST")));
//
//        assertThat(whoisResources.getTermsAndConditions().getHref(), is(WhoisResources.TERMS_AND_CONDITIONS));
    }


}
