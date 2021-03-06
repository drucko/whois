package net.ripe.db.whois.api.rest.mapper;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.ripe.db.whois.api.rest.domain.Attribute;
import net.ripe.db.whois.api.rest.domain.Link;
import net.ripe.db.whois.api.rest.domain.Source;
import net.ripe.db.whois.api.rest.domain.WhoisObject;
import net.ripe.db.whois.api.rest.domain.WhoisResources;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectTemplate;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.apache.commons.lang.StringUtils;

import java.util.Iterator;
import java.util.List;

public abstract class AbstractWhoisObjectMapper {

   protected final String baseUrl;

    public AbstractWhoisObjectMapper(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public RpslObject map(final WhoisObject whoisObject) {
        final List<RpslAttribute> rpslAttributes = Lists.newArrayList();

        for (final Attribute attribute : whoisObject.getAttributes()) {
            String rpslValue;

            final String value = attribute.getValue();
            final String comment = attribute.getComment();
            if (StringUtils.isBlank(comment)) {
                rpslValue = value;
            } else {
                if (value.indexOf('#') >= 0) {
                    throw new IllegalArgumentException("Value cannot have a comment in " + attribute);
                }
                rpslValue = value + " # " + comment;
            }
            rpslAttributes.add(new RpslAttribute(attribute.getName(), rpslValue));
        }

        return new RpslObject(rpslAttributes);
    }

    public List<RpslObject> mapWhoisObjects(final Iterable<WhoisObject> whoisObjects) {
        final List<RpslObject> rpslObjects = Lists.newArrayList();
        for (WhoisObject whoisObject : whoisObjects) {
            rpslObjects.add(map(whoisObject));
        }
        return rpslObjects;
    }

    public WhoisResources mapRpslObjects(final Iterable<RpslObject> rpslObjects) {
        final WhoisResources whoisResources = new WhoisResources();
        final List<WhoisObject> whoisObjects = Lists.newArrayList();
        for (RpslObject rpslObject : rpslObjects) {
            whoisObjects.add(map(rpslObject));
        }
        whoisResources.setWhoisObjects(whoisObjects);
        return whoisResources;
    }

    public WhoisObject map(final RpslObject rpslObject) {
        final String source = rpslObject.getValueForAttribute(AttributeType.SOURCE).toString().toLowerCase();
        final String type = rpslObject.getType().getName();
        final RpslAttribute primaryKeyRpslAttribute = getPrimaryKey(rpslObject);
        final String primaryKeyName = primaryKeyRpslAttribute.getKey();
        final String primaryKeyValue = primaryKeyRpslAttribute.getCleanValue().toString();
        final Attribute primaryKeyAttribute = createAttribute(primaryKeyName, primaryKeyValue, null, null, null);
        final List<Attribute> primaryKeyAttributes = Lists.newArrayList(primaryKeyAttribute);

        final List<Attribute> attributes = buildAttributes(rpslObject, source);

        return createWhoisObject(
                createSource(source),
                type,
                attributes,
                primaryKeyAttributes,
                createLink(rpslObject));
    }

    private List<Attribute> buildAttributes(RpslObject rpslObject, String source) {
        final List<Attribute> attributes = Lists.newArrayList();
        for (RpslAttribute attribute : rpslObject.getAttributes()) {
            for (CIString value : attribute.getCleanValues()) {
                attributes.add(buildAttribute(attribute, value, source));
            }
        }
        return attributes;
    }

    abstract Attribute buildAttribute(RpslAttribute attribute, final CIString value, final String source);

    protected Link createLink(final RpslObject rpslObject) {
        final String source = rpslObject.getValueForAttribute(AttributeType.SOURCE).toString().toLowerCase();
        final String type = rpslObject.getType().getName();
        final String key = rpslObject.getKey().toString();
        return createLink(source, type, key);
    }

    protected Link createLink(final String source, final String type, final String key) {
        return new Link("locator", String.format("%s/%s/%s/%s", baseUrl, source, type, key));
    }

    protected Source createSource(final String id) {
        return new Source(id);
    }

    protected RpslAttribute getPrimaryKey(final RpslObject rpslObject) {
        final ObjectTemplate objectTemplate = ObjectTemplate.getTemplate(rpslObject.getType());
        Iterator<AttributeType> iterator = Sets.intersection(objectTemplate.getKeyAttributes(), objectTemplate.getLookupAttributes()).iterator();
        if (iterator.hasNext()) {
            AttributeType primaryKey = iterator.next();
            if (!iterator.hasNext()) {
                return rpslObject.findAttribute(primaryKey);
            }
        }

        throw new IllegalArgumentException("Couldn't find primary key attribute for type: " + rpslObject.getType());
    }

    protected Attribute createAttribute(final String name, final String value, final String comment, final String referencedType, final Link link) {
        return new Attribute(name, value, comment, referencedType, link);
    }

    protected WhoisObject createWhoisObject(final Source source, final String type, final List<Attribute> attributes, final List<Attribute> primaryKey, final Link link) {
        final WhoisObject whoisObject = new WhoisObject();
        whoisObject.setSource(source);
        whoisObject.setType(type);
        whoisObject.setLink(link);
        whoisObject.setAttributes(attributes);
        whoisObject.setPrimaryKey(primaryKey);
        return whoisObject;
    }
}
