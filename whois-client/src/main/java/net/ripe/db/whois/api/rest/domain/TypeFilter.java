package net.ripe.db.whois.api.rest.domain;

import javax.annotation.concurrent.Immutable;
import javax.xml.bind.annotation.*;

@Immutable
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "")
@XmlRootElement(name = "type-filter")
public class TypeFilter {

    @XmlAttribute(name = "id", required = true)
    private String id;

    public TypeFilter(final String id) {
        this.id = id;
    }

    public TypeFilter() {
        // required no-arg constructor
    }

    public String getId() {
        return id;
    }
}
