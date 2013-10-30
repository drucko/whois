package net.ripe.db.whois.api.whois;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import net.ripe.db.whois.api.whois.domain.*;
import net.ripe.db.whois.common.DateTimeProvider;
import net.ripe.db.whois.common.dao.RpslObjectDao;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.domain.ResponseObject;
import net.ripe.db.whois.common.rpsl.*;
import net.ripe.db.whois.common.source.SourceContext;
import net.ripe.db.whois.query.domain.*;
import net.ripe.db.whois.query.handler.QueryHandler;
import net.ripe.db.whois.query.query.Query;
import net.ripe.db.whois.query.query.QueryFlag;
import net.ripe.db.whois.update.domain.*;
import net.ripe.db.whois.update.handler.UpdateRequestHandler;
import net.ripe.db.whois.update.log.LoggerContext;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.ripe.db.whois.common.domain.CIString.ciString;
import static net.ripe.db.whois.query.query.QueryFlag.*;

@Component
@Path("/")
public class WhoisRestService {
    private static final int STATUS_TOO_MANY_REQUESTS = 429;
    private static final Pattern UPDATE_RESPONSE_ERRORS = Pattern.compile("(?m)^\\*\\*\\*Error:\\s*((.*)(\\n[ ]+.*)*)$");
    private static final Joiner JOINER = Joiner.on(",");
    private static final Splitter AMPERSAND = Splitter.on("&");
    private static final Set<String> NOT_ALLOWED_SEARCH_QUERY_FLAGS = Sets.newHashSet(Iterables.concat(
            TEMPLATE.getFlags(),
            VERBOSE.getFlags(),
            CLIENT.getFlags(),
            NO_GROUPING.getFlags(),
            SOURCES.getFlags(),
            NO_TAG_INFO.getFlags(),
            SHOW_TAG_INFO.getFlags(),
            ALL_SOURCES.getFlags(),
            LIST_SOURCES_OR_VERSION.getFlags(),
            LIST_SOURCES.getFlags(),
            DIFF_VERSIONS.getFlags(),
            LIST_VERSIONS.getFlags(),
            SHOW_VERSION.getFlags(),
            PERSISTENT_CONNECTION.getFlags()
    ));

    private final DateTimeProvider dateTimeProvider;
    private final UpdateRequestHandler updateRequestHandler;
    private final LoggerContext loggerContext;
    private final RpslObjectDao rpslObjectDao;
    private final SourceContext sourceContext;
    private final QueryHandler queryHandler;
    private final WhoisObjectMapper whoisObjectMapper;
    private final InternalUpdatePerformer updatePerformer;

    @Autowired
    public WhoisRestService(final DateTimeProvider dateTimeProvider, final UpdateRequestHandler updateRequestHandler, final LoggerContext loggerContext, final RpslObjectDao rpslObjectDao, final SourceContext sourceContext, final QueryHandler queryHandler, final WhoisObjectMapper whoisObjectMapper, InternalUpdatePerformer updatePerformer) {
        this.dateTimeProvider = dateTimeProvider;
        this.updateRequestHandler = updateRequestHandler;
        this.loggerContext = loggerContext;
        this.rpslObjectDao = rpslObjectDao;
        this.sourceContext = sourceContext;
        this.queryHandler = queryHandler;
        this.whoisObjectMapper = whoisObjectMapper;
        this.updatePerformer = updatePerformer;
    }

    @DELETE
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{source}/{objectType}/{key:.*}")
    public Response restDelete(
            @Context final HttpServletRequest request,
            @PathParam("source") final String source,
            @PathParam("objectType") final String objectType,
            @PathParam("key") final String key,
            @QueryParam("reason") @DefaultValue("--") final String reason,
            @QueryParam("password") final List<String> passwords,
            @QueryParam("override") final String override) {

        checkForMainSource(source);

        final RpslObject originalObject = rpslObjectDao.getByKey(ObjectType.getByName(objectType), key);

        updatePerformer.performUpdate(
                createOrigin(request),
                updatePerformer.createUpdate(originalObject, passwords, reason, override),
                createContent(originalObject, passwords, reason, override),
                Keyword.NONE,
                loggerContext);

        return Response.status(Response.Status.OK).build();
    }

