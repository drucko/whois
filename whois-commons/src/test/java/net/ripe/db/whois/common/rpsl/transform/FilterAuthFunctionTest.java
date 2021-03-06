package net.ripe.db.whois.common.rpsl.transform;

import net.ripe.db.whois.common.rpsl.RpslObject;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class FilterAuthFunctionTest {
    FilterAuthFunction subject;

    @Before
    public void setUp() throws Exception {
        subject = new FilterAuthFunction();
    }

    @Test
    public void apply_irt() {
        final RpslObject rpslObject = RpslObject.parse("" +
                "irt: DEV-IRT\n" +
                "auth: MD5-PW $1$YmPozTxJ$s3eGZRVrKVGdSDTeEZJu\n" +
                "source: RIPE"
        );

        final RpslObject response = subject.apply(rpslObject);
        assertThat(response, is(RpslObject.parse("" +
                "irt:            DEV-IRT\n" +
                "auth:           MD5-PW # Filtered\n" +
                "source:         RIPE # Filtered\n")));
    }

    @Test
    public void apply_no_md5() {
        final RpslObject rpslObject = RpslObject.parse("" +
                "mntner: WEIRD-MNT\n" +
                "auth: value\n" +
                "source: RIPE"
        );

        final RpslObject response = subject.apply(rpslObject);
        assertThat(response, is(rpslObject));
    }

    @Test
    public void apply_md5_filtered() {
        final RpslObject rpslObject = RpslObject.parse("" +
                "mntner: WEIRD-MNT\n" +
                "auth: MD5-PW $1$YmPozTxJ$s3eGZRVrKVGdSDTeEZJu//\n" +
                "auth: MD5-PW $1$YmPozTxJ$s3eGZRVrKVGdSDTeEZJu//\n" +
                "source: RIPE"
        );

        final RpslObject response = subject.apply(rpslObject);

        assertThat(response.toString(), is("" +
                "mntner:         WEIRD-MNT\n" +
                "auth:           MD5-PW # Filtered\n" +
                "auth:           MD5-PW # Filtered\n" +
                "source:         RIPE # Filtered\n"));
    }

    @Test
    public void apply_md5_filtered_incorrect_password() {
        subject = new FilterAuthFunction(Collections.singletonList("test0"));
        final RpslObject rpslObject = RpslObject.parse("" +
                "mntner:         WEIRD-MNT\n" +
                "auth:           MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
                "auth:           MD5-PW $1$5XCg9Q1W$O7g9bgeJPkpea2CkBGnz/0 #test1\n" +
                "auth:           MD5-PW $1$ZjlXZmWO$VKyuYp146Vx5b1.398zgH/ #test2\n" +
                "source:         RIPE"
        );

        final RpslObject response = subject.apply(rpslObject);

        assertThat(response.toString(), is("" +
                "mntner:         WEIRD-MNT\n" +
                "auth:           MD5-PW # Filtered\n" +
                "auth:           MD5-PW # Filtered\n" +
                "auth:           MD5-PW # Filtered\n" +
                "source:         RIPE # Filtered\n"));
    }

    @Test
    public void apply_md5_unfiltered() {
        subject = new FilterAuthFunction(Collections.singletonList("test1"));
        final RpslObject rpslObject = RpslObject.parse("" +
                "mntner:         WEIRD-MNT\n" +
                "auth:           MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
                "auth:           MD5-PW $1$5XCg9Q1W$O7g9bgeJPkpea2CkBGnz/0 #test1\n" +
                "auth:           MD5-PW $1$ZjlXZmWO$VKyuYp146Vx5b1.398zgH/ #test2\n" +
                "source:         RIPE"
        );

        final RpslObject response = subject.apply(rpslObject);

        assertThat(response.toString(), is("" +
                "mntner:         WEIRD-MNT\n" +
                "auth:           MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
                "auth:           MD5-PW $1$5XCg9Q1W$O7g9bgeJPkpea2CkBGnz/0 #test1\n" +
                "auth:           MD5-PW $1$ZjlXZmWO$VKyuYp146Vx5b1.398zgH/ #test2\n" +
                "source:         RIPE\n"));
    }

    @Test
    public void apply_sso() {
        final RpslObject rpslObject = RpslObject.parse("" +
                "mntner: SSO-MNT\n" +
                "auth: SSO T2hOz8tlmka5lxoZQxzC1Q00\n" +
                "source: RIPE");

        final RpslObject result = subject.apply(rpslObject);
        assertThat(result.toString(), is("" +
                "mntner:         SSO-MNT\n" +
                "auth:           SSO # Filtered\n" +
                "source:         RIPE # Filtered\n"));
    }
}