    @PUT
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{source}/{objectType}/{key:.*}")
    public Response restUpdate(
            final WhoisResources resource,
            @Context final HttpServletRequest request,
            @PathParam("source") final String source,
            @PathParam("objectType") final String objectType,
            @PathParam("key") final String key,
            @QueryParam("password") final List<String> passwords,
            @QueryParam("override") final String override) {

        checkForMainSource(source);

        final RpslObject submittedObject = getSubmittedObject(resource);
        validateSubmittedObject(submittedObject, objectType, key);

        final RpslObject response = updatePerformer.performUpdate(
                createOrigin(request),
                updatePerformer.createUpdate(submittedObject, passwords, null, override),
                createContent(submittedObject, passwords, null, override),
                Keyword.NONE,
                loggerContext);

        return Response.ok(createWhoisResources(request, response, false)).build();
    }

    @POST
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{source}/{objectType}")
    public Response restCreate(
            final WhoisResources resource,
            @Context final HttpServletRequest request,
            @PathParam("source") final String source,
            @PathParam("objectType") final String objectType,
            @QueryParam("password") final List<String> passwords,
            @QueryParam("override") final String override) {

        checkForMainSource(source);

        final RpslObject submittedObject = getSubmittedObject(resource);

        final RpslObject response = updatePerformer.performUpdate(
                createOrigin(request),
                updatePerformer.createUpdate(submittedObject, passwords, null, override),
                createContent(submittedObject, passwords, null, override),
                Keyword.NEW,
                loggerContext);

        return Response.ok(createWhoisResources(request, response, false)).build();
    }

    private void checkForMainSource(String source) {
        if (!sourceContext.getCurrentSource().getName().toString().equalsIgnoreCase(source)) {
            throw new IllegalArgumentException("Invalid source: " + source);
        }
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{source}/{objectType}/{key:.*}")
    public Response restGet(
            @Context final HttpServletRequest request,
            @PathParam("source") final String source,
            @PathParam("objectType") final String objectType,
            @PathParam("key") final String key) {

        checkForInvalidSource(source);

        final boolean noFilter = hasUnfilteredQueryParameter(request.getQueryString());
        final Query query = Query.parse(String.format("%s %s %s %s %s %s %s %s %s %s",
                QueryFlag.EXACT.getLongFlag(),
                QueryFlag.NO_GROUPING.getLongFlag(),
                QueryFlag.NO_REFERENCED.getLongFlag(),
                QueryFlag.SOURCES.getLongFlag(),
                source,
                QueryFlag.SELECT_TYPES.getLongFlag(),
                ObjectType.getByName(objectType).getName(),
                QueryFlag.SHOW_TAG_INFO.getLongFlag(),
                noFilter ? QueryFlag.NO_FILTERING.getLongFlag() : "",
                key));

        return handleQuery(query, key, request, null);
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{source}/{objectType}/{key:.*}/versions")
    public Response restVersions(
            @Context final HttpServletRequest request,
            @PathParam("source") final String source,
            @PathParam("objectType") final String objectType,
            @PathParam("key") final String key) {

        checkForMainSource(source);

        final Query query = Query.parse(String.format("--list-versions %s", key));
        return handleQuery(query, key, request, null);
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/{source}/{objectType}/{key:.*}/versions/{version}")
    public Response restVersion(
            @Context final HttpServletRequest request,
            @PathParam("source") final String source,
            @PathParam("objectType") final String objectType,
            @PathParam("key") final String key,
            @PathParam("version") final Integer version) {

        checkForMainSource(source);

        final Query query = Query.parse(String.format("" +
                "--show-version %s %s",
                version,
                key));
        return handleQuery(query, key, request, null);
    }

    // TODO: [AH] refactor this looks-generic-but-is-not method
    private Response handleQuery(final Query query, final String key, final HttpServletRequest request, @Nullable final Parameters parameters) {
        final InetAddress remoteAddress = InetAddresses.forString(request.getRemoteAddr());
        final int contextId = System.identityHashCode(Thread.currentThread());

        if (query.isVersionList() || query.isObjectVersion()) {
            return handleVersionQuery(query, key, remoteAddress, contextId);
        }

        return handleQueryAndStreamResponse(query, request, remoteAddress, contextId, parameters);
    }

    // TODO: [AH] refactor this spaghetti
    private Response handleVersionQuery(final Query query, final String key, final InetAddress remoteAddress, final int contextId) {
        final ApiResponseHandlerVersions apiResponseHandlerVersions = new ApiResponseHandlerVersions();
        queryHandler.streamResults(query, remoteAddress, contextId, apiResponseHandlerVersions);

        final VersionWithRpslResponseObject versionResponseObject = apiResponseHandlerVersions.getVersionWithRpslResponseObject();
        final List<DeletedVersionResponseObject> deleted = apiResponseHandlerVersions.getDeletedObjects();
        final List<VersionResponseObject> versions = apiResponseHandlerVersions.getVersionObjects();

        if (versionResponseObject == null && versions.isEmpty()) {
            if (deleted.isEmpty() || query.isObjectVersion()) {
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            }
        }

        final WhoisResources whoisResources = new WhoisResources();

        if (versionResponseObject != null) {
            final WhoisObject whoisObject = whoisObjectMapper.map(versionResponseObject.getRpslObject());
            whoisObject.setVersion(versionResponseObject.getVersion());
            whoisResources.setWhoisObjects(Collections.singletonList(whoisObject));
        } else {
            final String type = (versions.size() > 0) ? versions.get(0).getType().getName() : deleted.size() > 0 ? deleted.get(0).getType().getName() : null;
            final WhoisVersions whoisVersions = new WhoisVersions(type, key, whoisObjectMapper.mapVersions(deleted, versions));
            whoisResources.setVersions(whoisVersions);
        }

        return Response.ok(whoisResources).build();
    }

    private Response handleQueryAndStreamResponse(final Query query, final HttpServletRequest request, final InetAddress remoteAddress, final int contextId, @Nullable final Parameters parameters) {
        final StreamingMarshal streamingMarshal = getStreamingMarshal(request);

        return Response.ok(new StreamingOutput() {
            private boolean found;

            @Override
            public void write(final OutputStream output) throws IOException {

                // TODO [AK] Crude way to handle tags, but working
                final Queue<RpslObject> rpslObjectQueue = new ArrayDeque<>(1);
                final List<TagResponseObject> tagResponseObjects = Lists.newArrayList();

                try {
                    queryHandler.streamResults(query, remoteAddress, contextId, new ApiResponseHandler() {

                        @Override
                        public void handle(final ResponseObject responseObject) {
                            if (responseObject instanceof TagResponseObject) {
                                tagResponseObjects.add((TagResponseObject) responseObject);
                            } else if (responseObject instanceof RpslObject) {
                                if (!found) {
                                    startStreaming(output);
                                }
                                found = true;
                                streamObject(rpslObjectQueue.poll(), tagResponseObjects);
                                rpslObjectQueue.add((RpslObject) responseObject);
                            }

                            // TODO [AK] Handle related messages
                        }
                    });

                    streamObject(rpslObjectQueue.poll(), tagResponseObjects);

                    if (!found) {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                } catch (QueryException e) {
                    if (e.getCompletionInfo() == QueryCompletionInfo.BLOCKED) {
                        throw new WebApplicationException(Response.status(STATUS_TOO_MANY_REQUESTS).build());
                    } else {
                        throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build());
                    }
                }

                streamingMarshal.close();
            }

            private void startStreaming(final OutputStream output) {
                streamingMarshal.open(output, "whois-resources");

                if (parameters != null) {
                    streamingMarshal.write("parameters", parameters);
                }

                streamingMarshal.start("objects");
            }

            private void streamObject(@Nullable final RpslObject rpslObject, final List<TagResponseObject> tagResponseObjects) {
                if (rpslObject == null) {
                    return;
                }

                final WhoisObject whoisObject = whoisObjectMapper.map(rpslObject, tagResponseObjects);

                if (streamingMarshal instanceof StreamingMarshalJson) {
                    streamingMarshal.write("object", Collections.singletonList(whoisObject));
                } else {
                    streamingMarshal.write("object", whoisObject);
                }

                tagResponseObjects.clear();
            }
        }).build();
    }

    private StreamingMarshal getStreamingMarshal(final HttpServletRequest request) {
        final String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);
        for (final String accept : Splitter.on(',').split(acceptHeader)) {
            try {
                final MediaType mediaType = MediaType.valueOf(accept);
                final String subtype = mediaType.getSubtype().toLowerCase();
                if (subtype.equals("json") || subtype.endsWith("+json")) {
                    return new StreamingMarshalJson();
                } else if (subtype.equals("xml") || subtype.endsWith("+xml")) {
                    return new StreamingMarshalXml();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new StreamingMarshalXml();
    }

    private boolean hasUnfilteredQueryParameter(final String queryString) {
        return !StringUtils.isBlank(queryString) &&
                Iterables.contains(Iterables.transform(AMPERSAND.split(queryString), new Function<String, String>() {
                    @Override
                    public String apply(final String input) {
                        String result = input.toLowerCase();
                        if (result.contains("=")) {
                            return result.substring(0, result.indexOf("="));
                        }

                        return result;
                    }
                }), "unfiltered");
    }

    /**
     * The search interface resembles a standard Whois client query with the extra features of multi-registry client, multiple response styles that can be selected via content negotiation and with an extensible URL parameters schema.
     *
     * @param sources           Mandatory. It's possible to specify multiple sources.
     * @param queryString       Mandatory.
     * @param inverseAttributes If specified the query is an inverse lookup on the given attribute, if not specified the query is a direct lookup search.
     * @param includeTags       Only show RPSL objects with given tags. Can be multiple.
     * @param excludeTags       Only show RPSL objects that <i>do not</i> have given tags. Can be multiple.
     * @param types             If specified the results will be filtered by object-type, multiple type-filters can be specified.
     * @param flags             Optional query-flags. Use separate flags parameters for each option (see examples above)
     * @return Returns the query result.
     */
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Path("/search")
    public Response search(
            @Context HttpServletRequest request,
            @QueryParam("source") Set<String> sources,
            @QueryParam("query-string") String queryString,
            @QueryParam("inverse-attribute") Set<String> inverseAttributes,
            @QueryParam("include-tag") Set<String> includeTags,
            @QueryParam("exclude-tag") Set<String> excludeTags,
            @QueryParam("type-filter") Set<String> types,
            @QueryParam("flags") Set<String> flags) {

        if (sources == null || sources.isEmpty()) {
            sources = Collections.singleton(sourceContext.getCurrentSource().getName().toString());
        } else {
            checkForInvalidSources(sources);
        }

        final Set<String> separateFlags = splitInputFlags(flags);
        checkForInvalidFlags(separateFlags);

        final Query query = Query.parse(String.format("%s %s %s %s %s %s %s %s %s %s %s %s %s",
                QueryFlag.SOURCES.getLongFlag(),
                JOINER.join(sources),
                QueryFlag.SHOW_TAG_INFO.getLongFlag(),
                (types == null || types.isEmpty()) ? "" : QueryFlag.SELECT_TYPES.getLongFlag(),
                JOINER.join(types),
                (inverseAttributes == null || inverseAttributes.isEmpty()) ? "" : QueryFlag.INVERSE.getLongFlag(),
                JOINER.join(inverseAttributes),
                (includeTags == null || includeTags.isEmpty()) ? "" : QueryFlag.FILTER_TAG_INCLUDE.getLongFlag(),
                JOINER.join(includeTags),
                (excludeTags == null || excludeTags.isEmpty()) ? "" : QueryFlag.FILTER_TAG_EXCLUDE.getLongFlag(),
                JOINER.join(excludeTags),
                Joiner.on(" ").join(Iterables.transform(separateFlags, new Function<String, String>() {
                    @Override
                    public String apply(String input) {
                        return input.length() > 1 ? "--" + input : "-" + input;
                    }
                })),
                (queryString == null ? "" : queryString)));

        final Parameters parameters = new Parameters();
        parameters.setSources(sources);
        parameters.setQueryStrings(queryString);
        parameters.setInverseLookup(inverseAttributes);
        parameters.setTypeFilters(types);
        parameters.setFlags(separateFlags);

        return handleQuery(query, queryString, request, parameters);
    }

    private void checkForInvalidSources(final Set<String> sources) {
        for (final String source : sources) {
            checkForInvalidSource(source);
        }
    }

    private void checkForInvalidSource(final String source) {
        if (!sourceContext.getAllSourceNames().contains(ciString(source))) {
            throw new IllegalArgumentException(String.format("Invalid source '%s'", source));
        }
    }

    private Set<String> splitInputFlags(final Set<String> inputFlags) {
        final Set<String> separateFlags = Sets.newLinkedHashSet();  // reporting errors should happen in the same order
        for (final String flagParameter : inputFlags) {
            if (QueryFlag.getValidLongFlags().contains(flagParameter)) {
                separateFlags.add(flagParameter);
            } else {
                final CharacterIterator charIterator = new StringCharacterIterator(flagParameter);
                for (char flag = charIterator.first(); flag != CharacterIterator.DONE; flag = charIterator.next()) {
                    final String flagString = String.valueOf(flag);
                    if (!QueryFlag.getValidShortFlags().contains(flagString)) {
                        throw new IllegalArgumentException(String.format("Invalid option '%s'", flag));
                    }
                    separateFlags.add(flagString);
                }
            }
        }
        return separateFlags;
    }

    private void checkForInvalidFlags(final Set<String> flags) {
        for (final String flag : flags) {
            if (NOT_ALLOWED_SEARCH_QUERY_FLAGS.contains(flag)) {
                throw new IllegalArgumentException(String.format("Disallowed option '%s'", flag));
            }
        }
    }

    private RpslObject performUpdate(final Origin origin, final Update update, final String content, final Keyword keyword) {
        loggerContext.init(getRequestId(origin.getFrom()));
        try {
            final UpdateContext updateContext = new UpdateContext(loggerContext);
            final boolean notificationsEnabled = true;

            final UpdateRequest updateRequest = new UpdateRequest(
                    origin,
                    keyword,
                    content,
                    Lists.newArrayList(update),
                    notificationsEnabled);

            final UpdateResponse response = updateRequestHandler.handle(updateRequest, updateContext);

            if (updateContext.getStatus(update) == UpdateStatus.FAILED_AUTHENTICATION) {
                throw new WebApplicationException(getResponse(new UpdateResponse(UpdateStatus.FAILED_AUTHENTICATION, response.getResponse())));
            }
            if (response.getStatus() != UpdateStatus.SUCCESS) {
                throw new WebApplicationException(getResponse(response));
            }

            return update.getOperation() == Operation.DELETE ? null : rpslObjectDao.getById(updateContext.getUpdateInfo(update).getObjectId());
        } finally {
            loggerContext.remove();
        }
    }

    private String createContent(final RpslObject rpslObject, final List<String> passwords, final String deleteReason, String override) {
        final StringBuilder builder = new StringBuilder();
        builder.append(rpslObject.toString());

        if (builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }

        if (deleteReason != null) {
            builder.append("delete: ");
            builder.append(deleteReason);
            builder.append("\n\n");
        }

        for (final String password : passwords) {
            builder.append("password: ");
            builder.append(password);
            builder.append('\n');
        }

        if (override != null) {
            builder.append("override: ");
            builder.append(override);
            builder.append("\n\n");
        }

        return builder.toString();
    }

    private Update createUpdate(final RpslObject rpslObject, final List<String> passwords, final String deleteReason) {
        return new Update(
                createParagraph(rpslObject, passwords),
                deleteReason != null ? Operation.DELETE : Operation.UNSPECIFIED,
                deleteReason != null ? Lists.newArrayList(deleteReason) : null,
                rpslObject);
    }

    private Paragraph createParagraph(final RpslObject rpslObject, final List<String> passwords) {
        final Set<PasswordCredential> passwordCredentials = Sets.newHashSet();
        for (String password : passwords) {
            passwordCredentials.add(new PasswordCredential(password));
        }

        return new Paragraph(rpslObject.toString(), new Credentials(passwordCredentials));
    }

    private Origin createOrigin(final HttpServletRequest request) {
        return new WhoisRestApi(dateTimeProvider, request.getRemoteAddr());
    }

    private WhoisResources createWhoisResources(final HttpServletRequest request, final RpslObject rpslObject, boolean filter) {
        final WhoisResources whoisResources = new WhoisResources();
        whoisResources.setWhoisObjects(Collections.singletonList(whoisObjectMapper.map(rpslObject, filter)));
        whoisResources.setLink(new Link("locator", RestServiceHelper.getRequestURL(request)));
        return whoisResources;
    }

    private RpslObject getSubmittedObject(final WhoisResources whoisResources) {
        if (whoisResources.getWhoisObjects().isEmpty() || whoisResources.getWhoisObjects().size() > 1) {
            throw new IllegalArgumentException("Expected a single RPSL object");
        }
        return whoisObjectMapper.map(whoisResources.getWhoisObjects().get(0));
    }

    private Response getResponse(final UpdateResponse updateResponse) {
        int status;
        switch (updateResponse.getStatus()) {
            case FAILED: {
                status = HttpServletResponse.SC_BAD_REQUEST;

                final String errors = findAllErrors(updateResponse);
                if (!errors.isEmpty()) {
                    if (errors.contains("Enforced new keyword specified, but the object already exists")) {
                        status = HttpServletResponse.SC_CONFLICT;
                    }
                    return Response.status(status).entity(errors).build();
                }

                break;
            }
            case EXCEPTION:
                status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                break;
            case FAILED_AUTHENTICATION:
                status = HttpServletResponse.SC_UNAUTHORIZED;
                final String errors = findAllErrors(updateResponse);
                if (!errors.isEmpty()) {
                    return Response.status(status).entity(errors).build();
                }
                break;
            default:
                status = HttpServletResponse.SC_OK;
        }

        return Response.status(status).build();
    }

    private String getRequestId(final String remoteAddress) {
        return String.format("rest_%s_%s", remoteAddress, System.nanoTime());
    }

    private String findAllErrors(final UpdateResponse updateResponse) {
        final StringBuilder builder = new StringBuilder();
        final Matcher matcher = UPDATE_RESPONSE_ERRORS.matcher(updateResponse.getResponse());
        while (matcher.find()) {
            builder.append(matcher.group(1).replaceAll("[\\n ]+", " "));
            builder.append('\n');
        }
        return builder.toString();
    }

    private void validateSubmittedObject(final RpslObject object, final String objectType, final String key) {
        if (!(object.getKey().equals(CIString.ciString(key)) &&
                object.getType().getName().equalsIgnoreCase(objectType))) {
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
    }
}
